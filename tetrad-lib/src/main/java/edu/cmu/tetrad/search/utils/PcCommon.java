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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.*;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Prodies some common implementation pieces of variaous PC-like algorithms, with options for collider discovery type,
 * FAS type, and conflict rule.
 *
 * @author josephramsey
 */
public final class PcCommon implements IGraphSearch {

    /**
     * <p>NONE = no heuristic, PC-1 = sort nodes alphabetically; PC-1 = sort edges by p-value; PC-3 = additionally sort
     * edges in reverse order using p-values of associated independence facts. See this reference:</p>
     *
     * <p>Spirtes, P., Glymour, C. N., & Scheines, R. (2000). Causation, prediction, and search. MIT press.</p>
     */
    public enum PcHeuristicType {NONE, HEURISTIC_1, HEURISTIC_2, HEURISTIC_3}

    /**
     * Gives the type of FAS used, regular or stable.
     *
     * @see Pc
     * @see Cpc
     * @see FasType
     */
    public enum FasType {REGULAR, STABLE}

    /**
     * <p>Give the options for the collider discovery algroithm to use--FAS with sepsets reasoning, FAS with conservative
     * reasoning, or FAS with Max P reasoning. See these respective references:</p>
     *
     * <p>Spirtes, P., Glymour, C. N., & Scheines, R. (2000). Causation, prediction, and search. MIT press.</p>

     * <p>Ramsey, J., Zhang, J., &amp; Spirtes, P. L. (2012). Adjacency-faithfulness and conservative causal inference.
     * arXiv preprint arXiv:1206.6843.</p>
     *
     * <p>Ramsey, J. (2016). Improving accuracy and scalability of the pc algorithm by maximizing p-value. arXiv
     * preprint arXiv:1610.00378.</p>
     *
     * @see Fas
     * @see Cpc
     * @see ColliderDiscovery
     */
    public enum ColliderDiscovery {FAS_SEPSETS, CONSERVATIVE, MAX_P}

    /**
     * Gives the type of conflict to be used, priority (when there is a conflict, keep the orientation that has already
     * been made), bidirected (when there is a conflict, orient a bidirected edge), or overwrite (when there is a
     * conflict, use the new orientation).
     *
     * @see Pc
     * @see Cpc
     * @see ConflictRule
     */
    public enum ConflictRule {PRIORITIZE_EXISTING, ORIENT_BIDIRECTED, OVERWRITE_EXISTING}

