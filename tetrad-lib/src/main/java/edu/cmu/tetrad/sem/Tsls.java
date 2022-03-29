///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.util.MatrixUtils;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Implementation of 2SLS, adapted to deal with estimation of covariance
 * matrices. See "An alternative two stage least squares (2SLS) estimator for
 * latent variable equations" by Kenneth Bollen (Psychometrika - vol. 61, no1.,
 * 109-121, March 1996). </p> IMPORTANT: this algorithm assumes that the SemPm
 * is a pure measurement model! With at least TWO measurements per latent. </p>
 * Large parts of this code (that is, everything but estimateCoeffs and
 * estimateCovars) were reused from SEMEstimateAdapter.
 *
 * @author Ricardo Silva
 */

class Tsls {
    private SemPm spm;
    private SemIm semIm;
    private List<String> fixedLoadings;
    private DataSet dataSet;
    private String nodeName;
    private double[][] asymptLCovar;
    private double[] A_hat;
    private String[] lNames;

    /**
     * We require a DataSet (with continuous dataSet) and a SemPm with matching
     * variables.
     */
    public Tsls(final SemPm spm, final DataSet dataSet, final String nm) {
        initialization(spm, dataSet, nm);
    }

    /*
     * fixedLoadings is a list of string. It should contain the names of the
     * nodes such that their loadings are fixed to 1.
     */

    public Tsls(final SemPm spm, final DataSet dataSet, final String nm,
                final List<String> fixedLoadings) {
        initialization(spm, dataSet, nm);
        this.fixedLoadings = fixedLoadings;
    }

    private void initialization(final SemPm spm, final DataSet dataSet,
                                final String nm) {
        this.dataSet = dataSet;
        this.spm = spm;
        if (nm != null) {
            this.nodeName = nm;
        }
        this.semIm = null;
    }

    public SemIm estimate() {
        this.semIm = new SemIm(this.spm);
        this.semIm = estimateCoeffs(this.semIm);
        return this.semIm;
    }

    public SemIm getEstimatedSem() {
        return this.semIm;
    }

    private void setFixedNodes(final SemGraph semGraph, final List<Node> mx1, final List<Node> my1) {
        if (this.fixedLoadings == null) {
            for (final Node nodeA : semGraph.getNodes()) {
                if (nodeA.getNodeType() == NodeType.ERROR) {
                    continue;
                }
                if (nodeA.getNodeType() == NodeType.LATENT) {
                    // We will choose the measurement node with fixed edge by the
                    // lexicographical order.
                    final Iterator<Node> children = semGraph.getChildren(nodeA).iterator();
                    Node nodeB = null;
                    while (children.hasNext()) {
                        final Node child = children.next();
                        if ((child.getNodeType() == NodeType.MEASURED) && (
                                (nodeB == null) || (child.getName().compareTo(
                                        nodeB.getName()) < 0))) {
                            nodeB = child;
                        }
                    }
                    if (semGraph.getParents(nodeA).size() == 0) {
                        mx1.add(nodeB);
                    } else {
                        my1.add(nodeB);
                    }
                }
            }
        } else {
            final Iterator<Node> it = semGraph.getNodes().iterator();
            latentIteration:
            while (it.hasNext()) {
                final Node nodeA = it.next();
                if (nodeA.getNodeType() == NodeType.ERROR) {
                    continue;
                }
                if (nodeA.getNodeType() == NodeType.LATENT) {
                    for (final String fixedLoading : this.fixedLoadings) {
                        final Node indicator = semGraph.getNode(fixedLoading);
                        for (final Node parent : semGraph.getParents(indicator)) {
                            if (parent == nodeA) {
                                if (semGraph.getParents(parent).size() == 0) {
                                    System.out.println("Fixing mx1 = " +
                                            indicator.getName());
                                    mx1.add(indicator);
                                } else {
                                    System.out.println("Fixing my1 = " +
                                            indicator.getName());
                                    my1.add(indicator);
                                }
                                continue latentIteration;
                            }
                        }
                    }
                }
            }
        }
    }

