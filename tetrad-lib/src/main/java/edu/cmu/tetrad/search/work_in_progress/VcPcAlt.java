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

package edu.cmu.tetrad.search.work_in_progress;

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IGraphSearch;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.LogUtilsSearch;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.CombinationGenerator;
import edu.cmu.tetrad.util.MillisecondTimes;
import edu.cmu.tetrad.util.TetradLogger;

import java.util.*;

/**
 * Implements a convervative version of PC, in which the Markov condition is assumed but faithfulness is tested
 * locally.
 *
 * @author josephramsey (this version).
 */
public final class VcPcAlt implements IGraphSearch {

    /**
     * The independence test used for the PC search.
     */
    private final IndependenceTest independenceTest;

    /**
     * Forbidden and required edges for the search.
     */
    private Knowledge knowledge = new Knowledge();

    /**
     * The maximum number of nodes conditioned on in the search.
     */
    private int depth = 1000;

    private Graph graph;


    /**
     * Elapsed time of last search.
     */
    private long elapsedTime;

    /**
     * Set of unshielded colliders from the triple orientation step.
     */
    private Set<Triple> colliderTriples;

    /**
     * Set of unshielded noncolliders from the triple orientation step.
     */
    private Set<Triple> noncolliderTriples;

    /**
     * Set of ambiguous unshielded triples.
     */
    private Set<Triple> ambiguousTriples;

    private Set<Edge> definitelyNonadjacencies;

    private boolean meekPreventCycles;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * Whether verbose output about independencies is output.
     */
    private boolean verbose;

    //=============================CONSTRUCTORS==========================//

    /**
     * Constructs a CPC algorithm that uses the given independence test as oracle. This does not make a copy of the
     * independence test, for fear of duplicating the data set!
     */
    public VcPcAlt(IndependenceTest independenceTest) {
        if (independenceTest == null) {
            throw new NullPointerException();
        }

        this.independenceTest = independenceTest;
    }

    //==============================PUBLIC METHODS========================//

    /**
     * @return true just in case edges will not be added if they would create cycles.
     */
    public boolean isMeekPreventCycles() {
        return this.meekPreventCycles;
    }

    /**
     * Sets to true just in case edges will not be added if they would create cycles.
     */
    public void setMeekPreventCycles(boolean meekPreventCycles) {
        this.meekPreventCycles = meekPreventCycles;
    }

    /**
     * Sets the maximum number of variables conditioned on in any conditional independence test. If set to -1, the value
     * of 1000 will be used. May not be set to Integer.MAX_VALUE, due to a Java bug on multi-core systems.
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
     * @return the elapsed time of search in milliseconds, after <code>search()</code> has been run.
     */
    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * @return the knowledge specification used in the search. Non-null.
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
     * @return the depth of the search--that is, the maximum number of variables conditioned on in any conditional
     * independence test.
     */
    public int getDepth() {
        return this.depth;
    }

    /**
     * @return the set of ambiguous triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    /**
     * @return the set of collider triples found during the most recent run of the algorithm. Non-null after a call to
     * <code>search()</code>.
     */
    public Set<Triple> getColliderTriples() {
        return new HashSet<>(this.colliderTriples);
    }

    /**
     * @return the set of noncollider triples found during the most recent run of the algorithm. Non-null after a call
     * to <code>search()</code>.
     */
    public Set<Triple> getNoncolliderTriples() {
        return new HashSet<>(this.noncolliderTriples);
    }

