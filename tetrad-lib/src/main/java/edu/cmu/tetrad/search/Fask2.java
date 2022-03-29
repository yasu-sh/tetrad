///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (c) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
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

import edu.cmu.tetrad.algcomparison.statistic.BicEst;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.*;
import org.apache.commons.math3.linear.SingularMatrixException;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Math.*;

//======================================== PUBLIC METHODS ====================================//

/**
 * Runs the FASK (Fast Adjacency Skewness) algorithm. The reference is Sanchez-Romero, R., Ramsey, J. D.,
 * Zhang, K., Glymour, M. R., Huang, B., & Glymour, C. (2019). Estimating feedforward and feedback
 * effective connections from fMRI time series: Assessments of statistical methods. Network Neuroscience,
 * 3(2), 274-306, though it has been improved in some ways from that version, and some pairwise methods from
 * Hyvärinen, A., & Smith, S. M. (2013). Pairwise likelihood ratios for estimation of non-Gaussian structural
 * equation models. Journal of Machine Learning Research, 14(Jan), 111-152 have been included for
 * comparison (and potential use!--they are quite good!).
 * <p>
 * This method (and the Hyvarinen and Smith methods) make the assumption that the data are generated by
 * a linear, non-Gaussian causal process and attempts to recover the causal graph for that process. They
 * do not attempt to recover the parametrization of this graph; for this a separate estimation algorithm
 * would be needed, such as linear regression regressing each node onto its parents. A further assumption
 * is made, that there are no latent common causes of the algorithm. This is not a constraint on the pairwise
 * orientation methods, since they orient with respect only to the two variables at the endpoints of an edge
 * and so are happy with all other variables being considered latent with respect to that single edge. However,
 * if the built-in adjacency search is used (FAS-Stable), the existence of latents will throw this method
 * off.
 * <p>
 * As was shown in the Hyvarinen and Smith paper above, FASK works quite well even if the graph contains
 * feedback loops in most configurations, including 2-cycles. 2-cycles can be detected fairly well if the
 * FASK left-right rule is selected and the 2-cycle threshold set to about 0.1--more will be detected (or
 * hallucinated) if the threshold is set higher. As shown in the Sanchez-Romero reference above, 2-cycle
 * detection of the FASK algorithm using this rule is quite good.
 * <p>
 * Some edges may be undiscoverable by FAS-Stable; to recover more of these edges, a test related to the
 * FASK left-right rule is used, and there is a threshold for this test. A good default for this threshold
 * (the "skew edge threshold") is 0.3. For more of these edges, set this threshold to a lower number.
 * <p>
 * It is assumed that the data are arranged so the each variable forms a column and that there are no missing
 * values. The data matrix is assumed to be rectangular. To this end, the Tetrad DataSet class is used, which
 * enforces this.
 * <p>
 * Note that orienting a DAG for a linear, non-Gaussian model using the Hyvarinen and Smith pairwise rules
 * is alternatively known in the literature as Pairwise LiNGAM--see Hyvärinen, A., & Smith, S. M. (2013). Pairwise
 * likelihood ratios for estimation of non-Gaussian structural equation models. Journal of Machine Learning Research,
 * 14(Jan), 111-152. We include some of these methods here for comparison.
 *
 * @author Joseph Ramsey
 */
public final class Fask2 implements GraphSearch {

    // The data sets being analyzed. They must all have the same variables and the same
    // number of records.
    private final DataSet dataSet;
    // Used for calculating coefficient values.
    private final RegressionDataset regressionDataset;
    private final Score score;
    double[][] D;
    // An initial graph to constrain the adjacency step.
    private Graph externalGraph = null;
    // Elapsed time of the search, in milliseconds.
    private long elapsed = 0;
    // For the Fast Adjacency Search, the maximum number of edges in a conditioning set.
    private int depth = 10;
    // Knowledge the the search will obey, of forbidden and required edges.
    private IKnowledge knowledge = new Knowledge2();
    // A threshold for including extra adjacencies due to skewness. Default is 0.3. For more edges, lower
    // this threshold.
    private double skewEdgeThreshold = 0;
    // A theshold for making 2-cycles. Default is 0 (no 2-cycles.) Note that the 2-cycle rule will only work
    // with the FASK left-right rule. Default is 0; a good value for finding a decent set of 2-cycles is 0.1.
    private double twoCycleScreeningCutoff = 0;
    // At the end of the procedure, two cycles marked in the graph (for having small LR differences) are then
    // tested statisstically to see if they are two-cycles, using this cutoff. To adjust this cutoff, set the
    // two cycle alpha to a number in [0, 1]. The default alpha  is 0.01.
    private double orientationCutoff;
    // The corresponding alpha.
    private double orientationAlpha;
    // Bias for orienting with negative coefficients.
    private double delta;
    // Whether X and Y should be adjusted for skewness. (Otherwise, they are assumed to have positive skewness.
    private boolean empirical = true;

