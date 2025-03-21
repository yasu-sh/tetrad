///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
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

package edu.cmu.tetrad.search.test;

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.IndependenceFact;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.Fges;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;

import static java.lang.Double.NaN;
import static org.apache.commons.math3.util.FastMath.*;

/**
 * <p>Implements a degenerate Gaussian score as a LRT. The reference is here:</p>
 *
 * <p>Andrews, B., Ramsey, J., & Cooper, G. F. (2019, July). Learning high-dimensional
 * directed acyclic graphs with mixed data-types. In The 2019 ACM SIGKDD Workshop on Causal Discovery (pp. 4-21).
 * PMLR.</p>
 *
 * @author Bryan Andrews
 */
public class IndTestDegenerateGaussianLrt implements IndependenceTest {

    private final BoxDataSet ddata;
    private final double[][] _ddata;
    private final Map<Node, Integer> nodesHash;
    private final DataSet dataSet;

    // The alpha level.
    private double alpha = 0.001;

    // The p value.
    private double pValue = NaN;

    // The mixed variables of the original dataset.
    private final List<Node> variables;

    // The embedding map.
    private final Map<Integer, List<Integer>> embedding;

    /**
     * Stores a return value for a likelihood--i.e., a likelihood value and the degrees of freedom for it.
     */
    public static class Ret {
        private final double lik;
        private final double dof;

        private Ret(double lik, double dof) {
            this.lik = lik;
            this.dof = dof;
        }

        public double getLik() {
            return this.lik;
        }

        public double getDof() {
            return this.dof;
        }

        public String toString() {
            return "lik = " + this.lik + " dof = " + this.dof;
        }
    }

    // A constant.
    private static final double L2PE = log(2.0 * PI * E);

    private boolean verbose;

    /**
     * Constructs the score using a covariance matrix.
     *
     * @param dataSet The data being analyzed.
     */
    public IndTestDegenerateGaussianLrt(DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        // The number of instances.
        int n = dataSet.getNumRows();
        this.embedding = new ConcurrentSkipListMap<>();

        List<Node> A = new ArrayList<>();
        List<double[]> B = new ArrayList<>();

        Map<Node, Integer> nodesHash = new ConcurrentSkipListMap<>();

        for (int j = 0; j < this.variables.size(); j++) {
            nodesHash.put(this.variables.get(j), j);
        }

        this.nodesHash = nodesHash;

        int index = 0;

        int i = 0;
        int i_ = 0;
        while (i_ < this.variables.size()) {

            Node v = this.variables.get(i_);

            if (v instanceof DiscreteVariable) {

                Map<List<Integer>, Integer> keys = new ConcurrentHashMap<>();
                Map<Integer, List<Integer>> keysReverse = new ConcurrentSkipListMap<>();
                for (int j = 0; j < n; j++) {
                    List<Integer> key = new ArrayList<>();
                    key.add(this.dataSet.getInt(j, i_));
                    if (!keys.containsKey(key)) {
                        keys.put(key, i);
                        keysReverse.put(i, key);
                        Node v_ = new ContinuousVariable("V__" + ++index);
                        A.add(v_);
                        B.add(new double[n]);
                        i++;
                    }
                    B.get(keys.get(key))[j] = 1;
                }

                /*
                 * Remove a degenerate dimension.
                 */
                i--;
                keys.remove(keysReverse.get(i));
                A.remove(i);
                B.remove(i);

                this.embedding.put(i_, new ArrayList<>(keys.values()));

            } else {

                A.add(v);
                double[] b = new double[n];
                for (int j = 0; j < n; j++) {
                    b[j] = this.dataSet.getDouble(j, i_);
                }

                B.add(b);
                List<Integer> index2 = new ArrayList<>();
                index2.add(i);
                this.embedding.put(i_, index2);
                i++;

            }
            i_++;
        }

        double[][] B_ = new double[n][B.size()];
        for (int j = 0; j < B.size(); j++) {
            for (int k = 0; k < n; k++) {
                B_[k][j] = B.get(j)[k];
            }
        }

        // The continuous variables of the post-embedding dataset.
        RealMatrix D = new BlockRealMatrix(B_);
        this.ddata = new BoxDataSet(new DoubleDataBox(D.getData()), A);
        this._ddata = this.ddata.getDoubleData().toArray();
    }


    /**
     * @throws UnsupportedOperationException This method is not implemented.
     */
    public IndependenceTest indTestSubset(List<Node> vars) {
        throw new UnsupportedOperationException("This method is not implemented.");
    }

