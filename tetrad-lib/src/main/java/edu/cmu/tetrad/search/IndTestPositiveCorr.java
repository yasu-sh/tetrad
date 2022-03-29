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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.CovarianceMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.StatUtils;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static java.lang.Math.*;

/**
 * Checks conditional independence of variable in a continuous data set using Fisher's Z test. See Spirtes, Glymour, and
 * Scheines, "Causation, Prediction and Search," 2nd edition, page 94.
 *
 * @author Joseph Ramsey
 * @author Frank Wimberly adapted IndTestCramerT for Fisher's Z
 */
public final class IndTestPositiveCorr implements IndependenceTest {

    /**
     * The covariance matrix.
     */
    private final ICovarianceMatrix covMatrix;
    private final double[][] data;

//    /**
//     * The matrix out of the cov matrix.
//     */
//    private final TetradMatrix _covMatrix;

    /**
     * The variables of the covariance matrix, in order. (Unmodifiable list.)
     */
    private List<Node> variables;

    /**
     * The significance level of the independence tests.
     */
    private double alpha;

    /**
     * The value of the Fisher's Z statistic associated with the las calculated partial correlation.
     */
    private double pValue;

    /**
     * Formats as 0.0000.
     */
    private static final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

    /**
     * Stores a reference to the dataset being analyzed.
     */
    private final DataSet dataSet;

    private PrintStream pValueLogger;
    private final Map<Node, Integer> indexMap;
    private final Map<String, Node> nameMap;
    private boolean verbose = true;
    private final double fisherZ = Double.NaN;
    private double cutoff = Double.NaN;
    private double rho;
    private final NormalDistribution normal = new NormalDistribution(0, 1);

    //==========================CONSTRUCTORS=============================//

    /**
     * Constructs a new Independence test which checks independence facts based on the correlation matrix implied by the
     * given data set (must be continuous). The given significance level is used.
     *
     * @param dataSet A data set containing only continuous columns.
     * @param alpha   The alpha level of the test.
     */
    public IndTestPositiveCorr(final DataSet dataSet, final double alpha) {
        if (!(dataSet.isContinuous())) {
            throw new IllegalArgumentException("Data set must be continuous.");
        }

        if (!(alpha >= 0 && alpha <= 1)) {
            throw new IllegalArgumentException("Alpha mut be in [0, 1]");
        }

        this.covMatrix = new CovarianceMatrix(dataSet);
        final List<Node> nodes = this.covMatrix.getVariables();

        this.variables = Collections.unmodifiableList(nodes);
        this.indexMap = indexMap(this.variables);
        this.nameMap = nameMap(this.variables);
        setAlpha(alpha);

        this.dataSet = dataSet;

        this.data = dataSet.getDoubleData().transpose().toArray();

    }

    //==========================PUBLIC METHODS=============================//

    /**
     * Creates a new independence test instance for a subset of the variables.
     */
    public IndependenceTest indTestSubset(final List<Node> vars) {
        throw new UnsupportedOperationException();
    }

