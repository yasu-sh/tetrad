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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.regression.LogisticRegression2;
import edu.cmu.tetrad.util.DepthChoiceGenerator;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Vector;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.lang.Math.sqrt;

/**
 * Implements the continuous BIC score for FGES.
 *
 * @author Joseph Ramsey
 */
public class MixedBicScore implements Score {

    // The covariance matrix.
    private ICovarianceMatrix covariances;

    // The variables of the continuousData set.
    private List<Node> variables;

    // The sample size of the covariance matrix.
    private final int sampleSize;

    // The penalty penaltyDiscount.
    private double penaltyDiscount = 2.0;

    // True if linear dependencies should return NaN for the score, and hence be
    // ignored by FGES
    private boolean ignoreLinearDependent = false;

    // The printstream output should be sent to.
    private PrintStream out = System.out;

    // True if verbose output should be sent to out.
    private boolean verbose = false;
    private final Set<Integer> forbidden = new HashSet<>();
    private final double logn;
    private boolean[] isDiscrete;
    private int[] numValues;
    private double[][] continuousData;
    private int[][] discreteData;

    /**
     * Constructs the score using a covariance matrix.
     */
    public MixedBicScore(final DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        setDataSet(dataSet);
        this.variables = dataSet.getVariables();
        this.sampleSize = dataSet.getNumRows();
        this.penaltyDiscount = 4;
        this.logn = Math.log(this.sampleSize);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(final int i, final int... parents) {
        if (this.isDiscrete[i]) {
            final double bicLogistic = getBicLogistic(i, parents);
//            System.out.println("Bic logistic = " + bicLogistic);
            return bicLogistic;
        } else {
            return getBicLinear(i, parents);
        }
    }


    @Override
    public double localScoreDiff(final int x, final int y, final int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(final int x, final int y) {
        return localScore(y, x) - localScore(y);
    }

    private int[] append(final int[] parents, final int extra) {
        final int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */
    public double localScore(final int i, final int parent) {
        return localScore(i, new int[]{parent});
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(final int i) {
        return localScore(i, new int[0]);
    }

    /**
     * True iff edges that cause linear dependence are ignored.
     */
    public boolean isIgnoreLinearDependent() {
        return this.ignoreLinearDependent;
    }

    public void setIgnoreLinearDependent(final boolean ignoreLinearDependent) {
        this.ignoreLinearDependent = ignoreLinearDependent;
    }

    public void setOut(final PrintStream out) {
        this.out = out;
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public int getSampleSize() {
        return this.sampleSize;
    }

    @Override
    public boolean isEffectEdge(final double bump) {
        return bump > 0;//-0.25 * getPenaltyDiscount() * Math.log(sampleSize);
    }

    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public boolean isVerbose() {
        return this.verbose;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    public boolean getAlternativePenalty() {
        return false;
    }

    public void setAlternativePenalty(final double alpha) {
        this.penaltyDiscount = alpha;
    }

    private Matrix getSelection1(final ICovarianceMatrix cov, final int[] rows) {
        return cov.getSelection(rows, rows);
    }

    private Vector getSelection2(final ICovarianceMatrix cov, final int[] rows, final int k) {
        return cov.getSelection(rows, new int[]{k}).getColumn(0);
    }


    @Override
    public Node getVariable(final String targetName) {
        for (final Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(this.sampleSize));
    }

    @Override
    public boolean determines(final List<Node> z, final Node y) {
        return false;
    }

    public ICovarianceMatrix getCovariances() {
        return this.covariances;
    }

    private double getBicLogistic(final int i, final int[] parents) {
        if (!this.isDiscrete[i]) throw new IllegalArgumentException();

        final double[][] regressors = new double[parents.length][];
        for (int j = 0; j < parents.length; j++) {
            regressors[j] = this.continuousData[parents[j]];
        }

        final LogisticRegression2 logisticRegression = new LogisticRegression2();
        logisticRegression.regress(this.discreteData[i], this.numValues[i], regressors);
        final double ll = logisticRegression.getLikelihood();

        final int k = (this.numValues[i] - 1) * (parents.length + 1);

        return 2.0 * ll - getPenaltyDiscount() * k * Math.log(this.sampleSize);
    }

    private double getBicLinear(final int i, int[] parents) {
        double residualVariance = getCovariances().getValue(i, i);
        final int n = getSampleSize();
        final int p = parents.length;
        final Matrix covxx = getSelection1(getCovariances(), parents);

        try {
            final Matrix covxxInv = covxx.inverse();

            final Vector covxy = getSelection2(getCovariances(), parents, i);
            final Vector b = covxxInv.times(covxy);
            residualVariance -= covxy.dotProduct(b);

            if (residualVariance <= 0) {
                if (isVerbose()) {
                    this.out.println("Nonpositive residual varianceY: resVar / varianceY = " + (residualVariance / getCovariances().getValue(i, i)));
                }
                return Double.NaN;
            }

            final double c = getPenaltyDiscount();
            return -n * Math.log(residualVariance) - c * (p + 1) * this.logn;
        } catch (final Exception e) {
            boolean removedOne = true;

            while (removedOne) {
                final List<Integer> _parents = new ArrayList<>();
                for (int y = 0; y < parents.length; y++) _parents.add(parents[y]);
                _parents.removeAll(this.forbidden);
                parents = new int[_parents.size()];
                for (int y = 0; y < _parents.size(); y++) parents[y] = _parents.get(y);
                removedOne = printMinimalLinearlyDependentSet(parents, getCovariances());
            }

            return Double.NaN;
        }
    }

    private void standardize(final double[] data) {
        double sum = 0.0;

        for (final double d : data) {
            sum += d;
        }

        final double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data[i] = data[i] - mean;
        }

        double var = 0.0;

        for (final double d : data) {
            var += d * d;
        }

        var /= (data.length);
        final double sd = sqrt(var);

        for (int i = 0; i < data.length; i++) {
            data[i] /= sd;
        }
    }

    private void center(final double[] data) {
        double sum = 0.0;

        for (final double d : data) {
            sum += d;
        }

        final double mean = sum / data.length;

        for (int i = 0; i < data.length; i++) {
            data[i] = data[i] - mean;
        }
    }

    private void setDataSet(final DataSet dataSet) {
        this.variables = dataSet.getVariables();
        this.isDiscrete = new boolean[this.variables.size()];
        this.numValues = new int[this.variables.size()];

        for (int i = 0; i < this.variables.size(); i++) {
            if (this.variables.get(i) instanceof DiscreteVariable) {
                this.isDiscrete[i] = true;
                this.numValues[i] = ((DiscreteVariable) this.variables.get(i)).getNumCategories();
            }
        }

        final double[][] data = new double[dataSet.getNumColumns()][dataSet.getNumRows()];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            for (int i = 0; i < dataSet.getNumRows(); i++) {
                data[j][i] = dataSet.getDouble(i, j);
            }
        }

//        for (int i = 0; i < data.length; i++) {
//            standardize(data[i]);
//        }

        this.continuousData = data;

        this.discreteData = new int[dataSet.getNumColumns()][];

        for (int j = 0; j < dataSet.getNumColumns(); j++) {
            this.discreteData[j] = new int[dataSet.getNumRows()];

            if (this.variables.get(j) instanceof DiscreteVariable) {
                for (int i = 0; i < dataSet.getNumRows(); i++) {
                    this.discreteData[j][i] = dataSet.getInt(i, j);
                }

                center(data[j]);
            } else {
                center(data[j]);
            }
        }

        final Matrix mTranspose = new Matrix(this.continuousData);
        final Matrix m = mTranspose.transpose();
        final DataSet dataSet1 = new BoxDataSet(new DoubleDataBox(m.toArray()), this.variables);
        this.covariances = new CovarianceMatrix(dataSet1);
    }

    // Prints a smallest subset of parents that causes a singular matrix exception.
    private boolean printMinimalLinearlyDependentSet(final int[] parents, final ICovarianceMatrix cov) {
        final List<Node> _parents = new ArrayList<>();
        for (final int p : parents) _parents.add(this.variables.get(p));

        final DepthChoiceGenerator gen = new DepthChoiceGenerator(_parents.size(), _parents.size());
        int[] choice;

        while ((choice = gen.next()) != null) {
            final int[] sel = new int[choice.length];
            final List<Node> _sel = new ArrayList<>();
            for (int m = 0; m < choice.length; m++) {
                sel[m] = parents[m];
                _sel.add(this.variables.get(sel[m]));
            }

            final Matrix m = cov.getSelection(sel, sel);

            try {
                m.inverse();
            } catch (final Exception e2) {
                this.forbidden.add(sel[0]);
                this.out.println("### Linear dependence among variables: " + _sel);
                this.out.println("### Removing " + _sel.get(0));
                return true;
            }
        }

        return false;
    }

}