    //  modified FAS into VCFAS; added in definitelyNonadjacencies set of edges.
    public Graph search() {
        this.logger.log("info", "Starting VCCPC algorithm");
        this.logger.log("info", "Independence test = " + getIndependenceTest() + ".");
        this.ambiguousTriples = new HashSet<>();
        this.colliderTriples = new HashSet<>();
        this.noncolliderTriples = new HashSet<>();
        VcFas fas = new VcFas(getIndependenceTest());
        this.definitelyNonadjacencies = new HashSet<>();

        long startTime = MillisecondTimes.timeMillis();

        List<Node> allNodes = getIndependenceTest().getVariables();

        fas.setKnowledge(getKnowledge());
        fas.setDepth(getDepth());
        fas.setVerbose(this.verbose);

        // Note that we are ignoring the sepset map returned by this method
        // on purpose; it is not used in this search.
        this.graph = fas.search();

        Map<Edge, Set<Node>> apparentlyNonadjacencies = fas.getApparentlyNonadjacencies();

        if (isDoOrientation()) {
            if (this.verbose) {
                System.out.println("CPC orientation...");
            }
            GraphSearchUtils.pcOrientbk(this.knowledge, this.graph, allNodes);
            orientUnshieldedTriples(this.knowledge, getIndependenceTest(), getDepth());
//            orientUnshieldedTriplesConcurrent(knowledge, getIndependenceTest(), getMaxIndegree());
            MeekRules meekRules = new MeekRules();

            meekRules.setMeekPreventCycles(this.meekPreventCycles);
            meekRules.setKnowledge(this.knowledge);

            meekRules.orientImplied(this.graph);
        }


        List<Triple> ambiguousTriples = new ArrayList<>(this.graph.getAmbiguousTriples());

        int[] dims = new int[ambiguousTriples.size()];

        for (int i = 0; i < ambiguousTriples.size(); i++) {
            dims[i] = 2;
        }

        List<Graph> patterns = new ArrayList<>();
        Map<Graph, List<Triple>> newColliders = new IdentityHashMap<>();
        Map<Graph, List<Triple>> newNonColliders = new IdentityHashMap<>();

//      Using combination generator to generate a list of combinations of ambiguous triples dismabiguated into colliders
//      and non-colliders. The combinations are added as graphs to the list patterns. The graphs are then subject to
//      basic rules to ensure consistent patterns.


        CombinationGenerator generator = new CombinationGenerator(dims);
        int[] combination;

        while ((combination = generator.next()) != null) {
            Graph _graph = new EdgeListGraph(this.graph);
            newColliders.put(_graph, new ArrayList<>());
            newNonColliders.put(_graph, new ArrayList<>());

            for (int k = 0; k < combination.length; k++) {
                Triple triple = ambiguousTriples.get(k);
                _graph.removeAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());


                if (combination[k] == 0) {
                    newColliders.get(_graph).add(triple);
                    Node x = triple.getX();
                    Node y = triple.getY();
                    Node z = triple.getZ();

                    _graph.setEndpoint(x, y, Endpoint.ARROW);
                    _graph.setEndpoint(z, y, Endpoint.ARROW);

                }
                if (combination[k] == 1) {
                    newNonColliders.get(_graph).add(triple);
                }
            }
            patterns.add(_graph);
        }

        ///    Takes patterns and runs them through basic constraints to ensure consistent patterns (e.g. no cycles, no bidirected edges).

        GRAPH:

        for (Graph graph : new ArrayList<>(patterns)) {

            List<Triple> colliders = newColliders.get(graph);
            List<Triple> nonColliders = newNonColliders.get(graph);


            for (Triple triple : colliders) {
                Node x = triple.getX();
                Node y = triple.getY();
                Node z = triple.getZ();

                if (graph.getEdge(x, y).pointsTowards(x) || (graph.getEdge(y, z).pointsTowards(z))) {
                    patterns.remove(graph);
                    continue GRAPH;
                }
            }

            for (Triple triple : colliders) {
                Node x = triple.getX();
                Node y = triple.getY();
                Node z = triple.getZ();

                graph.setEndpoint(x, y, Endpoint.ARROW);
                graph.setEndpoint(z, y, Endpoint.ARROW);
            }

            for (Triple triple : nonColliders) {
                Node x = triple.getX();
                Node y = triple.getY();
                Node z = triple.getZ();

                if (graph.getEdge(x, y).pointsTowards(y)) {
                    graph.removeEdge(y, z);
                    graph.addDirectedEdge(y, z);
                }
                if (graph.getEdge(y, z).pointsTowards(y)) {
                    graph.removeEdge(x, y);
                    graph.addDirectedEdge(y, x);
                }
            }

            for (Edge edge : graph.getEdges()) {
                if (Edges.isBidirectedEdge(edge)) {
                    patterns.remove(graph);
                    continue GRAPH;
                }
            }

            MeekRules rules = new MeekRules();
            rules.orientImplied(graph);
            if (graph.paths().existsDirectedCycle()) {
                patterns.remove(graph);
            }
        }

//        "some" version: For each apparently non-adjacent pair X and Y, if X and Y are independent given *some* subset of
//         X's possible parents or *some* subset of Y's possible parents, then X and Y are definitely non-adjacent.
        // 4/8/15 Local Relative Markov (M2)

