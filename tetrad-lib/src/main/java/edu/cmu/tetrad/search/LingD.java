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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.EigenDecomposition;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.math3.util.FastMath.*;

/**
 * <p>This class implements the LiNG-D algorithm as well as a number of ancillary
 * methods for LiNG-D and LiNGAM.</p>
 * <p>Lacerda, G., Spirtes, P. L., Ramsey, J., & Hoyer, P. O. (2012). Discovering
 * cyclic causal models by independent components analysis. arXiv preprint
 * arXiv:1206.3273.</p>
 *
 * @author josephramsey
 */
public class LingD {

    private double wThreshold;

    /**
     * Constructor. The W matrix needs to be estimated separately (e.g., using
     * the Lingam.estimateW(.) method using the ICA method in Tetrad, or some
     * method in Python or R) and passed into the search(W) method.
     */
    public LingD() {
    }

    /**
     * Performs the LiNG-D algorithm given a W matrix, which needs to be discovered
     * elsewhere. The local algorithm is assumed--in fact, the W matrix is simply
     * thresholded without bootstrapping.
     *
     * @param W The W matrix to be used.
     * @return A list of estimated B Hat matrices generated by LiNG-D.
     */
    public List<Matrix> search(Matrix W) {
        System.out.println("Starting LiNG-D");
        W = LingD.threshold(W, wThreshold);
        List<PermutationMatrixPair> pairs = nRooks(W);

        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("Could not find an N Rooks solution with that threshold.");
        }

        List<Matrix> results = new ArrayList<>();

        for (PermutationMatrixPair pair : pairs) {
            Matrix bHat = edu.cmu.tetrad.search.LingD.getScaledBHat(pair);
            results.add(bHat);
        }