    // The left right rule to use, default FASK.
    private LeftRight leftRight = LeftRight.RSKEW;
    // The graph resulting from search.
    private Graph graph;
    private int numRounds = 50;
    private boolean verbose = false;

    /**
     * @param dataSet A continuous dataset over variables V.
     */
    public Fask2(final Score score, final DataSet dataSet) {
        if (dataSet == null) {
            throw new NullPointerException("Data set not provided.");
        }

        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("For FASK, the dataset must be entirely continuous");
        }

        this.score = score;
        this.dataSet = dataSet;

        this.regressionDataset = new RegressionDataset(dataSet);
        this.orientationCutoff = StatUtils.getZForAlpha(0.01);
        this.orientationAlpha = 0.01;
    }

    private static double cu(final double[] x, final double[] y, final double[] condition) {
        double exy = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition[k] > 0) {
                exy += x[k] * y[k];
                n++;
            }
        }

        return exy / n;
    }

    // Returns E(XY | Z > 0) / sqrt(E(XX | Z > 0) * E(YY | Z > 0)). Z is typically either X or Y.
    private static double correxp(final double[] x, final double[] y, final double[] z) {
        return Fask2.E(x, y, z) / sqrt(Fask2.E(x, x, z) * Fask2.E(y, y, z));
    }

    // Returns E(XY | Z > 0); Z is typically either X or Y.
    private static double E(final double[] x, final double[] y, final double[] z) {
        double exy = 0.0;
        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (z[k] > 0) {
                exy += x[k] * y[k];
                n++;
            }
        }

        return exy / n;
    }

    /**
     * Runs the search on the concatenated data, returning a graph, possibly cyclic, possibly with
     * two-cycles. Runs the fast adjacency search (FAS, Spirtes et al., 2000) follows by a modification
     * of the robust skew rule (Pairwise Likelihood Ratios for Estimation of Non-Gaussian Structural
     * Equation Models, Smith and Hyvarinen), together with some heuristics for orienting two-cycles.
     *
     * @return the graph. Some of the edges may be undirected (though it shouldn't be many in most cases)
     * and some of the adjacencies may be two-cycles.
     */
    public Graph search() {
        final long start = System.currentTimeMillis();
        final NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        final DataSet dataSet = DataUtils.standardizeData(this.dataSet);

        final List<Node> variables = dataSet.getVariables();
        final double[][] lrs = getLrScores(); // Sets D.
//        D = dataSet.getDoubleData().transpose().toArray();

        for (int i = 0; i < variables.size(); i++) {
            System.out.println("Skewness of " + variables.get(i) + " = " + skewness(this.D[i]));
        }

        TetradLogger.getInstance().forceLogMessage("FASK v. 2.0");
        TetradLogger.getInstance().forceLogMessage("");
        TetradLogger.getInstance().forceLogMessage("# variables = " + dataSet.getNumColumns());
        TetradLogger.getInstance().forceLogMessage("N = " + dataSet.getNumRows());
        TetradLogger.getInstance().forceLogMessage("Skewness edge threshold = " + this.skewEdgeThreshold);
        TetradLogger.getInstance().forceLogMessage("Orientation Alpha = " + this.orientationAlpha);
        TetradLogger.getInstance().forceLogMessage("2-cycle threshold = " + this.twoCycleScreeningCutoff);
        TetradLogger.getInstance().forceLogMessage("");

        final Grasp grasp = new Grasp(this.score);
        grasp.setUseRaskuttiUhler(false);
        grasp.setDepth(this.depth);
        grasp.bestOrder(dataSet.getVariables());
        Graph G = grasp.getGraph(false);
        G = GraphUtils.replaceNodes(G, dataSet.getVariables());

        TetradLogger.getInstance().forceLogMessage("");

        SearchGraphUtils.pcOrientbk(this.knowledge, G, G.getNodes());

        final Graph graph = new EdgeListGraph(G.getNodes());

        TetradLogger.getInstance().forceLogMessage("X\tY\tMethod\tLR\tEdge");

        final int V = variables.size();

        final List<NodePair> twoCycles = new ArrayList<>();

        for (int i = 0; i < V; i++) {
            for (int j = i + 1; j < V; j++) {
                final Node X = variables.get(i);
                final Node Y = variables.get(j);

                // Centered
                final double[] x = this.D[i];
                final double[] y = this.D[j];

                final double cx = Fask2.correxp(x, y, x);
                final double cy = Fask2.correxp(x, y, y);

                if (G.isAdjacentTo(X, Y) || (abs(cx - cy) > this.skewEdgeThreshold)) {
                    final double lr = lrs[i][j];// leftRight(x, y);

                    if (edgeForbiddenByKnowledge(X, Y) && edgeForbiddenByKnowledge(Y, X)) {
                        TetradLogger.getInstance().forceLogMessage(X + "\t" + Y + "\tknowledge_forbidden"
                                + "\t" + nf.format(lr)
                                + "\t" + X + "<->" + Y
                        );
                        continue;
                    }

                    if (knowledgeOrients(X, Y)) {
                        TetradLogger.getInstance().forceLogMessage(X + "\t" + Y + "\tknowledge"
                                + "\t" + nf.format(lr)
                                + "\t" + X + "-->" + Y
                        );
                        graph.addDirectedEdge(X, Y);
                    } else if (knowledgeOrients(Y, X)) {
                        TetradLogger.getInstance().forceLogMessage(X + "\t" + Y + "\tknowledge"
                                + "\t" + nf.format(lr)
                                + "\t" + X + "<--" + Y
                        );
                        graph.addDirectedEdge(Y, X);
                    } else {
                        if (zeroDiff(i, j, this.D)) {
                            TetradLogger.getInstance().forceLogMessage(X + "\t" + Y + "\t2-cycle Prescreen"
                                    + "\t" + nf.format(lr)
                                    + "\t" + X + "...TC?..." + Y
                            );

                            System.out.println(X + " " + Y + " lr = " + lr + " zero");
                            continue;
                        }

                        if (this.twoCycleScreeningCutoff > 0 && abs(faskLeftRightV2(x, y)) < this.twoCycleScreeningCutoff) {
                            TetradLogger.getInstance().forceLogMessage(X + "\t" + Y + "\t2-cycle Prescreen"
                                    + "\t" + nf.format(lr)
                                    + "\t" + X + "...TC?..." + Y
                            );

                            twoCycles.add(new NodePair(X, Y));
                            System.out.println(X + " " + Y + " lr = " + lr + " zero");
                        }

                        if (lr > 0) {
                            TetradLogger.getInstance().forceLogMessage(X + "\t" + Y + "\tleft-right"
                                    + "\t" + nf.format(lr)
                                    + "\t" + X + "-->" + Y
                            );
                            graph.addDirectedEdge(X, Y);
                        } else if (lr < 0) {
                            TetradLogger.getInstance().forceLogMessage(Y + "\t" + X + "\tleft-right"
                                    + "\t" + nf.format(lr)
                                    + "\t" + Y + "-->" + X
                            );
                            graph.addDirectedEdge(Y, X);
                        }
                    }
                }
            }
        }

        if (this.twoCycleScreeningCutoff > 0 && this.orientationAlpha == 0) {
            for (final NodePair edge : twoCycles) {
                final Node X = edge.getFirst();
                final Node Y = edge.getSecond();

                graph.removeEdges(X, Y);
                graph.addDirectedEdge(X, Y);
                graph.addDirectedEdge(Y, X);
                logTwoCycle(nf, variables, this.D, X, Y, "2-cycle Pre-screen");
            }
        } else if (this.twoCycleScreeningCutoff == 0 && this.orientationAlpha > 0) {
            for (final Edge edge : graph.getEdges()) {
                final Node X = edge.getNode1();
                final Node Y = edge.getNode2();

                final int i = variables.indexOf(X);
                final int j = variables.indexOf(Y);

                if (twoCycleTest(i, j, this.D, graph, variables)) {
                    graph.removeEdges(X, Y);
                    graph.addDirectedEdge(X, Y);
                    graph.addDirectedEdge(Y, X);
                    logTwoCycle(nf, variables, this.D, X, Y, "2-cycle Tested");
                }
            }
        } else if (this.twoCycleScreeningCutoff > 0 && this.orientationAlpha > 0) {
            for (final NodePair edge : twoCycles) {
                final Node X = edge.getFirst();
                final Node Y = edge.getSecond();

                final int i = variables.indexOf(X);
                final int j = variables.indexOf(Y);

                if (twoCycleTest(i, j, this.D, graph, variables)) {
                    graph.removeEdges(X, Y);
                    graph.addDirectedEdge(X, Y);
                    graph.addDirectedEdge(Y, X);
                    logTwoCycle(nf, variables, this.D, X, Y, "2-cycle Screened then Tested");
                }
            }
        }

        final long stop = System.currentTimeMillis();
        this.elapsed = stop - start;

        this.graph = graph;

        final double bic = new BicEst().getValue(null, graph, dataSet);
        graph.addAttribute("BIC", nf.format(bic));

        return graph;
    }

    private void logTwoCycle(final NumberFormat nf, final List<Node> variables, final double[][] d, final Node X, final Node Y, final String type) {
        final int i = variables.indexOf(X);
        final int j = variables.indexOf(Y);

        final double[] x = d[i];
        final double[] y = d[j];

        final double lr = leftRight(x, y);

        TetradLogger.getInstance().forceLogMessage(X + "\t" + Y + "\t" + type
                + "\t" + nf.format(lr)
                + "\t" + X + "<=>" + Y
        );
    }

    /**
     * Returns the coefficient matrix for the search. If the search has not yet run, runs it,
     * then estimates coefficients of each node given its parents using linear regression and forms
     * the B matrix of coefficients from these estimates. B[i][j] != 0 means i->j with that coefficient.
     */
    public double[][] getB() {
        if (this.graph == null) search();

        final List<Node> nodes = this.dataSet.getVariables();
        final double[][] B = new double[nodes.size()][nodes.size()];

        for (int j = 0; j < nodes.size(); j++) {
            final Node y = nodes.get(j);

            final List<Node> pary = this.graph.getParents(y);
            final RegressionResult result = this.regressionDataset.regress(y, pary);
            final double[] coef = result.getCoef();

            for (int i = 0; i < pary.size(); i++) {
                B[nodes.indexOf(pary.get(i))][j] = coef[i + 1];
            }
        }

        return B;
    }

    /**
     * Returns a natrux matrix of left-right scores for the search. If lr = getLrScores(), then
     * lr[i][j] is the left right scores leftRight(data[i], data[j]);
     */
    public double[][] getLrScores() {
        final List<Node> variables = this.dataSet.getVariables();
        final double[][] D = DataUtils.standardizeData(this.dataSet).getDoubleData().transpose().toArray();

        final double[][] lr = new double[variables.size()][variables.size()];

        for (int i = 0; i < variables.size(); i++) {
            for (int j = 0; j < variables.size(); j++) {
                lr[i][j] = leftRight(D[i], D[j]);
            }
        }

        this.D = D;

        return lr;
    }

    /**
     * @return The depth of search for the Fast Adjacency Search (FAS).
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * @param depth The depth of search for the Fast Adjacency Search (S). The default is -1.
     *              unlimited. Making this too high may results in statistical errors.
     */
    public void setDepth(final int depth) {
        this.depth = depth;
    }

    /**
     * @return The elapsed time in milliseconds.
     */
    public long getElapsedTime() {
        return this.elapsed;
    }

    /**
     * @return the current knowledge.
     */
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * @param knowledge Knowledge of forbidden and required edges.
     */
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    public void setExternalGraph(final Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    public void setSkewEdgeThreshold(final double skewEdgeThreshold) {
        this.skewEdgeThreshold = skewEdgeThreshold;
    }

    public void setTwoCycleScreeningCutoff(final double twoCycleScreeningCutoff) {
        if (twoCycleScreeningCutoff < 0)
            throw new IllegalStateException("Two cycle screening threshold must be >= 0");
        this.twoCycleScreeningCutoff = twoCycleScreeningCutoff;
    }

    public void setOrientationAlpha(final double orientationAlpha) {
        if (orientationAlpha < 0 || orientationAlpha > 1)
            throw new IllegalArgumentException("Two cycle testing alpha should be in [0, 1].");
        this.orientationCutoff = StatUtils.getZForAlpha(orientationAlpha);
        this.orientationAlpha = orientationAlpha;
    }

    public void setLeftRight(final LeftRight leftRight) {
        this.leftRight = leftRight;
    }

    public void setDelta(final double delta) {
        this.delta = delta;
    }

    //======================================== PRIVATE METHODS ====================================//

    public void setEmpirical(final boolean empirical) {
        this.empirical = empirical;
    }

    public double leftRight(final Node X, final Node Y) {
        final List<Node> variables = this.dataSet.getVariables();

        int i = -1;

        for (int k = 0; k < variables.size(); k++) {
            if (X.getName().equals(variables.get(k).getName())) i = k;
        }

        int j = -1;

        for (int k = 0; k < variables.size(); k++) {
            if (Y.getName().equals(variables.get(k).getName())) j = k;
        }

        final double[] x = this.D[i];
        final double[] y = this.D[j];

        return leftRight(x, y);

    }

    private double leftRight(final double[] x, final double[] y) {
        if (this.leftRight == LeftRight.FASK1) {
            return faskLeftRightV1(x, y);
        } else if (this.leftRight == LeftRight.FASK2) {
            return faskLeftRightV2(x, y);
        } else if (this.leftRight == LeftRight.RSKEW) {
            return robustSkew(x, y);
        } else if (this.leftRight == LeftRight.SKEW) {
            return skew(x, y);
        } else if (this.leftRight == LeftRight.TANH) {
            return tanh(x, y);
        }

        throw new IllegalStateException("Left right rule not configured: " + this.leftRight);
    }

    private double faskLeftRightV2(final double[] x, final double[] y) {
        final double sx = skewness(x);
        final double sy = skewness(y);
        final double r = correlation(x, y);
        double lr = Fask2.correxp(x, y, x) - Fask2.correxp(x, y, y);

        if (this.empirical) {
            lr *= signum(sx) * signum(sy);
        }

//        lr *= signum(r);

        if (r < this.delta) {
            lr *= -1;
        }

        return lr;
    }

    private double faskLeftRightV1(final double[] x, final double[] y) {
        final double left = Fask2.cu(x, y, x) / (sqrt(Fask2.cu(x, x, x) * Fask2.cu(y, y, x)));
        final double right = Fask2.cu(x, y, y) / (sqrt(Fask2.cu(x, x, y) * Fask2.cu(y, y, y)));
        double lr = left - right;

        double r = StatUtils.correlation(x, y);
        final double sx = StatUtils.skewness(x);
        final double sy = StatUtils.skewness(y);

        if (this.empirical) {
            r *= signum(sx) * signum(sy);
        }

        lr *= signum(r);
        if (r < this.delta) lr *= -1;

        return lr;
    }

    private double robustSkew(double[] x, double[] y) {

        if (this.empirical) {
            x = correctSkewness(x, skewness(x));
            y = correctSkewness(y, skewness(y));
        }

        final double[] lr = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            lr[i] = g(x[i]) * y[i] - x[i] * g(y[i]);
        }

        return correlation(x, y) * mean(lr);
    }

    private double skew(double[] x, double[] y) {

        if (this.empirical) {
            x = correctSkewness(x, skewness(x));
            y = correctSkewness(y, skewness(y));
        }

        final double[] lr = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            lr[i] = x[i] * x[i] * y[i] - x[i] * y[i] * y[i];
        }

        return correlation(x, y) * mean(lr);
    }

    private double tanh(double[] x, double[] y) {

        if (this.empirical) {
            x = correctSkewness(x, skewness(x));
            y = correctSkewness(y, skewness(y));
        }

        final double[] lr = new double[x.length];

        for (int i = 0; i < x.length; i++) {
            lr[i] = x[i] * Math.tanh(y[i]) - Math.tanh(x[i]) * y[i];
        }

        return correlation(x, y) * mean(lr);
    }

    private double g(final double x) {
        return Math.log(Math.cosh(Math.max(x, 0)));
    }

    private boolean knowledgeOrients(final Node X, final Node Y) {
        return this.knowledge.isForbidden(Y.getName(), X.getName()) || this.knowledge.isRequired(X.getName(), Y.getName());
    }

    private boolean edgeForbiddenByKnowledge(final Node X, final Node Y) {
        return this.knowledge.isForbidden(Y.getName(), X.getName()) && this.knowledge.isForbidden(X.getName(), Y.getName());
    }

    private double[] correctSkewness(final double[] data, final double sk) {
        final double[] data2 = new double[data.length];
        for (int i = 0; i < data.length; i++) data2[i] = data[i] * signum(sk);
        return data2;
    }

    private boolean twoCycleTest(final int i, final int j, final double[][] D, final Graph G0, final List<Node> V) {
        final Node X = V.get(i);
        final Node Y = V.get(j);

        final double[] x = D[i];
        final double[] y = D[j];

        final Set<Node> adjSet = new HashSet<>(G0.getAdjacentNodes(X));
        adjSet.addAll(G0.getAdjacentNodes(Y));
        final List<Node> adj = new ArrayList<>(adjSet);
        adj.remove(X);
        adj.remove(Y);

        final DepthChoiceGenerator gen = new DepthChoiceGenerator(adj.size(), Math.min(this.depth, adj.size()));
        int[] choice;

        while ((choice = gen.next()) != null) {
            final List<Node> _adj = GraphUtils.asList(choice, adj);
            final double[][] _Z = new double[_adj.size()][];

            for (int f = 0; f < _adj.size(); f++) {
                final Node _z = _adj.get(f);
                final int column = this.dataSet.getColumn(_z);
                _Z[f] = D[column];
            }

            final double pc;
            final double pc1;
            final double pc2;

            try {
                pc = partialCorrelation(x, y, _Z, x, Double.NEGATIVE_INFINITY);
                pc1 = partialCorrelation(x, y, _Z, x, 0);
                pc2 = partialCorrelation(x, y, _Z, y, 0);
            } catch (final SingularMatrixException e) {
                System.out.println("Singularity X = " + X + " Y = " + Y + " adj = " + adj);
                TetradLogger.getInstance().log("info", "Singularity X = " + X + " Y = " + Y + " adj = " + adj);
                continue;
            }

            final int nc = StatUtils.getRows(x, x, 0, Double.NEGATIVE_INFINITY).size();
            final int nc1 = StatUtils.getRows(x, x, 0, +1).size();
            final int nc2 = StatUtils.getRows(y, y, 0, +1).size();

            final double z = 0.5 * (log(1.0 + pc) - log(1.0 - pc));
            final double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
            final double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

            final double zv1 = (z - z1) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc1 - 3)));
            final double zv2 = (z - z2) / sqrt((1.0 / ((double) nc - 3) + 1.0 / ((double) nc2 - 3)));

            final boolean rejected1 = abs(zv1) > this.orientationCutoff;
            final boolean rejected2 = abs(zv2) > this.orientationCutoff;

            boolean possibleTwoCycle = false;

            if (zv1 < 0 && zv2 > 0 && rejected1) {
                possibleTwoCycle = true;
            } else if (zv1 > 0 && zv2 < 0 && rejected2) {
                possibleTwoCycle = true;
            } else if (rejected1 && rejected2) {
                possibleTwoCycle = true;
            }

            if (!possibleTwoCycle) {
                return false;
            }
        }

        return true;
    }

    private boolean zeroDiff(final int i, final int j, final double[][] D) {
        final double[] x = D[i];
        final double[] y = D[j];

        final double pc1;
        final double pc2;

        try {
            pc1 = partialCorrelation(x, y, new double[0][], x, 0);
            pc2 = partialCorrelation(x, y, new double[0][], y, 0);
        } catch (final SingularMatrixException e) {
            throw new RuntimeException(e);
        }

        final int nc1 = StatUtils.getRows(x, x, 0, +1).size();
        final int nc2 = StatUtils.getRows(y, y, 0, +1).size();

        final double z1 = 0.5 * (log(1.0 + pc1) - log(1.0 - pc1));
        final double z2 = 0.5 * (log(1.0 + pc2) - log(1.0 - pc2));

        final double zv = (z1 - z2) / sqrt((1.0 / ((double) nc1 - 3) + 1.0 / ((double) nc2 - 3)));

        return abs(zv) <= this.twoCycleScreeningCutoff;
    }

    private double partialCorrelation(final double[] x, final double[] y, final double[][] z, final double[] condition, final double threshold) throws
            SingularMatrixException {
        final double[][] cv = StatUtils.covMatrix(x, y, z, condition, threshold, 1);
        final Matrix m = new Matrix(cv).transpose();
        return StatUtils.partialCorrelation(m);
    }

    public void setNumRounds(final int numRounds) {
        this.numRounds = numRounds;
    }

    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    // The left-right rule to use. Options include the FASK left-right rule and three left-right rules
// from the Hyvarinen and Smith pairwise orientation paper: Robust Skew, Skew, and Tanh. In that
// paper, "empirical" versions were given in which the variables are multiplied through by the
// signs of the skewnesses; we follow this advice here (with good results). These others are provided
// for comparison; in general they are quite good.
    public enum LeftRight {FASK1, FASK2, RSKEW, SKEW, TANH}
}