        MARKOV:

        for (Edge edge : apparentlyNonadjacencies.keySet()) {
            Node x = edge.getNode1();
            Node y = edge.getNode2();

            for (Graph _graph : new ArrayList<>(patterns)) {

                Set<Node> boundaryX = new HashSet<>(boundary(x, _graph));
                Set<Node> boundaryY = new HashSet<>(boundary(y, _graph));
                Set<Node> futureX = new HashSet<>(future(x, _graph));
                Set<Node> futureY = new HashSet<>(future(y, _graph));

                if (y == x) {
                    continue;
                }
                if (boundaryX.contains(y) || boundaryY.contains(x)) {
                    continue;
                }
                IndependenceTest test = this.independenceTest;

                if (!futureX.contains(y)) {
                    if (test.checkIndependence(x, y, boundaryX).isIndependent()) {
                        if (!futureY.contains(x)) {
                            if (test.checkIndependence(y, x, boundaryY).isIndependent()) {
                                this.definitelyNonadjacencies.add(edge);
                                continue MARKOV;
                            }
                        }
                    }
                }
            }
        }

        for (Edge edge : this.definitelyNonadjacencies) {
            if (apparentlyNonadjacencies.containsKey(edge)) {
                apparentlyNonadjacencies.keySet().remove(edge);
            }
        }

        //        Step V5. For each consistent disambiguation of the ambiguous triples
//                we test whether the resulting pattern satisfies Markov. If
//                every pattern does, then mark all the apparently non-adjacent
//                pairs as definitely non-adjacent.
        System.out.println("Definitely Nonadjacencies:");

        for (Edge edge : this.definitelyNonadjacencies) {
            System.out.println(edge);
        }

        System.out.println("patterns:" + patterns);
        System.out.println("Apparently Nonadjacencies:");


        for (Edge edge : apparentlyNonadjacencies.keySet()) {
            System.out.println(edge);
        }
        System.out.println("Definitely Nonadjacencies:");


        for (Edge edge : this.definitelyNonadjacencies) {
            System.out.println(edge);
        }

        TetradLogger.getInstance().log("apparentlyNonadjacencies", "\n Apparent Non-adjacencies" + apparentlyNonadjacencies);

        TetradLogger.getInstance().log("definitelyNonadjacencies", "\n Definite Non-adjacencies" + this.definitelyNonadjacencies);

        TetradLogger.getInstance().log("patterns", "Disambiguated Patterns: " + patterns);


        TetradLogger.getInstance().log("graph", "\nReturning this graph: " + this.graph);

        long endTime = MillisecondTimes.timeMillis();
        this.elapsedTime = endTime - startTime;

        TetradLogger.getInstance().log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        TetradLogger.getInstance().log("info", "Finishing CPC algorithm.");

        logTriples();

        TetradLogger.getInstance().flush();
        return this.graph;
    }

    //==========================PRIVATE METHODS===========================//

    //    For a node x, adds nodes y such that either y-x or y->x to the boundary of x
    private Set<Node> boundary(Node x, Graph graph) {
        Set<Node> boundary = new HashSet<>();
        List<Node> adj = graph.getAdjacentNodes(x);
        for (Node y : adj) {
            if (graph.isParentOf(y, x) || Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                boundary.add(y);
            }
        }
        return boundary;
    }

    //      For a node x, adds nodes y such that either x->..->y or x-..-..->..->y to the future of x
    private Set<Node> future(Node x, Graph graph) {
        Set<Node> futureNodes = new HashSet<>();
        LinkedList<Node> path = new LinkedList<>();
        VcPcAlt.futureNodeVisit(graph, x, path, futureNodes);
        futureNodes.remove(x);
        List<Node> adj = graph.getAdjacentNodes(x);
        for (Node y : adj) {
            if (graph.isParentOf(y, x) || Edges.isUndirectedEdge(graph.getEdge(x, y))) {
                futureNodes.remove(y);
            }
        }
        return futureNodes;
    }

    //    Constraints to guarantee future path conditions met. After traversing the entire path,