    /**
     * Returns an independence result specifying whether x _||_ y | Z and what its p-value are.
     *
     * @return This result
     * @see IndependenceResult
     */
    public IndependenceResult checkIndependence(Node x, Node y, Set<Node> _z) {

        List<Node> allNodes = new ArrayList<>();
        allNodes.add(x);
        allNodes.add(y);
        allNodes.addAll(_z);

        List<Node> z = new ArrayList<>(_z);
        Collections.sort(z);

        List<Integer> rows = getRows(allNodes, this.nodesHash);

        if (rows.isEmpty()) return new IndependenceResult(new IndependenceFact(x, y, _z),
                true, NaN, pValue);

        int _x = this.nodesHash.get(x);
        int _y = this.nodesHash.get(y);

        int[] list0 = new int[z.size() + 1];
        int[] list2 = new int[z.size()];

        list0[0] = _x;

        for (int i = 0; i < z.size(); i++) {
            int __z = this.nodesHash.get(z.get(i));
            list0[i + 1] = __z;
            list2[i] = __z;
        }

        Ret ret1 = getlldof(rows, _y, list0);
        Ret ret2 = getlldof(rows, _y, list2);

        double lik0 = ret1.getLik() - ret2.getLik();
        double dof0 = ret1.getDof() - ret2.getDof();

        if (dof0 <= 0) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false, NaN, NaN);
        if (this.alpha == 0) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false,  NaN, NaN);
        if (this.alpha == 1) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false,  NaN, NaN);
        if (lik0 == Double.POSITIVE_INFINITY) return new IndependenceResult(new IndependenceFact(x, y, _z),
                false,  NaN, NaN);

        double pValue;

        if (Double.isNaN(lik0)) {
            pValue = NaN;
        } else {
            pValue = 1.0 - new ChiSquaredDistribution(dof0).cumulativeProbability(2.0 * lik0);
        }

        this.pValue = pValue;

        boolean independent = this.pValue > this.alpha;

        if (this.verbose) {
            if (independent) {
                TetradLogger.getInstance().forceLogMessage(
                        LogUtilsSearch.independenceFactMsg(x, y, _z, pValue));
            }
        }

        return new IndependenceResult(new IndependenceFact(x, y, _z),
                independent, pValue, alpha - pValue);
    }

    /**
     * Returns the probability associated with the most recently executed independence test, of Double.NaN if p value is
     * not meaningful for tis test.
     *
     * @return This p-value.
     */
    public double getPValue() {
        return this.pValue;
    }

    /**
     * Returns the list of searchVariables over which this independence checker is capable of determinining independence
     * relations.
     *
     * @return This list.
     */
    public List<Node> getVariables() {
        return new ArrayList<>(this.variables);
    }

    /**
     * Returns true if y is determined the variable in z.
     *
     * @return True if so.
     */
    public boolean determines(List<Node> z, Node y) {
        return false; //stub
    }

    /**
     * Returns  the significance level of the independence test.
     *
     * @return this level.
     */
    public double getAlpha() {
        return this.alpha;
    }

    /**
     * Sets the significance level.
     *
     * @param alpha This level.
     */
    public void setAlpha(double alpha) {
        this.alpha = alpha;
    }

    /**
     * Returns the dataset being analyzed.
     *
     * @return This data.
     */
    public DataSet getData() {
        return this.dataSet;
    }

    /**
     * Returns a string representation of this test.
     *
     * @return This string.
     */
    public String toString() {
        NumberFormat nf = new DecimalFormat("0.0000");
        return "Degenerate Gaussian, alpha = " + nf.format(getAlpha());
    }

    /**
     * Returns true iff verbose output should be printed.
     *
     * @return True if so.
     */
    @Override
    public boolean isVerbose() {
        return this.verbose;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True if so.
     */
    @Override
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Calculates the sample log likelihood
     */
    private Ret getlldof(List<Integer> rows, int i, int... parents) {
        int N = rows.size();

        List<Integer> B = new ArrayList<>();
        List<Integer> A = new ArrayList<>(this.embedding.get(i));
        for (int i_ : parents) {
            B.addAll(this.embedding.get(i_));
        }

        int[] A_ = new int[A.size() + B.size()];
        int[] B_ = new int[B.size()];
        for (int i_ = 0; i_ < A.size(); i_++) {
            A_[i_] = A.get(i_);
        }
        for (int i_ = 0; i_ < B.size(); i_++) {
            A_[A.size() + i_] = B.get(i_);
            B_[i_] = B.get(i_);
        }

        double dof = (A_.length * (A_.length + 1) - B_.length * (B_.length + 1)) / 2.0;
        double ldetA = log(getCov(rows, A_).det());
        double ldetB = log(getCov(rows, B_).det());

        double lik = N * (ldetB - ldetA) + IndTestDegenerateGaussianLrt.L2PE * (B_.length - A_.length);

        return new Ret(lik, dof);
    }

    private List<Integer> getRows(List<Node> allVars, Map<Node, Integer> nodesHash) {
        List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < this.dataSet.getNumRows(); k++) {
            for (Node node : allVars) {
                List<Integer> A = new ArrayList<>(this.embedding.get(nodesHash.get(node)));

                for (int i : A) {
                    if (Double.isNaN(this.ddata.getDouble(k, i))) continue K;
                }
            }

            rows.add(k);
        }

        return rows;
    }

    // Subsample of the continuous mixedVariables conditioning on the given cols.
    private Matrix getCov(List<Integer> rows, int[] cols) {
        Matrix cov = new Matrix(cols.length, cols.length);

        for (int i = 0; i < cols.length; i++) {
            for (int j = 0; j < cols.length; j++) {
                double mui = 0.0;
                double muj = 0.0;

                for (int k : rows) {
                    mui += this._ddata[k][cols[i]];
                    muj += this._ddata[k][cols[j]];
                }

                mui /= rows.size() - 1;
                muj /= rows.size() - 1;

                double _cov = 0.0;

                for (int k : rows) {
                    _cov += (this._ddata[k][cols[i]] - mui) * (this._ddata[k][cols[j]] - muj);
//                    _cov += (ddata.getDouble(k, cols[i]) - mui) * (ddata.getDouble(k, cols[j]) - muj);
                }

                double mean = _cov / (rows.size());
                cov.set(i, j, mean);
            }
        }

        return cov;
    }
}