    /**
     * Determines whether variable x is independent of variable y given a list of conditioning variables z.
     *
     * @param x0 the one variable being compared.
     * @param y0 the second variable being compared.
     * @param z0 the list of conditioning variables.
     * @return true iff x _||_ y | z.
     * @throws RuntimeException if a matrix singularity is encountered.
     */
    public boolean isIndependent(final Node x0, final Node y0, final List<Node> z0) {

        System.out.println(SearchLogUtils.independenceFact(x0, y0, z0));


        final double[] x = this.data[this.dataSet.getColumn(x0)];
        final double[] y = this.data[this.dataSet.getColumn(y0)];

        final double[][] _Z = new double[z0.size()][];

        for (int f = 0; f < z0.size(); f++) {
            final Node _z = z0.get(f);
            final int column = this.dataSet.getColumn(_z);
            _Z[f] = this.data[column];
        }

        final double pc = partialCorrelation(x, y, _Z, x, Double.NEGATIVE_INFINITY, +1);
        final double pc1 = partialCorrelation(x, y, _Z, x, 0, +1);
        final double pc2 = partialCorrelation(x, y, _Z, y, 0, +1);

        final int nc = StatUtils.getRows(x, Double.NEGATIVE_INFINITY, +1).size();
        final int nc1 = StatUtils.getRows(x, 0, +1).size();
        final int nc2 = StatUtils.getRows(y, 0, +1).size();

        final double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
        final double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
        final double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

        final double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
        final double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));

        final double p1 = (1.0 - new NormalDistribution(0, 1).cumulativeProbability(abs(zv1)));
        final double p2 = (1.0 - new NormalDistribution(0, 1).cumulativeProbability(abs(zv2)));

        final boolean rejected1 = p1 < this.alpha;
        final boolean rejected2 = p2 < this.alpha;

        boolean possibleEdge = false;

        if (zv1 < 0 && zv2 > 0 && rejected1) {
            possibleEdge = true;
        } else if (zv1 > 0 && zv2 < 0 && rejected2) {
            possibleEdge = true;
        } else if (rejected1 && rejected2) {
            possibleEdge = true;
        } else if (rejected1 || rejected2) {
            possibleEdge = true;
        }

        System.out.println(possibleEdge);

        return !possibleEdge;
    }

    public boolean isIndependent(final Node x, final Node y, final Node... z) {
        return isIndependent(x, y, Arrays.asList(z));
    }

    public boolean isDependent(final Node x, final Node y, final List<Node> z) {
        return !isIndependent(x, y, z);
    }

    public boolean isDependent(final Node x, final Node y, final Node... z) {
        final List<Node> zList = Arrays.asList(z);
        return isDependent(x, y, zList);
    }

    /**
     * @return the probability associated with the most recently computed independence test.
     */
    public double getPValue() {
        return 2.0 * (1.0 - this.normal.cumulativeProbability(abs(this.fisherZ)));
    }

    /**
     * Sets the significance level at which independence judgments should be made.  Affects the cutoff for partial
     * correlations to be considered statistically equal to zero.
     */
    public void setAlpha(final double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("Significance out of range: " + alpha);
        }

        this.alpha = alpha;
        this.cutoff = StatUtils.getZForAlpha(alpha);
    }

    /**
     * Gets the getModel significance level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * @return the list of variables over which this independence checker is capable of determinine independence
     * relations-- that is, all the variables in the given graph or the given data set.
     */
    public List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @return the variable with the given name.
     */
    public Node getVariable(final String name) {
        return this.nameMap.get(name);
    }

    /**
     * @return the list of variable varNames.
     */
    public List<String> getVariableNames() {
        final List<Node> variables = getVariables();
        final List<String> variableNames = new ArrayList<>();
        for (final Node variable1 : variables) {
            variableNames.add(variable1.getName());
        }
        return variableNames;
    }

    /**
     * If <code>isDeterminismAllowed()</code>, deters to IndTestFisherZD; otherwise throws
     * UnsupportedOperationException.
     */
    public boolean determines(final List<Node> z, final Node x) throws UnsupportedOperationException {
        final int[] parents = new int[z.size()];

        for (int j = 0; j < parents.length; j++) {
            parents[j] = this.covMatrix.getVariables().indexOf(z.get(j));
        }

//        int i = covMatrix.getVariable().indexOf(x);

//        double variance = covMatrix.getValue(i, i);

        if (parents.length > 0) {

            // Regress z onto i, yielding regression coefficients b.
            final Matrix Czz = this.covMatrix.getSelection(parents, parents);
//            TetradMatrix inverse;

            try {
//                inverse =
                Czz.inverse();
            } catch (final SingularMatrixException e) {
//                System.out.println(SearchLogUtils.determinismDetected(z, x));

                return true;
            }

//            TetradVector Cyz = covMatrix.getSelection(parents, new int[]{i}).getColumn(0);
//            TetradVector b = inverse.times(Cyz);
//
//            variance -= Cyz.dotProduct(b);
        }

        return false;

//        return variance < 1e-20;
    }

    /**
     * @return the data set being analyzed.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    public void shuffleVariables() {
        final ArrayList<Node> nodes = new ArrayList<>(this.variables);
        Collections.shuffle(nodes);
        this.variables = Collections.unmodifiableList(nodes);
    }

    /**
     * @return a string representation of this test.
     */
    public String toString() {
        return "Fisher Z, alpha = " + new DecimalFormat("0.0E0").format(getAlpha());
    }

    public void setPValueLogger(final PrintStream pValueLogger) {
        this.pValueLogger = pValueLogger;
    }

    //==========================PRIVATE METHODS============================//

    private int sampleSize() {
        return covMatrix().getSampleSize();
    }

    private ICovarianceMatrix covMatrix() {
        return this.covMatrix;
    }

    private Map<String, Node> nameMap(final List<Node> variables) {
        final Map<String, Node> nameMap = new ConcurrentHashMap<>();

        for (final Node node : variables) {
            nameMap.put(node.getName(), node);
        }

        return nameMap;
    }

    private Map<Node, Integer> indexMap(final List<Node> variables) {
        final Map<Node, Integer> indexMap = new ConcurrentHashMap<>();

        for (int i = 0; i < variables.size(); i++) {
            indexMap.put(variables.get(i), i);
        }

        return indexMap;
    }

    public void setVariables(final List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = new ArrayList<>(variables);
        this.covMatrix.setVariables(variables);
    }

    public ICovarianceMatrix getCov() {
        return this.covMatrix;
    }

    @Override
    public List<DataSet> getDataSets() {

        final List<DataSet> dataSets = new ArrayList<>();

        dataSets.add(this.dataSet);

        return dataSets;
    }

    @Override
    public int getSampleSize() {
        return this.covMatrix.getSampleSize();
    }

    @Override
    public List<Matrix> getCovMatrices() {
        return null;
    }

    @Override
    public double getScore() {
        return Math.abs(this.fisherZ) - this.cutoff;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    public double getRho() {
        return this.rho;
    }

    private double partialCorrelation(final double[] x, final double[] y, final double[][] z, final double[] condition, final double threshold, final double direction) throws SingularMatrixException {
        final double[][] cv = StatUtils.covMatrix(x, y, z, condition, threshold, direction);
        final Matrix m = new Matrix(cv).transpose();
        return StatUtils.partialCorrelation(m);
    }
}