//    returns last node on path when satisfied, stops otherwise.
    private static Node traverseFuturePath(Node node, Edge edge1, Edge edge2) {
        Endpoint E1 = edge1.getProximalEndpoint(node);
        Endpoint E2 = edge2.getProximalEndpoint(node);
        Endpoint E3 = edge2.getDistalEndpoint(node);
        Endpoint E4 = edge1.getDistalEndpoint(node);
        if (E1 == Endpoint.ARROW && E2 == Endpoint.ARROW && E3 == Endpoint.TAIL) {
            return null;
        }
        if (E4 == Endpoint.ARROW) {
            return null;
        }
        if (E4 == Endpoint.TAIL && E1 == Endpoint.TAIL && E2 == Endpoint.TAIL && E3 == Endpoint.TAIL) {
            return null;
        }
        return edge2.getDistalNode(node);
    }

    //    Takes a triple n1-n2-child and adds child to futureNodes set if satisfies constraints for future.
//    Uses traverseFuturePath to add nodes to set.
    public static void futureNodeVisit(Graph graph, Node b, LinkedList<Node> path, Set<Node> futureNodes) {
        path.addLast(b);
        futureNodes.add(b);
        for (Edge edge2 : graph.getEdges(b)) {
            Node c;

            int size = path.size();
            if (path.size() < 2) {
                c = edge2.getDistalNode(b);
            } else {
                Node a = path.get(size - 2);
                Edge edge1 = graph.getEdge(a, b);
                c = VcPcAlt.traverseFuturePath(b, edge1, edge2);
            }
            if (c == null) {
                continue;
            }
            if (path.contains(c)) {
                continue;
            }
            VcPcAlt.futureNodeVisit(graph, c, path, futureNodes);
        }
        path.removeLast();
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
                "\nthere is ambiguous data about whether they are colliders or not):");

        for (Triple triple : getAmbiguousTriples()) {
            TetradLogger.getInstance().log("info", "Ambiguous: " + triple);
        }
    }

    private void orientUnshieldedTriples(Knowledge knowledge,
                                         IndependenceTest test, int depth) {
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
                Node x = adjacentNodes.get(combination[0]);
                Node z = adjacentNodes.get(combination[1]);

                if (this.graph.isAdjacentTo(x, z)) {
                    continue;
                }

                GraphSearchUtils.CpcTripleType type = GraphSearchUtils.getCpcTripleType(x, y, z, test, depth, graph);
//                SearchGraphUtils.CpcTripleType type = SearchGraphUtils.getCpcTripleType2(x, y, z, test, depth, graph);

                if (type == GraphSearchUtils.CpcTripleType.COLLIDER) {
                    if (this.colliderAllowed(x, y, z, knowledge)) {
                        graph.setEndpoint(x, y, Endpoint.ARROW);
                        graph.setEndpoint(z, y, Endpoint.ARROW);

                        TetradLogger.getInstance().log("colliderOrientations", LogUtilsSearch.colliderOrientedMsg(x, y, z));
                    }

                    colliderTriples.add(new Triple(x, y, z));
                } else if (type == GraphSearchUtils.CpcTripleType.AMBIGUOUS) {
                    Triple triple = new Triple(x, y, z);
                    ambiguousTriples.add(triple);
                    graph.addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
                    Edge edge = Edges.undirectedEdge(x, z);
                    definitelyNonadjacencies.add(edge);
                } else {
                    noncolliderTriples.add(new Triple(x, y, z));
                }
            }
        }

        TetradLogger.getInstance().log("info", "Finishing Collider Orientation.");
    }

    private boolean colliderAllowed(Node x, Node y, Node z, Knowledge knowledge) {
        if (!GraphSearchUtils.isArrowheadAllowed(x, y, knowledge)) return false;
        return GraphSearchUtils.isArrowheadAllowed(z, y, knowledge);
    }

    public boolean isDoOrientation() {
        return true;
    }

    /**
     * The graph that's constructed during the search.
     */
    public Graph getGraph() {
        return this.graph;
    }

    public void setGraph(Graph graph) {
        this.graph = graph;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }


}