    private final IndependenceTest independenceTest;
    private final TetradLogger logger = TetradLogger.getInstance();
    private Knowledge knowledge = new Knowledge();
    private int depth = 1000;
    private Graph graph;
    private long elapsedTime;
    private Set<Triple> colliderTriples;
    private Set<Triple> noncolliderTriples;
    private Set<Triple> ambiguousTriples;
    private boolean meekPreventCycles;
    private boolean verbose = false;
    private int maxPathLength = 3;
    private FasType fasType = FasType.REGULAR;
    private ColliderDiscovery colliderDiscovery = ColliderDiscovery.FAS_SEPSETS;
    private ConflictRule conflictRule = ConflictRule.PRIORITIZE_EXISTING;
    private PcHeuristicType pcHeuristicType = PcHeuristicType.NONE;

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     *
     * @param independenceTest The independence test to use.
     */
    public PcCommon(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    /**
     * @param maxPathLength The max path length for the max p collider orientation heuristic.
     */
    public void setMaxPathLength(int maxPathLength) {
        this.maxPathLength = maxPathLength;
    }

    /**
     * @param fasType The type of FAS to be used.
     */
    public void setFasType(FasType fasType) {
        this.fasType = fasType;
    }

    /**
     * @param pcHeuristic Which PC heuristic to use (see Causation, Prediction and Search). Default is
     *                    PcHeuristicType.NONE.
     * @see PcHeuristicType
     */
    public void setPcHeuristicType(PcHeuristicType pcHeuristic) {
        this.pcHeuristicType = pcHeuristic;
    }

    /**
     * @return true just in case edges will not be added if they would create cycles.
     */
    public boolean isMeekPreventCycles() {
        return this.meekPreventCycles;
    }

    /**
     * Runs the search and returns the search graph.
     *
     * @return This result graph.
     */
    public Graph search() {
        return search(getIndependenceTest().getVariables());
    }

    /**
     * Runs the search over the given list of nodes only, returning the serach graph.
     *
     * @param nodes The nodes to search over.
     * @return The result graph.
     */
    public Graph search(List<Node> nodes) {
        nodes = new ArrayList<>(nodes);

        this.logger.log("info", "Starting algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();

        this.independenceTest.setVerbose(this.verbose);

        long startTime = MillisecondTimes.timeMillis();

        List<Node> allNodes = getIndependenceTest().getVariables();

        if (!new HashSet<>(allNodes).containsAll(nodes)) {
            throw new IllegalArgumentException("All of the given nodes must " +
                    "be in the domain of the independence test provided.");
        }

        Fas fas;

        if (this.fasType == FasType.REGULAR) {
            fas = new Fas(getIndependenceTest());
            fas.setPcHeuristicType(this.pcHeuristicType);
        } else {
            fas = new Fas(getIndependenceTest());
            fas.setPcHeuristicType(this.pcHeuristicType);
            fas.setStable(true);
        }

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        this.graph = fas.search();
        SepsetMap sepsets = fas.getSepsets();

        if (this.graph.paths().existsDirectedCycle())
            throw new IllegalArgumentException("Graph is cyclic after sepsets!");

        GraphSearchUtils.pcOrientbk(this.knowledge, this.graph, nodes);

        if (this.colliderDiscovery == ColliderDiscovery.FAS_SEPSETS) {
            orientCollidersUsingSepsets(sepsets, this.knowledge, this.graph, this.verbose, this.conflictRule);
        } else if (this.colliderDiscovery == ColliderDiscovery.MAX_P) {
            if (this.verbose) {
                System.out.println("MaxP orientation...");
            }

            if (this.graph.paths().existsDirectedCycle())
                throw new IllegalArgumentException("Graph is cyclic before maxp!");

            MaxP orientCollidersMaxP = new MaxP(this.independenceTest);
            orientCollidersMaxP.setConflictRule(this.conflictRule);
            orientCollidersMaxP.setMaxPathLength(this.maxPathLength);
            orientCollidersMaxP.setDepth(this.depth);
            orientCollidersMaxP.setKnowledge(this.knowledge);
            orientCollidersMaxP.orient(this.graph);
        } else if (this.colliderDiscovery == ColliderDiscovery.CONSERVATIVE) {
            if (this.verbose) {
                System.out.println("CPC orientation...");
            }

            orientUnshieldedTriplesConservative(this.knowledge);
        }

        this.graph = GraphUtils.replaceNodes(this.graph, nodes);

        MeekRules meekRules = new MeekRules();
        meekRules.setKnowledge(this.knowledge);
        meekRules.setVerbose(verbose);
        meekRules.setMeekPreventCycles(this.meekPreventCycles);
        meekRules.orientImplied(this.graph);

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - startTime;

        TetradLogger.getInstance().log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");

        logTriples();

        TetradLogger.getInstance().flush();

        return this.graph;
    }

    /**
     * Sets to true just in case edges will not be added if they would create cycles.
     *
     * @param meekPreventCycles True just in case edges will not be added if they would create cycles.
     */
    public void setMeekPreventCycles(boolean meekPreventCycles) {
        this.meekPreventCycles = meekPreventCycles;
    }

    /**
     * Sets the type of collider discovery to do.
     *
     * @param colliderDiscovery This type.
     */
    public void setColliderDiscovery(ColliderDiscovery colliderDiscovery) {
        this.colliderDiscovery = colliderDiscovery;
    }

    /**
     * Sets the conflict rule to use.
     *
     * @param conflictRule This rule.
     * @see ConflictRule
     */
    public void setConflictRule(ConflictRule conflictRule) {
        this.conflictRule = conflictRule;
    }

    /**
     * @return The elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * @return The knowledge specification used in the search. Non-null.
     */
    public Knowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the knowledge specification used in the search. Non-null.
     */
    public void setKnowledge(Knowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * @return the independence test used in the search, set in the constructor. This is not returning a copy, for fear
     * of duplicating the data set!
     */
    public IndependenceTest getIndependenceTest() {
        return this.independenceTest;
    }

    /**
     * @return The depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multi-core systems.
     *
     * @param depth The depth.
     */
    public void setDepth(int depth) {
        if (depth < -1) {
            throw new IllegalArgumentException("Depth must be -1 or >= 0: " + depth);
        }

        if (depth == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Depth must not be Integer.MAX_VALUE, " +
                    "due to a known bug.");
        }

        this.depth = depth;
    }

    /**
     * Sets whether verbose output should be printed.
     *
     * @param verbose True iff the case.
     */
    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * @return The set of ambiguous triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * @return The set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        return new HashSet<>(this.colliderTriples);
    }

    /**
     * @return The set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(this.noncolliderTriples);
    }

    /**
     * Returns The edges in the search graph.
     *
     * @return These edges.
     */
    public Set<Edge> getAdjacencies() {
        return new HashSet<>(this.graph.getEdges());
    }

    /**
     * Orient a single unshielded triple, x*-*y*-*z, in a graph.
     *
     * @param conflictRule The conflict rule to use.
     * @param graph        The graph to orient.
     * @see PcCommon.ConflictRule
     */
    public static void orientCollider(Node x, Node y, Node z, ConflictRule conflictRule, Graph graph) {
        if (conflictRule == ConflictRule.PRIORITIZE_EXISTING) {
            if (!(graph.getEndpoint(x, y) == Endpoint.ARROW && graph.getEndpoint(z, y) == Endpoint.ARROW)) {
                graph.removeEdge(x, y);
                graph.removeEdge(z, y);
                graph.addDirectedEdge(x, y);
                graph.addDirectedEdge(z, y);
            }
        } else if (conflictRule == ConflictRule.ORIENT_BIDIRECTED) {
            graph.setEndpoint(x, y, Endpoint.ARROW);
            graph.setEndpoint(z, y, Endpoint.ARROW);

            System.out.println("Orienting " + graph.getEdge(x, y) + " " + graph.getEdge(z, y));
        } else if (conflictRule == ConflictRule.OVERWRITE_EXISTING) {
            graph.removeEdge(x, y);
            graph.removeEdge(z, y);
            graph.addDirectedEdge(x, y);
            graph.addDirectedEdge(z, y);
        }

        TetradLogger.getInstance().log("colliderOrientations", LogUtilsSearch.colliderOrientedMsg(x, y, z));
    }

    private void logTriples() {
        TetradLogger.getInstance().log("info", "\nCollider triples:");

        for (Triple triple : this.colliderTriples) {
            TetradLogger.getInstance().log("info", "Collider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nNoncollider triples:");

        for (Triple triple : this.noncolliderTriples) {
            TetradLogger.getInstance().log("info", "Noncollider: " + triple);
        }

        TetradLogger.getInstance().log("info", "\nAmbiguous triples (i.e. list of triples for which " +
                "\nthere is ambiguous data about whether they are colliderDiscovery or not):");

        for (Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
        }
    }

    private void orientUnshieldedTriplesConservative(Knowledge knowledge) {
        TetradLogger.getInstance().log("info", "Starting Collider Orientation:");

        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        this.ambiguousTriples = new HashSet<>();
        List<Node> nodes = this.graph.getNodes();

        for (Node y : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(this.graph.getAdjacentNodes(y));

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (this.graph.isAdjacentTo(x, z)) {
                    continue;
                }

                Set<Set<Node>> sepsetsxz = getSepsets(x, z, this.graph);

                if (isColliderSepset(y, sepsetsxz)) {
                    if (colliderAllowed(x, y, z, knowledge)) {
                        PcCommon.orientCollider(x, y, z, this.conflictRule, this.graph);
                    }

                    this.colliderTriples.add(new Triple(x, y, z));
                } else if (isNoncolliderSepset(y, sepsetsxz)) {
                    this.noncolliderTriples.add(new Triple(x, y, z));
                } else {
                    Triple triple = new Triple(x, y, z);
                    this.ambiguousTriples.add(triple);
                    this.graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private Set<Set<Node>> getSepsets(Node i, Node k, Graph g) {
        List<Node> adji = new ArrayList<>(g.getAdjacentNodes(i));
        List<Node> adjk = new ArrayList<>(g.getAdjacentNodes(k));
        Set<Set<Node>> sepsets = new HashSet<>();

        for (int d = 0; d <= FastMath.max(adji.size(), adjk.size()); d++) {
            if (adji.size() >= 2 && d <= adji.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adji.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    Set<Node> v = GraphUtils.asSet(choice, adji);
                    if (getIndependenceTest().checkIndependence(i, k, v).isIndependent()) sepsets.add(v);
                }
            }

            if (adjk.size() >= 2 && d <= adjk.size()) {
                ChoiceGenerator gen = new ChoiceGenerator(adjk.size(), d);
                int[] choice;

                while ((choice = gen.next()) != null) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    Set<Node> v = GraphUtils.asSet(choice, adjk);
                    if (getIndependenceTest().checkIndependence(i, k, v).isIndependent()) sepsets.add(v);
                }
            }
        }

        return sepsets;
    }

    private boolean isColliderSepset(Node j, Set<Set<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (Set<Node> sepset : sepsets) {
            if (sepset.contains(j)) return false;
        }

        return true;
    }

    private boolean isNoncolliderSepset(Node j, Set<Set<Node>> sepsets) {
        if (sepsets.isEmpty()) return false;

        for (Set<Node> sepset : sepsets) {
            if (!sepset.contains(j)) return false;
        }

        return true;
    }

    private boolean colliderAllowed(Node x, Node y, Node z, Knowledge knowledge) {
        boolean result = true;
        if (knowledge != null) {
            result = !knowledge.isRequired(((Object) y).toString(), ((Object) x).toString())
                    && !knowledge.isForbidden(((Object) x).toString(), ((Object) y).toString());
        }
        if (!result) return false;
        if (knowledge == null) {
            return true;
        }
        return !knowledge.isRequired(((Object) y).toString(), ((Object) z).toString())
                && !knowledge.isForbidden(((Object) z).toString(), ((Object) y).toString());
    }

    /**
     * Step C of PC; orients colliders using specified sepset. That is, orients x *-* y *-* z as x *-&gt; y &lt;-* z
     * just in case y is in Sepset({x, z}).
     */
    private void orientCollidersUsingSepsets(SepsetMap set, Knowledge knowledge, Graph graph, boolean verbose,
                                             ConflictRule conflictRule) {
        if (verbose) {
            System.out.println("FAS Sepset orientation...");
        }

        TetradLogger.getInstance().log("details", "Starting Collider Orientation:");

        List<Node> nodes = graph.getNodes();

        for (Node b : nodes) {
            List<Node> adjacentNodes = new ArrayList<>(graph.getAdjacentNodes(b));

            if (adjacentNodes.size() < 2) {
                continue;
            }

            ChoiceGenerator cg = new ChoiceGenerator(adjacentNodes.size(), 2);
            int[] combination;

            while ((combination = cg.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                Node a = adjacentNodes.get(combination[0]);
                Node c = adjacentNodes.get(combination[1]);

                // Skip triples that are shielded.
                if (graph.isAdjacentTo(a, c)) {
                    continue;
                }

                Set<Node> sepset = set.get(a, c);

                List<Node> s2 = new ArrayList<>(sepset);
                if (!s2.contains(b)) s2.add(b);

                if (!sepset.contains(b)) {
                    boolean result1 = true;
                    if (knowledge != null) {
                        result1 = !knowledge.isRequired(((Object) b).toString(), ((Object) a).toString())
                                && !knowledge.isForbidden(((Object) a).toString(), ((Object) b).toString());
                    }
                    if (result1) {
                        boolean result = true;
                        if (knowledge != null) {
                            result = !knowledge.isRequired(((Object) b).toString(), ((Object) c).toString())
                                    && !knowledge.isForbidden(((Object) c).toString(), ((Object) b).toString());
                        }
                        if (result) {
                            PcCommon.orientCollider(a, b, c, conflictRule, graph);

                            if (verbose) {
                                System.out.println("Collider orientation <" + a + ", " + b + ", " + c + "> sepset = " + sepset);
                            }

                            TetradLogger.getInstance().log("colliderOrientations", LogUtilsSearch.colliderOrientedMsg(a, b, c, sepset));
                        }
                    }
                }
            }
        }
    }
}