    private SemIm estimateCoeffs(final SemIm semIm) {

        //System.out.print("\n****************\nCalling 2SLS... ");
        final SemGraph semGraph = semIm.getSemPm().getGraph();

        // Get list of fixed measurements that will be kept fixed, and the
        // respective latent variables that are their parents.
        // "X" variables are exogenous, while "Y" variables are endogenous.
        final List<Node> ly = new LinkedList<>();
        final List<Node> lx = new LinkedList<>();
        final List<Node> my1 = new LinkedList<>();
        final List<Node> mx1 = new LinkedList<>();
        final List<Node> observed = new LinkedList<>();

        for (final Node nodeA : semGraph.getNodes()) {
            if (nodeA.getNodeType() == NodeType.ERROR) {
                continue;
            }
            if (nodeA.getNodeType() == NodeType.LATENT) {
                if (semGraph.getParents(nodeA).size() == 0) {
                    lx.add(nodeA);
                } else {
                    ly.add(nodeA);
                }
            } else {
                observed.add(nodeA);
            }
        }
        setFixedNodes(semGraph, mx1, my1);

        //------------------------------------------------------------------

        // Estimate freeParameters for the latent/latent edges
        for (final Node current : ly) {
            if (this.nodeName != null && !this.nodeName.equals(current.getName())) {
                continue;
            }
            // Build Z, the matrix containing the data for the fixed measurements
            // associated with the parents of the getModel (endogenous) latent node
            List<Node> endo_parents_m = new LinkedList<>();
            List<Node> exo_parents_m = new LinkedList<>();
            final List<Node> endo_parents = new LinkedList<>();
            final List<Node> exo_parents = new LinkedList<>();
            Iterator<Node> it_p = semGraph.getParents(current).iterator();
            this.lNames = new String[lx.size() + ly.size()];
            while (it_p.hasNext()) {
                final Node node = it_p.next();
                if (node.getNodeType() == NodeType.ERROR) {
                    continue;
                }
                if (lx.contains(node)) {
                    final int position = lx.indexOf(node);
                    exo_parents_m.add(mx1.get(position));
                    exo_parents.add(node);
                } else {
                    final int position = ly.indexOf(node);
                    endo_parents_m.add(my1.get(position));
                    endo_parents.add(node);
                }
            }
            Object[] endp_a_m = endo_parents_m.toArray();
            Object[] exop_a_m = exo_parents_m.toArray();
            final Object[] endp_a = endo_parents.toArray();
            final Object[] exop_a = exo_parents.toArray();
            int n = this.dataSet.getNumRows(), c = endp_a_m.length + exop_a_m.length;
            if (c == 0) {
                continue;
            }
            final double[][] Z = new double[n][c];
            int count = 0;

            for (int i = 0; i < endp_a_m.length; i++) {
                final Node node = (Node) endp_a_m[i];
                final String name = node.getName();
                final Node variable = this.dataSet.getVariable(name);
                final int colIndex = this.dataSet.getVariables().indexOf(variable);

//                Column column = dataSet.getColumnObject(variable);
//                double column_data[] = (double[]) column.getRawData();

                for (int j = 0; j < n; j++) {
//                    Z[j][i] = column_data[j];
                    Z[j][i] = this.dataSet.getDouble(j, colIndex);
                }

                this.lNames[count++] = (endo_parents.get(i)).getName();
            }
            for (int i = 0; i < exop_a_m.length; i++) {
                final Node node = (Node) exop_a_m[i];
                final String name = node.getName();
                final Node variable = this.dataSet.getVariable(name);
                final int colIndex = this.dataSet.getVariables().indexOf(variable);

//                Column column = dataSet.getColumnObject(variable);
//                double column_data[] = (double[]) column.getRawData();

                for (int j = 0; j < n; j++) {
//                    Z[j][endp_a_m.length + i] = column_data[j];
                    Z[j][endp_a_m.length + i] = this.dataSet.getDouble(j, colIndex);

                }
                this.lNames[count++] = exo_parents.get(i).getName();
            }
            // Build V, the matrix containing the data for the nonfixed measurements
            // associated with the parents of the getModel (endogenous) latent node
            endo_parents_m = new LinkedList<>();
            exo_parents_m = new LinkedList<>();
            it_p = semGraph.getParents(current).iterator();
            while (it_p.hasNext()) {
                final Node node = it_p.next();
                if (node.getNodeType() == NodeType.ERROR) {
                    continue;
                }
                final List<Node> other_measures = new LinkedList<>();

                for (final Node next : semGraph.getChildren(node)) {
                    if (next.getNodeType() == NodeType.MEASURED) {
                        other_measures.add(next);
                    }
                }

                if (lx.contains(node)) {
                    final int position = lx.indexOf(node);
                    other_measures.remove(mx1.get(position));
                    exo_parents_m.addAll(other_measures);
                } else {
                    final int position = ly.indexOf(node);
                    other_measures.remove(my1.get(position));
                    endo_parents_m.addAll(other_measures);
                }
            }
            endp_a_m = endo_parents_m.toArray();
            exop_a_m = exo_parents_m.toArray();
            n = this.dataSet.getNumRows();
            c = endp_a_m.length + exop_a_m.length;
            final double[][] V = new double[n][c];
            if (c == 0) {
                continue;
            }
            for (int i = 0; i < endp_a_m.length; i++) {
                final Node node = ((Node) endp_a_m[i]);
                final String name = node.getName();
                final Node variable = this.dataSet.getVariable(name);
                final int colIndex = this.dataSet.getVariables().indexOf(variable);

//                Column column = dataSet.getColumnObject(variable);
//                double column_data[] = (double[]) column.getRawData();

                for (int j = 0; j < n; j++) {
//                    V[j][i] = column_data[j];
                    V[j][i] = this.dataSet.getDouble(j, colIndex);
                }
            }
            for (int i = 0; i < exop_a_m.length; i++) {
                final Node node = (Node) exop_a_m[i];
                final String name = node.getName();
                final Node variable = this.dataSet.getVariable(name);
                final int colIndex = this.dataSet.getVariables().indexOf(variable);

//                Column column = dataSet.getColumnObject(variable);
//                double column_data[] = (double[]) column.getRawData();

                for (int j = 0; j < n; j++) {
//                    V[j][endp_a_m.length + i] = column_data[j];
                    V[j][endp_a_m.length + i] = this.dataSet.getDouble(j, colIndex);
                }
            }
            final double[] yi = new double[n];
            if (lx.contains(current)) {
                final int position = lx.indexOf(current);
                final Node node = mx1.get(position);
                final String name = node.getName();
                final Node variable = this.dataSet.getVariable(name);
                final int colIndex = this.dataSet.getVariables().indexOf(variable);

//                Column column = dataSet.getColumnObject(variable);
//
//                System.arraycopy(column.getRawData(), 0, yi, 0, n);

                for (int i = 0; i < n; i++) {
                    yi[i] = this.dataSet.getDouble(i, colIndex);
                }
            } else {
                final int position = ly.indexOf(current);
                final Node node = my1.get(position);
                final String name = node.getName();
                final Node variable = this.dataSet.getVariable(name);
                final int colIndex = this.dataSet.getVariables().indexOf(variable);

//                System.arraycopy(dataSet.getColumnObject(variable).getRawData(), 0, yi, 0, n);

                for (int i = 0; i < n; i++) {
                    yi[i] = this.dataSet.getDouble(i, colIndex);
                }
            }
            // Build Z_hat
            final double[][] Z_hat = MatrixUtils.product(V, MatrixUtils.product(
                    MatrixUtils.inverse(
                            MatrixUtils.product(MatrixUtils.transpose(V), V)),
                    MatrixUtils.product(MatrixUtils.transpose(V), Z)));
            this.A_hat = MatrixUtils.product(MatrixUtils.inverse(
                            MatrixUtils.product(MatrixUtils.transpose(Z_hat), Z_hat)),
                    MatrixUtils.product(MatrixUtils.transpose(Z_hat), yi));
            //Set the edge for the fixed measurement
            final int position = ly.indexOf(current);
            semIm.setParamValue(current, my1.get(position), 1.);
            // Set the edge for the latents
            for (int i = 0; i < endp_a.length; i++) {
                semIm.setParamValue((Node) endp_a[i], current, this.A_hat[i]);
            }
            for (int i = 0; i < exop_a.length; i++) {
                semIm.setParamValue((Node) exop_a[i], current,
                        this.A_hat[endp_a.length + i]);
            }
            if (this.nodeName != null && this.nodeName.equals(current.getName())) {
                computeAsymptLatentCovar(yi, this.A_hat, Z, Z_hat,
                        this.dataSet.getNumRows());
                break;
            }
        }

        //------------------------------------------------------------------

        // Estimate freeParameters of the measurement model

        // Set the edges of the fixed measurements of exogenous
        for (final Node current : lx) {
            final int position = lx.indexOf(current);
            semIm.setParamValue(current, mx1.get(position), 1.);
        }

        for (final Node current : observed) {
            if (this.nodeName != null && !this.nodeName.equals(current.getName())) {
                continue;
            }
            if (mx1.contains(current) || my1.contains(current)) {
                continue;
            }

            // First, get the parent of this observed
            Node current_latent = null;

            for (final Node node : semGraph.getParents(current)) {
                if (node.getNodeType() == NodeType.ERROR) {
                    continue;
                }
                current_latent = node;
            }
            final Iterator<Node> children =
                    semGraph.getChildren(current_latent).iterator();
            final List<Node> other_measures = new LinkedList<>();
            final Node fixed_measurement;
            while (children.hasNext()) {
                final Node next = children.next();
                if ((next.getNodeType() == NodeType.MEASURED) &&
                        next != current) {
                    other_measures.add(next);
                }
            }
            if (lx.contains(current_latent)) {
                final int position = lx.indexOf(current_latent);
                other_measures.remove(mx1.get(position));
                fixed_measurement = mx1.get(position);
            } else {
                final int position = ly.indexOf(current_latent);
                other_measures.remove(my1.get(position));
                fixed_measurement = my1.get(position);
            }
            // Regress other_measures over the fixed measurement x1 (y1) correspondent
            // to the measurement variable that is being evaluated
            int n = this.dataSet.getNumRows(), c = other_measures.size();
            if (c == 0) {
                continue;
            }
            final double[][] Z = new double[n][c];
            for (int i = 0; i < c; i++) {
                final Node variable = this.dataSet.getVariable(
                        (other_measures.get(i)).getName());
                final int varIndex = this.dataSet.getVariables().indexOf(variable);

//                Column column = dataSet.getColumnObject(variable);
//                double column_data[] = (double[]) column.getRawData();

                for (int j = 0; j < n; j++) {
//                    Z[j][i] = column_data[j];
                    Z[j][i] = this.dataSet.getDouble(varIndex, j);
                }
            }

            // Build C, the column matrix containing the data for the fixed
            // measurement associated with the only latent parent of the getModel
            // observed node (as assumed by the structure of our measurement model).
            final Node variable = this.dataSet.getVariable(fixed_measurement.getName());
            final int colIndex = this.dataSet.getVariables().indexOf(variable);
//            Column column = dataSet.getColumnObject(variable);
//            double C[] = (double[]) column.getRawData();

            final double[] C = new double[this.dataSet.getNumRows()];

            for (int i = 0; i < this.dataSet.getNumRows(); i++) {
                C[i] = this.dataSet.getDouble(colIndex, i);
            }

            // Build V, the matrix containing the data for the other measurements
            // associated with the parents of the (latent) parent of getModel
            // observed node. The only difference with respect to the estimation
            // of the within-latent coefficients is that here we only include
            // the other measurements attached to the parent of the getModel node,
            // assuming that the error term of the getModel node is independent
            // of the error term of the others and that each measurement is
            // taken with respect to only one latent.
            n = this.dataSet.getNumRows();
            c = other_measures.size();
            final double[][] V = new double[n][c];
            for (int i = 0; i < c; i++) {
                final Node variable2 = this.dataSet.getVariable(
                        (other_measures.get(i)).getName());
                final int var2index = this.dataSet.getVariables().indexOf(variable2);

//                Column column = dataSet.getColumnObject(variable2);
//                double column_data[] = (double[]) column.getRawData();

                for (int j = 0; j < n; j++) {
//                    V[j][i] = column_data[j];
                    V[j][i] = this.dataSet.getDouble(j, var2index);
                }
            }
            final double[] yi = new double[n];
            final Node variable3 = this.dataSet.getVariable((current).getName());
            final int var3Index = this.dataSet.getVariables().indexOf(variable3);

            for (int i = 0; i < n; i++) {
                yi[i] = this.dataSet.getDouble(i, var3Index);
            }

//            Object rawData = dataSet.getColumnObject(variable3).getRawData();
//            System.arraycopy(rawData, 0, yi, 0, n);
            final double[] C_hat = MatrixUtils.product(V, MatrixUtils.product(
                    MatrixUtils.inverse(
                            MatrixUtils.product(MatrixUtils.transpose(V), V)),
                    MatrixUtils.product(MatrixUtils.transpose(V), C)));
            final double A_hat = MatrixUtils.innerProduct(MatrixUtils.scalarProduct(
                    1. / MatrixUtils.innerProduct(C_hat, C_hat), C_hat), yi);
            // Set the edge for the getModel measurement
            semIm.setParamValue(current_latent, current, A_hat);
        }

        return semIm;
    }