        return results;
    }

    /**
     * Sets the threshold used to prune the W matrix for the local algorithms.
     *
     * @param wThreshold The treshold, a non-negative number.
     */
    public void setWThreshold(double wThreshold) {
        if (wThreshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + wThreshold);
        this.wThreshold = wThreshold;
    }

    /**
     * Estimates the W matrix using FastICA. Assumes the "parallel" option, using
     * the "exp" function.
     *
     * @param data             The dataset to estimate W for.
     * @param fastIcaMaxIter   Maximum number of iterations of ICA.
     * @param fastIcaTolerance Tolerance for ICA.
     * @param fastIcaA         Alpha for ICA.
     * @return The estimated W matrix.
     */
    public static Matrix estimateW(DataSet data, int fastIcaMaxIter, double fastIcaTolerance,
                                   double fastIcaA) {
        Matrix X = data.getDoubleData();
        X = DataUtils.centerData(X).transpose();
        FastIca fastIca = new FastIca(X, X.rows());
        fastIca.setVerbose(false);
        fastIca.setMaxIterations(fastIcaMaxIter);
        fastIca.setAlgorithmType(FastIca.PARALLEL);
        fastIca.setTolerance(fastIcaTolerance);
        fastIca.setFunction(FastIca.LOGCOSH);
        fastIca.setRowNorm(false);
        fastIca.setAlpha(fastIcaA);
        FastIca.IcaResult result11 = fastIca.findComponents();
        return result11.getW();
    }

    /**
     * Returns a graph given a coefficient matrix and a list of variables. It is
     * assumed that any non-zero entry in B corresponds to a directed edges, so
     * that Bij != 0 implies that j->i in the graph.
     *
     * @param B         The coefficient matrix.
     * @param variables The list of variables.
     * @return The built graph.
     */
    @NotNull
    public static Graph makeGraph(Matrix B, List<Node> variables) {
        Graph g = new EdgeListGraph(variables);

        for (int j = 0; j < B.columns(); j++) {
            for (int i = 0; i < B.rows(); i++) {
                if (B.get(i, j) != 0) {
                    g.addDirectedEdge(variables.get(j), variables.get(i));
                }
            }
        }
        return g;
    }

    /**
     * Finds a column permutation of the W matrix that maximizes the sum
     * of 1 / |Wii| for diagonal elements Wii in W. This will be speeded up
     * if W is a thresholded matrix.
     *
     * @param W The (possibly thresholded) W matrix.
     * @return The model with the strongest diagonal, as a permutation matrix pair.
     * @see PermutationMatrixPair
     */
    public static PermutationMatrixPair strongestDiagonalByCols(Matrix W) {
        List<PermutationMatrixPair> pairs = nRooks(W.transpose());

        if (pairs.isEmpty()) {
            throw new IllegalArgumentException("Could not find an N Rooks solution with that threshold.");
        }

        PermutationMatrixPair bestPair = null;
        double sum1 = Double.POSITIVE_INFINITY;

        P:
        for (PermutationMatrixPair pair : pairs) {
            Matrix permutedMatrix = pair.getPermutedMatrix();

            double sum = 0.0;
            for (int j = 0; j < permutedMatrix.rows(); j++) {
                double a = permutedMatrix.get(j, j);

                if (a == 0) {
                    continue P;
                }

                sum += 1.0 / StrictMath.abs(a);
            }

            if (sum < sum1) {
                sum1 = sum;
                bestPair = pair;
            }
        }

        if (bestPair == null) {
            throw new IllegalArgumentException("Could not find a best N Rooks solution with that threshold.");
        }

        return bestPair;
    }

    /**
     * Whether the BHat matrix represents a stable model. The eigenvalues are
     * checked ot make sure they are all less than 1 in modulus.
     *
     * @param bHat The bHat matrix.
     * @return True iff the model is stable.
     */
    public static boolean isStable(Matrix bHat) {
        EigenDecomposition eigen = new EigenDecomposition(new BlockRealMatrix(bHat.toArray()));
        double[] realEigenvalues = eigen.getRealEigenvalues();
        double[] imagEigenvalues = eigen.getImagEigenvalues();

        for (int i = 0; i < realEigenvalues.length; i++) {
            double realEigenvalue = realEigenvalues[i];
            double imagEigenvalue = imagEigenvalues[i];
            double modulus = sqrt(pow(realEigenvalue, 2) + pow(imagEigenvalue, 2));

            System.out.println("Modulus for eigenvalue " + (i + 1) + " = " + modulus);

            if (modulus >= 1.0) {
                return false;
            }
        }

        return true;
    }

    /**
     * Scares the given matrix M by diving each entry (i, j) by M(j, j)
     *
     * @param M The matrix to scale.
     * @return The scaled matrix.
     */
    public static Matrix scale(Matrix M) {
        Matrix _M = M.like();

        for (int i = 0; i < _M.rows(); i++) {
            for (int j = 0; j < _M.columns(); j++) {
                _M.set(i, j, M.get(i, j) / M.get(j, j));
            }
        }

        return _M;
    }

    /**
     * Thresholds the givem matrix, sending any small entries to zero.
     *
     * @param M         The matrix to threshold.
     * @param threshold The value such that M(i, j) is set to zero if |M(i, j)| < threshold.
     *                  Should be non-negative.
     * @return The thresholded matrix.
     */
    public static Matrix threshold(Matrix M, double threshold) {
        if (threshold < 0) throw new IllegalArgumentException("Expecting a non-negative number: " + threshold);

        Matrix _M = M.copy();

        for (int i = 0; i < M.rows(); i++) {
            for (int j = 0; j < M.columns(); j++) {
                if (abs(M.get(i, j)) < threshold) _M.set(i, j, 0.0);
            }
        }

        return _M;
    }

    /**
     * Returns the BHat matrix, permuted to causal order (lower triangle) and
     * scaled so that the diagonal consists only of 1's.
     *
     * @param pair The (column permutation, thresholded, column permuted W matrix)
     *             pair.
     * @return The estimated B Hat matrix for this pair.
     * @see PermutationMatrixPair
     */
    public static Matrix getScaledBHat(PermutationMatrixPair pair) {
        Matrix _w = pair.getPermutedMatrix();
        _w = scale(_w);
        Matrix bHat = Matrix.identity(_w.rows()).minus(_w);
        int[] perm = pair.getColPerm();
        int[] inverse = LingD.inversePermutation(perm);
        PermutationMatrixPair inversePair = new PermutationMatrixPair(bHat, inverse, inverse);
        return inversePair.getPermutedMatrix();
    }

    @NotNull
    private static List<PermutationMatrixPair> nRooks(Matrix W) {
        List<PermutationMatrixPair> pairs = new ArrayList<>();
        boolean[][] allowablePositions = new boolean[W.rows()][W.columns()];

        for (int i = 0; i < W.rows(); i++) {
            for (int j = 0; j < W.columns(); j++) {
                allowablePositions[i][j] = W.get(i, j) != 0;
            }
        }

        List<int[]> colPermutations = NRooks.nRooks(allowablePositions);

        for (int[] colPermutation : colPermutations) {
            pairs.add(new PermutationMatrixPair(W, null, colPermutation));
        }

        return pairs;
    }

    static int[] inversePermutation(int[] perm) {
        int[] inverse = new int[perm.length];

        for (int i = 0; i < perm.length; i++) {
            inverse[perm[i]] = i;
        }

        return inverse;
    }
}