    private void computeAsymptLatentCovar(final double[] y, final double[] A_hat,
                                          final double[][] Z, final double[][] Z_hat, final double n) {
        final double[] yza = MatrixUtils.subtract(y, MatrixUtils.product(Z, A_hat));
        final double sigma_ui = MatrixUtils.innerProduct(yza, yza) / n;
        this.asymptLCovar = MatrixUtils.inverse(
                MatrixUtils.product(MatrixUtils.transpose(Z_hat), Z_hat));

        for (final double[] anAsymptLCovar : this.asymptLCovar) {
            for (int j = 0; j < this.asymptLCovar.length; j++) {
                anAsymptLCovar[j] *= sigma_ui;
            }
        }
    }

    /*
     * Get variance for the edge from source to nodeName
     **/

    public double getEdgePValue(final String source) {
        if (this.asymptLCovar == null) {
            return 0.;
        }
        for (int i = 0; i < this.lNames.length; i++) {
            if (this.lNames[i].equals(source)) {
                final double z = Math.abs(this.A_hat[i] / Math.sqrt(this.asymptLCovar[i][i]));
                System.out.println("Asymptotic Z = " + z);
                return 2.0 * (1.0 - edu.cmu.tetrad.util.ProbUtils.normalCdf(z));
            }
        }
        return 0.;
    }

}





