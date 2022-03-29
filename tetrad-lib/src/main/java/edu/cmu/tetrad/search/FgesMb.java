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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.data.KnowledgeEdge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.util.ChoiceGenerator;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.TaskManager;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * GesSearch is an implementation of the GES algorithm, as specified in
 * Chickering (2002) "Optimal structure identification with greedy search"
 * Journal of Machine Learning Research. It works for both BayesNets and SEMs.
 * <p>
 * Some code optimization could be done for the scoring part of the graph for
 * discrete models (method scoreGraphChange). Some of Andrew Moore's approaches
 * for caching sufficient statistics, for instance.
 * <p>
 * To speed things up, it has been assumed that variables X and Y with zero
 * correlation do not correspond to edges in the graph. This is a restricted
 * form of the heuristicSpeedup assumption, something GES does not assume. This
 * the graph. This is a restricted form of the heuristicSpeedup assumption,
 * something GES does not assume. This heuristicSpeedup assumption needs to be
 * explicitly turned on using setHeuristicSpeedup(true).
 * <p>
 * A number of other optimizations were added 5/2015. See code for details.
 *
 * @author Ricardo Silva, Summer 2003
 * @author Joseph Ramsey, Revisions 5/2015
 */
public final class FgesMb {

    private List<Node> targets;

    /**
     * Internal.
     */
    private enum Mode {
        allowUnfaithfulness, heuristicSpeedup, coverNoncolliders
    }

    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();

    /**
     * List of variables in the data set, in order.
     */
    private List<Node> variables;

    /**
     * The true graph, if known. If this is provided, asterisks will be printed
     * out next to false positive added edges (that is, edges added that aren't
     * adjacencies in the true graph).
     */
    private Graph trueGraph;

    /**
     * An initial graph to start from.
     */
    private Graph externalGraph;

    /**
     * If non-null, edges not adjacent in this graph will not be added.
     */
    private Graph boundGraph = null;

    /**
     * Elapsed time of the most recent search.
     */
    private long elapsedTime;

    /**
     * The depth of search for the forward reevaluation step.
     */
    private final int depth = -1;

    /**
     * A bound on cycle length.
     */
    private int cycleBound = -1;

    /**
     * The totalScore for discrete searches.
     */
    private Score fgesScore;

    /**
     * The logger for this class. The config needs to be set.
     */
    private final TetradLogger logger = TetradLogger.getInstance();

    /**
     * The top n graphs found by the algorithm, where n is numCPDAGsToStore.
     */
    private final LinkedList<ScoredGraph> topGraphs = new LinkedList<>();

    /**
     * The number of top CPDAGs to store.
     */
    private int numCPDAGsToStore = 0;

    /**
     * True if verbose output should be printed.
     */
    private boolean verbose = false;

    // Potential arrows sorted by bump high to low. The first one is a candidate for adding to the graph.
    private SortedSet<Arrow> sortedArrows = null;

    // Arrows added to sortedArrows for each <i, j>.
    private Map<OrderedPair<Node>, Set<Arrow>> lookupArrows = null;

    // A utility map to help with orientation.
    private Map<Node, Set<Node>> neighbors = null;

    // Map from variables to their column indices in the data set.
    private ConcurrentMap<Node, Integer> hashIndices;

    // The static ForkJoinPool instance.
    private ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

    // A running tally of the total BIC totalScore.
    private double totalScore;

    // A graph where X--Y means that X and Y have non-zero total effect on one another.
    private Graph effectEdgesGraph;

    // The minimum number of operations to do before parallelizing.
    private final int minChunk = 100;

    // Where printed output is sent.
    private PrintStream out = System.out;

    // A initial adjacencies graph.
    private Graph adjacencies = null;

    // The graph being constructed.
    private Graph graph;

    // Arrows with the same totalScore are stored in this list to distinguish their order in sortedArrows.
    // The ordering doesn't matter; it just have to be transitive.
    int arrowIndex = 0;

    // The final totalScore after search.
    private double modelScore;

    // Internal.
    private Mode mode = Mode.heuristicSpeedup;

    // Bounds the degree of the graph.
    private int maxDegree = -1;

    /**
     * True if one-edge faithfulness is assumed. Speeds the algorithm up.
     */
    private boolean faithfulnessAssumed = true;

    final int maxThreads = ForkJoinPoolInstance.getInstance().getPool().getParallelism();

    //===========================CONSTRUCTORS=============================//

    /**
     * Construct a Score and pass it in here. The totalScore should return a
     * positive value in case of conditional dependence and a negative values in
     * case of conditional independence. See Chickering (2002), locally
     * consistent scoring criterion.
     */
    public FgesMb(final Score score) {
        if (score == null) {
            throw new NullPointerException();
        }
        setFgesScore(score);
        this.graph = new EdgeListGraph(getVariables());
    }

    //==========================PUBLIC METHODS==========================//

    /**
     * Set to true if it is assumed that all path pairs with one length 1 path
     * do not cancel.
     */
    public void setFaithfulnessAssumed(final boolean faithfulnessAssumed) {
        this.faithfulnessAssumed = true;
    }

    /**
     * @return true if it is assumed that all path pairs with one length 1 path
     * do not cancel.
     */
    public boolean isFaithfulnessAssumed() {
        return this.faithfulnessAssumed;
    }

    /**
     * Greedy equivalence search: Start from the empty graph, add edges till
     * model is significant. Then start deleting edges till a minimum is
     * achieved.
     *
     * @return the resulting CPDAG.
     */
//    public Graph search() {
//        topGraphs.clear();
//
//        lookupArrows = new ConcurrentHashMap<>();
//        final List<Node> nodes = new ArrayList<>(variables);
//        graph = new EdgeListGraph(nodes);
//
//        if (adjacencies != null) {
//            adjacencies = GraphUtils.replaceNodes(adjacencies, nodes);
//        }
//
//        if (externalGraph != null) {
//            graph = new EdgeListGraph(externalGraph);
//            graph = GraphUtils.replaceNodes(graph, nodes);
//        }
//
//        addRequiredEdges(graph);
//
//        if (faithfulnessAssumed) {
//            initializeForwardEdgesFromEmptyGraph(getVariable());
//
//            // Do forward search.
//            this.mode = Mode.heuristicSpeedup;
//            fes();
//            bes();
//
//            this.mode = Mode.coverNoncolliders;
//            initializeTwoStepEdges(getVariable());
//            fes();
//            bes();
//        } else {
//            initializeForwardEdgesFromEmptyGraph(getVariable());
//
//            // Do forward search.
//            this.mode = Mode.heuristicSpeedup;
//            fes();
//            bes();
//
//            this.mode = Mode.allowUnfaithfulness;
//            initializeForwardEdgesFromExistingGraph(getVariable());
//            fes();
//            bes();
//        }
//
//        long start = System.currentTimeMillis();
//        totalScore = 0.0;
//
//        long endTime = System.currentTimeMillis();
//        this.elapsedTime = endTime - start;
//        this.logger.log("graph", "\nReturning this graph: " + graph);
//
//        this.logger.log("info", "Elapsed time = " + (elapsedTime) / 1000. + " s");
//        this.logger.flush();
//
//        this.modelScore = totalScore;
//
//        return graph;
//    }
    public Graph search(final Node target) {
        return search(Collections.singletonList(target));
    }

    public Graph search(final List<Node> targets) {

        // Assumes one-edge faithfulness.
        final long start = System.currentTimeMillis();
        this.modelScore = 0.0;

        if (targets == null) {
            throw new NullPointerException();
        }

        for (final Node target : targets) {
            if (!this.fgesScore.getVariables().contains(target)) {
                throw new IllegalArgumentException(
                        "Target is not specified."
                );
            }
        }

        this.targets = targets;

        this.topGraphs.clear();

        this.lookupArrows = new ConcurrentHashMap<>();
        final List<Node> nodes = new ArrayList<>(this.fgesScore.getVariables());

        if (this.adjacencies != null) {
            this.adjacencies = GraphUtils.replaceNodes(this.adjacencies, nodes);
        }

        this.graph = new EdgeListGraph(getVariables());

        this.mode = Mode.heuristicSpeedup;

        calcDConnections(targets);

        // Do forward search.
        fes();
        bes();

        this.mode = Mode.coverNoncolliders;
        initializeTwoStepEdges(getVariables());
        fes();
        bes();

        final long endTime = System.currentTimeMillis();
        this.elapsedTime = endTime - start;
        this.logger.log("graph", "\nReturning this graph: " + this.graph);

        this.logger.log("info", "Elapsed time = " + (this.elapsedTime) / 1000. + " s");
        this.logger.flush();

        final Set<Node> mb = new HashSet<>();
        mb.addAll(targets);

        for (final Node target : targets) {
            mb.addAll(this.graph.getAdjacentNodes(target));

            for (final Node child : this.graph.getChildren(target)) {
                mb.addAll(this.graph.getParents(child));
            }
        }

        final Graph mbgraph = this.graph.subgraph(new ArrayList<>(mb));

        storeGraph(mbgraph);

        return mbgraph;
    }

    private void calcDConnections(final List<Node> targets) {
        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();
        final List<Node> nodes = this.fgesScore.getVariables();

        this.effectEdgesGraph = new EdgeListGraph();

        for (final Node target : targets) {
            this.effectEdgesGraph.addNode(target);
        }

        final Set emptySet = new HashSet();

        for (final Node target : targets) {
            for (final Node x : this.fgesScore.getVariables()) {
                if (targets.contains(x)) {
                    continue;
                }

                final int child = this.hashIndices.get(target);
                final int parent = this.hashIndices.get(x);
                final double bump = this.fgesScore.localScoreDiff(parent, child);

                if (bump > 0) {
                    synchronized (this.effectEdgesGraph) {
                        this.effectEdgesGraph.addNode(x);
                    }

                    addUnconditionalArrows(x, target, emptySet);

                    class MbAboutNodeTask extends RecursiveTask<Boolean> {

                        public MbAboutNodeTask() {
                        }

                        @Override
                        protected Boolean compute() {
                            final Queue<NodeTaskEmptyGraph> tasks = new ArrayDeque<>();

                            for (final Node y : FgesMb.this.fgesScore.getVariables()) {
                                if (Thread.currentThread().isInterrupted()) {
                                    break;
                                }

                                if (x == y) {
                                    continue;
                                }

                                final MbTask mbTask = new MbTask(x, y, target);
                                mbTask.fork();

                                for (final NodeTaskEmptyGraph _task : new ArrayList<>(tasks)) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        break;
                                    }

                                    if (_task.isDone()) {
                                        _task.join();
                                        tasks.remove(_task);
                                    }
                                }

                                while (tasks.size() > FgesMb.this.maxThreads) {
                                    if (Thread.currentThread().isInterrupted()) {
                                        break;
                                    }

                                    final NodeTaskEmptyGraph _task = tasks.poll();
                                    _task.join();
                                }
                            }

                            for (final NodeTaskEmptyGraph task : tasks) {
                                if (Thread.currentThread().isInterrupted()) {
                                    break;
                                }

                                task.join();
                            }

                            return true;
                        }
                    }

                    this.pool.invoke(new MbAboutNodeTask());
                }
            }
        }
    }

    class MbTask extends RecursiveTask<Boolean> {

        Node x;
        Node y;
        Node target;
        Set<Node> emptySet = new HashSet<>();

        public MbTask(final Node x, final Node y, final Node target) {
            this.x = x;
            this.y = y;
            this.target = target;
        }

        @Override
        protected Boolean compute() {
            if (!FgesMb.this.effectEdgesGraph.isAdjacentTo(this.x, this.y) && !FgesMb.this.effectEdgesGraph.isAdjacentTo(this.y, this.target)) {
                final int child2 = FgesMb.this.hashIndices.get(this.x);
                final int parent2 = FgesMb.this.hashIndices.get(this.y);

                final double bump2 = FgesMb.this.fgesScore.localScoreDiff(parent2, child2);

                if (bump2 > 0) {
                    synchronized (FgesMb.this.effectEdgesGraph) {
                        FgesMb.this.effectEdgesGraph.addNode(this.y);
                    }

                    addUnconditionalArrows(this.x, this.y, this.emptySet);
                }
            }

            return true;
        }
    }

    private void addUnconditionalArrows(final Node x, final Node y, final Set emptySet) {
        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                return;
            }

            if (!validSetByKnowledge(y, emptySet)) {
                return;
            }
        }

        if (this.adjacencies != null && !this.adjacencies.isAdjacentTo(x, y)) {
            return;
        }

        final int child = this.hashIndices.get(y);
        final int parent = this.hashIndices.get(x);
        final double bump = this.fgesScore.localScoreDiff(parent, child);

        if (this.boundGraph != null && !this.boundGraph.isAdjacentTo(x, y)) {
            return;
        }

        final Edge edge = Edges.undirectedEdge(x, y);
        this.effectEdgesGraph.addEdge(edge);

        if (bump > 0.0) {
            addArrow(x, y, emptySet, emptySet, bump);
            addArrow(y, x, emptySet, emptySet, bump);
        }
    }

    /**
     * @return the background knowledge.
     */
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required
     *                  edges.
     */
    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }
        this.knowledge = knowledge;
    }

    public long getElapsedTime() {
        return this.elapsedTime;
    }

    /**
     * If the true graph is set, askterisks will be printed in log output for
     * the true edges.
     */
    public void setTrueGraph(final Graph trueGraph) {
        this.trueGraph = trueGraph;
    }

    /**
     * @return the totalScore of the given DAG, up to a constant.
     */
    public double getScore(final Graph dag) {
        return scoreDag(dag);
    }

    /**
     * @return the list of top scoring graphs.
     */
    public LinkedList<ScoredGraph> getTopGraphs() {
        return this.topGraphs;
    }

    /**
     * @return the number of CPDAGs to store.
     */
    public int getnumCPDAGsToStore() {
        return this.numCPDAGsToStore;
    }

    /**
     * Sets the number of CPDAGs to store. This should be set to zero for fast
     * search.
     */
    public void setNumCPDAGsToStore(final int numCPDAGsToStore) {
        if (numCPDAGsToStore < 0) {
            throw new IllegalArgumentException("# graphs to store must at least 0: " + numCPDAGsToStore);
        }

        this.numCPDAGsToStore = numCPDAGsToStore;
    }

    /**
     * @return the initial graph for the search. The search is initialized to
     * this graph and proceeds from there.
     */
    public Graph getexternalGraph() {
        return this.externalGraph;
    }

    /**
     * Sets the initial graph.
     */
    public void setExternalGraph(Graph externalGraph) {
        if (externalGraph != null) {
            externalGraph = GraphUtils.replaceNodes(externalGraph, this.variables);

            if (this.verbose) {
                this.out.println("Initial graph variables: " + externalGraph.getNodes());
                this.out.println("Data set variables: " + this.variables);
            }

            if (!new HashSet<>(externalGraph.getNodes()).equals(new HashSet<>(this.variables))) {
                throw new IllegalArgumentException("Variables aren't the same.");
            }
        }

        this.externalGraph = externalGraph;
    }

    /**
     * Sets whether verbose output should be produced.
     */
    public void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent
     * to. By detault System.out.
     */
    public void setOut(final PrintStream out) {
        this.out = out;
    }

    /**
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    /**
     * @return the set of preset adjacenies for the algorithm; edges not in this
     * adjacencies graph will not be added.
     */
    public Graph getAdjacencies() {
        return this.adjacencies;
    }

    /**
     * Sets the set of preset adjacenies for the algorithm; edges not in this
     * adjacencies graph will not be added.
     */
    public void setAdjacencies(final Graph adjacencies) {
        this.adjacencies = adjacencies;
    }

    /**
     * A bound on cycle length.
     */
    public int getCycleBound() {
        return this.cycleBound;
    }

    /**
     * A bound on cycle length.
     *
     * @param cycleBound The bound, >= 1, or -1 for unlimited.
     */
    public void setCycleBound(final int cycleBound) {
        if (!(cycleBound == -1 || cycleBound >= 1)) {
            throw new IllegalArgumentException("Cycle bound needs to be -1 or >= 1: " + cycleBound);
        }
        this.cycleBound = cycleBound;
    }

    /**
     * Creates a new processors pool with the specified number of threads.
     */
    public void setParallelism(final int numProcessors) {
        this.pool = new ForkJoinPool(numProcessors);
    }

    /**
     * If non-null, edges not adjacent in this graph will not be added.
     */
    public void setBoundGraph(final Graph boundGraph) {
        this.boundGraph = GraphUtils.replaceNodes(boundGraph, getVariables());
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
     *
     * @deprecated Use the getters on the individual scores instead.
     */
    public double getPenaltyDiscount() {
        if (this.fgesScore instanceof ISemBicScore) {
            return ((ISemBicScore) this.fgesScore).getPenaltyDiscount();
        } else {
            return 2.0;
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setSamplePrior(final double samplePrior) {
        if (this.fgesScore instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) this.fgesScore).setSamplePrior(samplePrior);
        }
    }

    /**
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setStructurePrior(final double expectedNumParents) {
        if (this.fgesScore instanceof LocalDiscreteScore) {
            ((LocalDiscreteScore) this.fgesScore).setStructurePrior(expectedNumParents);
        }
    }

    /**
     * For BIC totalScore, a multiplier on the penalty term. For continuous
     * searches.
     *
     * @deprecated Use the setters on the individual scores instead.
     */
    public void setPenaltyDiscount(final double penaltyDiscount) {
        if (this.fgesScore instanceof ISemBicScore) {
            ((ISemBicScore) this.fgesScore).setPenaltyDiscount(penaltyDiscount);
        }
    }

    /**
     * The maximum of parents any nodes can have in output CPDAG.
     *
     * @return -1 for unlimited.
     */
    public int getMaxDegree() {
        return this.maxDegree;
    }

    /**
     * The maximum of parents any nodes can have in output CPDAG.
     *
     * @param maxDegree -1 for unlimited.
     */
    public void setMaxDegree(final int maxDegree) {
        if (maxDegree < -1) {
            throw new IllegalArgumentException();
        }
        this.maxDegree = maxDegree;
    }

    //===========================PRIVATE METHODS========================//
    //Sets the discrete scoring function to use.
    private void setFgesScore(final Score totalScore) {
        this.fgesScore = totalScore;

        this.variables = new ArrayList<>();

        for (final Node node : totalScore.getVariables()) {
            if (node.getNodeType() == NodeType.MEASURED) {
                this.variables.add(node);
            }
        }

        buildIndexing(totalScore.getVariables());
    }

    final int[] count = new int[1];

    public int getMinChunk(final int n) {
        return Math.max(n / this.maxThreads, this.minChunk);
    }

    class NodeTaskEmptyGraph extends RecursiveTask<Boolean> {

        private final int from;
        private final int to;
        private final List<Node> nodes;
        private final Set<Node> emptySet;

        public NodeTaskEmptyGraph(final int from, final int to, final List<Node> nodes, final Set<Node> emptySet) {
            this.from = from;
            this.to = to;
            this.nodes = nodes;
            this.emptySet = emptySet;
        }

        @Override
        protected Boolean compute() {
            for (int i = this.from; i < this.to; i++) {
                if ((i + 1) % 1000 == 0) {
                    FgesMb.this.count[0] += 1000;
                    FgesMb.this.out.println("Initializing effect edges: " + (FgesMb.this.count[0]));
                }

                final Node y = this.nodes.get(i);
                FgesMb.this.neighbors.put(y, this.emptySet);

                for (int j = i + 1; j < this.nodes.size(); j++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    final Node x = this.nodes.get(j);

                    if (existsKnowledge()) {
                        if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                            continue;
                        }

                        if (!validSetByKnowledge(y, this.emptySet)) {
                            continue;
                        }
                    }

                    if (FgesMb.this.adjacencies != null && !FgesMb.this.adjacencies.isAdjacentTo(x, y)) {
                        continue;
                    }

                    final int child = FgesMb.this.hashIndices.get(y);
                    final int parent = FgesMb.this.hashIndices.get(x);
                    final double bump = FgesMb.this.fgesScore.localScoreDiff(parent, child);

                    if (FgesMb.this.boundGraph != null && !FgesMb.this.boundGraph.isAdjacentTo(x, y)) {
                        continue;
                    }

                    if (bump > 0) {
                        final Edge edge = Edges.undirectedEdge(x, y);
                        FgesMb.this.effectEdgesGraph.addEdge(edge);
                    }

                    if (bump > 0.0) {
                        addArrow(x, y, this.emptySet, this.emptySet, bump);
                        addArrow(y, x, this.emptySet, this.emptySet, bump);
                    }
                }
            }

            return true;
        }
    }

    private void initializeForwardEdgesFromEmptyGraph(final List<Node> nodes) {
        if (this.verbose) {
            System.out.println("heuristicSpeedup = true");
        }

        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();
        final Set<Node> emptySet = new HashSet<>();

        final long start = System.currentTimeMillis();
        this.effectEdgesGraph = new EdgeListGraph(nodes);

        class InitializeFromEmptyGraphTask extends RecursiveTask<Boolean> {

            public InitializeFromEmptyGraphTask() {
            }

            @Override
            protected Boolean compute() {
                final Queue<NodeTaskEmptyGraph> tasks = new ArrayDeque<>();

                final int numNodesPerTask = Math.max(100, nodes.size() / FgesMb.this.maxThreads);

                for (int i = 0; i < nodes.size(); i += numNodesPerTask) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    final NodeTaskEmptyGraph task = new NodeTaskEmptyGraph(i, Math.min(nodes.size(), i + numNodesPerTask),
                            nodes, emptySet);
                    tasks.add(task);
                    task.fork();

                    for (final NodeTaskEmptyGraph _task : new ArrayList<>(tasks)) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if (_task.isDone()) {
                            _task.join();
                            tasks.remove(_task);
                        }
                    }

                    while (tasks.size() > FgesMb.this.maxThreads) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        final NodeTaskEmptyGraph _task = tasks.poll();
                        _task.join();
                    }
                }

                for (final NodeTaskEmptyGraph task : tasks) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    task.join();
                }

                return true;
            }
        }

        this.pool.invoke(new InitializeFromEmptyGraphTask());

        final long stop = System.currentTimeMillis();

        if (this.verbose) {
            this.out.println("Elapsed initializeForwardEdgesFromEmptyGraph = " + (stop - start) + " ms");
        }
    }

    private void initializeTwoStepEdges(final List<Node> nodes) {
        if (this.verbose) {
            System.out.println("heuristicSpeedup = false");
        }

        this.count[0] = 0;

        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();

        if (this.effectEdgesGraph == null) {
            this.effectEdgesGraph = new EdgeListGraph(nodes);
        }

        if (this.externalGraph != null) {
            for (final Edge edge : this.externalGraph.getEdges()) {
                if (!this.effectEdgesGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    this.effectEdgesGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
                }
            }
        }

        final Set<Node> emptySet = new HashSet<>(0);

        class InitializeFromExistingGraphTask extends RecursiveTask<Boolean> {

            private final int chunk;
            private final int from;
            private final int to;

            public InitializeFromExistingGraphTask(final int chunk, final int from, final int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (TaskManager.getInstance().isCanceled()) {
                    return false;
                }

                if (this.to - this.from <= this.chunk) {
                    for (int i = this.from; i < this.to; i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if ((i + 1) % 1000 == 0) {
                            FgesMb.this.count[0] += 1000;
                            FgesMb.this.out.println("Initializing effect edges: " + (FgesMb.this.count[0]));
                        }

                        final Node y = nodes.get(i);

                        final Set<Node> g = new HashSet<>();

                        for (final Node n : FgesMb.this.graph.getAdjacentNodes(y)) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            for (final Node m : FgesMb.this.graph.getAdjacentNodes(n)) {
                                if (Thread.currentThread().isInterrupted()) {
                                    break;
                                }

                                if (FgesMb.this.graph.isAdjacentTo(y, m)) {
                                    continue;
                                }

                                if (FgesMb.this.graph.isDefCollider(m, n, y)) {
                                    continue;
                                }

                                g.add(m);
                            }
                        }

                        for (final Node x : g) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            if (existsKnowledge()) {
                                if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                                    continue;
                                }

                                if (!validSetByKnowledge(y, emptySet)) {
                                    continue;
                                }
                            }

                            if (FgesMb.this.adjacencies != null && !FgesMb.this.adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            if (FgesMb.this.removedEdges.contains(Edges.undirectedEdge(x, y))) {
                                continue;
                            }

                            calculateArrowsForward(x, y);
                        }
                    }

                    return true;
                } else {
                    final int mid = (this.to + this.from) / 2;

                    final InitializeFromExistingGraphTask left = new InitializeFromExistingGraphTask(this.chunk, this.from, mid);
                    final InitializeFromExistingGraphTask right = new InitializeFromExistingGraphTask(this.chunk, mid, this.to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        this.pool.invoke(new InitializeFromExistingGraphTask(getMinChunk(nodes.size()), 0, nodes.size()));
    }

    private void initializeForwardEdgesFromExistingGraph(final List<Node> nodes) {
        if (this.verbose) {
            System.out.println("heuristicSpeedup = false");
        }

        this.count[0] = 0;

        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();

        if (this.effectEdgesGraph == null) {
            this.effectEdgesGraph = new EdgeListGraph(nodes);
        }

        if (this.externalGraph != null) {
            for (final Edge edge : this.externalGraph.getEdges()) {
                if (!this.effectEdgesGraph.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                    this.effectEdgesGraph.addUndirectedEdge(edge.getNode1(), edge.getNode2());
                }
            }
        }

        final Set<Node> emptySet = new HashSet<>(0);

        class InitializeFromExistingGraphTask extends RecursiveTask<Boolean> {

            private final int chunk;
            private final int from;
            private final int to;

            public InitializeFromExistingGraphTask(final int chunk, final int from, final int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (TaskManager.getInstance().isCanceled()) {
                    return false;
                }

                if (this.to - this.from <= this.chunk) {
                    for (int i = this.from; i < this.to; i++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        if ((i + 1) % 1000 == 0) {
                            FgesMb.this.count[0] += 1000;
                            FgesMb.this.out.println("Initializing effect edges: " + (FgesMb.this.count[0]));
                        }

                        final Node y = nodes.get(i);
                        final Set<Node> D = new HashSet<>();
                        final List<Node> cond = new ArrayList<>();
                        D.addAll(GraphUtils.getDconnectedVars(y, cond, FgesMb.this.graph));
                        D.remove(y);
                        D.removeAll(FgesMb.this.effectEdgesGraph.getAdjacentNodes(y));

                        for (final Node x : D) {
                            if (Thread.currentThread().isInterrupted()) {
                                break;
                            }

                            if (existsKnowledge()) {
                                if (getKnowledge().isForbidden(x.getName(), y.getName()) && getKnowledge().isForbidden(y.getName(), x.getName())) {
                                    continue;
                                }

                                if (!validSetByKnowledge(y, emptySet)) {
                                    continue;
                                }
                            }

                            if (FgesMb.this.adjacencies != null && !FgesMb.this.adjacencies.isAdjacentTo(x, y)) {
                                continue;
                            }

                            calculateArrowsForward(x, y);
                        }
                    }

                    return true;
                } else {
                    final int mid = (this.to + this.from) / 2;

                    final InitializeFromExistingGraphTask left = new InitializeFromExistingGraphTask(this.chunk, this.from, mid);
                    final InitializeFromExistingGraphTask right = new InitializeFromExistingGraphTask(this.chunk, mid, this.to);

                    left.fork();
                    right.compute();
                    left.join();

                    return true;
                }
            }
        }

        this.pool.invoke(new InitializeFromExistingGraphTask(getMinChunk(nodes.size()), 0, nodes.size()));
    }

    private void fes() {
        TetradLogger.getInstance().log("info", "** FORWARD EQUIVALENCE SEARCH");

        final int maxDeg = this.maxDegree == -1 ? 1000 : this.maxDegree;

        while (!this.sortedArrows.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Arrow arrow = this.sortedArrows.first();
            this.sortedArrows.remove(arrow);

            final Node x = arrow.getA();
            final Node y = arrow.getB();

            if (this.graph.isAdjacentTo(x, y)) {
                continue;
            }

            if (this.graph.getDegree(x) > maxDeg - 1) {
                continue;
            }
            if (this.graph.getDegree(y) > maxDeg - 1) {
                continue;
            }

            if (!arrow.getNaYX().equals(getNaYX(x, y))) {
                continue;
            }

            if (!getTNeighbors(x, y).containsAll(arrow.getHOrT())) {
                continue;
            }

            if (!validInsert(x, y, arrow.getHOrT(), getNaYX(x, y))) {
                continue;
            }

            final Set<Node> T = arrow.getHOrT();
            final double bump = arrow.getBump();

            final boolean inserted = insert(x, y, T, bump);
            if (!inserted) {
                continue;
            }

            this.totalScore += bump;

            final Set<Node> visited = reapplyOrientation(x, y, null);
            final Set<Node> toProcess = new HashSet<>();

            for (final Node node : visited) {
                final Set<Node> neighbors1 = getNeighbors(node);
                final Set<Node> storedNeighbors = this.neighbors.get(node);

                if (!(neighbors1.equals(storedNeighbors))) {
                    toProcess.add(node);
                }
            }

            toProcess.add(x);
            toProcess.add(y);

            storeGraph(this.graph);
            reevaluateForward(toProcess, arrow);
        }
    }

    private void bes() {
        TetradLogger.getInstance().log("info", "** BACKWARD EQUIVALENCE SEARCH");

        this.sortedArrows = new ConcurrentSkipListSet<>();
        this.lookupArrows = new ConcurrentHashMap<>();
        this.neighbors = new ConcurrentHashMap<>();

        initializeArrowsBackward();

        while (!this.sortedArrows.isEmpty()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Arrow arrow = this.sortedArrows.first();
            this.sortedArrows.remove(arrow);

            final Node x = arrow.getA();
            final Node y = arrow.getB();

            if (!arrow.getNaYX().equals(getNaYX(x, y))) {
                continue;
            }

            if (!this.graph.isAdjacentTo(x, y)) {
                continue;
            }

            final Edge edge = this.graph.getEdge(x, y);
            if (edge.pointsTowards(x)) {
                continue;
            }

            final HashSet<Node> diff = new HashSet<>(arrow.getNaYX());
            diff.removeAll(arrow.getHOrT());

            if (!validDelete(x, y, arrow.getHOrT(), arrow.getNaYX())) {
                continue;
            }

            final Set<Node> H = arrow.getHOrT();
            final double bump = arrow.getBump();

            final boolean deleted = delete(x, y, H, bump, arrow.getNaYX());
            if (!deleted) {
                continue;
            }

            this.totalScore += bump;

            clearArrow(x, y);

            final Set<Node> visited = reapplyOrientation(x, y, H);

            final Set<Node> toProcess = new HashSet<>();

            for (final Node node : visited) {
                final Set<Node> neighbors1 = getNeighbors(node);
                final Set<Node> storedNeighbors = this.neighbors.get(node);

                if (!(neighbors1.equals(storedNeighbors))) {
                    toProcess.add(node);
                }
            }

            toProcess.add(x);
            toProcess.add(y);
            toProcess.addAll(getCommonAdjacents(x, y));

            storeGraph(this.graph);
            reevaluateBackward(toProcess);
        }

        meekOrientRestricted(getVariables(), getKnowledge());
    }

    private Set<Node> getCommonAdjacents(final Node x, final Node y) {
        final Set<Node> commonChildren = new HashSet<>(this.graph.getAdjacentNodes(x));
        commonChildren.retainAll(this.graph.getAdjacentNodes(y));
        return commonChildren;
    }

    private Set<Node> reapplyOrientation(final Node x, final Node y, final Set<Node> newArrows) {
        final Set<Node> toProcess = new HashSet<>();
        toProcess.add(x);
        toProcess.add(y);

        if (newArrows != null) {
            toProcess.addAll(newArrows);
        }

        return meekOrientRestricted(new ArrayList<>(toProcess), getKnowledge());
    }

    // Returns true if knowledge is not empty.
    private boolean existsKnowledge() {
        return !this.knowledge.isEmpty();
    }

    // Initiaizes the sorted arrows lists for the backward search.
    private void initializeArrowsBackward() {
        for (final Edge edge : this.graph.getEdges()) {
            final Node x = edge.getNode1();
            final Node y = edge.getNode2();

            if (existsKnowledge()) {
                if (!getKnowledge().noEdgeRequired(x.getName(), y.getName())) {
                    continue;
                }
            }

            clearArrow(x, y);
            clearArrow(y, x);

            if (edge.pointsTowards(y)) {
                calculateArrowsBackward(x, y);
            } else if (edge.pointsTowards(x)) {
                calculateArrowsBackward(y, x);
            } else {
                calculateArrowsBackward(x, y);
                calculateArrowsBackward(y, x);
            }

            this.neighbors.put(x, getNeighbors(x));
            this.neighbors.put(y, getNeighbors(y));
        }
    }

    // Calcuates new arrows based on changes in the graph for the forward search.
    private void reevaluateForward(final Set<Node> nodes, final Arrow arrow) {
        class AdjTask extends RecursiveTask<Boolean> {

            private final List<Node> nodes;
            private final int from;
            private final int to;
            private final int chunk;

            public AdjTask(final int chunk, final List<Node> nodes, final int from, final int to) {
                this.nodes = nodes;
                this.from = from;
                this.to = to;
                this.chunk = chunk;
            }

            @Override
            protected Boolean compute() {
                if (this.to - this.from <= this.chunk) {
                    for (int _w = this.from; _w < this.to; _w++) {
                        final Node x = this.nodes.get(_w);

                        final List<Node> adj;

                        if (FgesMb.this.mode == Mode.heuristicSpeedup) {
                            adj = FgesMb.this.effectEdgesGraph.getAdjacentNodes(x);
                        } else if (FgesMb.this.mode == Mode.coverNoncolliders) {
                            final Set<Node> g = new HashSet<>();

                            for (final Node n : FgesMb.this.graph.getAdjacentNodes(x)) {
                                for (final Node m : FgesMb.this.graph.getAdjacentNodes(n)) {
                                    if (FgesMb.this.graph.isAdjacentTo(x, m)) {
                                        continue;
                                    }

                                    if (FgesMb.this.graph.isDefCollider(m, n, x)) {
                                        continue;
                                    }

                                    g.add(m);
                                }
                            }

                            adj = new ArrayList<>(g);
                        } else if (FgesMb.this.mode == Mode.allowUnfaithfulness) {
                            final HashSet<Node> D = new HashSet<>();
                            D.addAll(GraphUtils.getDconnectedVars(x, new ArrayList<Node>(), FgesMb.this.graph));
                            D.remove(x);
                            adj = new ArrayList<>(D);
                        } else {
                            throw new IllegalStateException();
                        }

                        for (final Node w : adj) {
                            if (FgesMb.this.adjacencies != null && !(FgesMb.this.adjacencies.isAdjacentTo(w, x))) {
                                continue;
                            }

                            if (w == x) {
                                continue;
                            }

                            if (!FgesMb.this.graph.isAdjacentTo(w, x)) {
                                clearArrow(w, x);
                                calculateArrowsForward(w, x);
                            }
                        }
                    }

                    return true;
                } else {
                    final int mid = (this.to - this.from) / 2;

                    final List<AdjTask> tasks = new ArrayList<>();

                    tasks.add(new AdjTask(this.chunk, this.nodes, this.from, this.from + mid));
                    tasks.add(new AdjTask(this.chunk, this.nodes, this.from + mid, this.to));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        final AdjTask task = new AdjTask(getMinChunk(nodes.size()), new ArrayList<>(nodes), 0, nodes.size());
        this.pool.invoke(task);
    }

    // Calculates the new arrows for an a->b edge.
    private void calculateArrowsForward(final Node a, final Node b) {
        if (this.mode == Mode.heuristicSpeedup && !this.effectEdgesGraph.isAdjacentTo(a, b)) {
            return;
        }
        if (this.adjacencies != null && !this.adjacencies.isAdjacentTo(a, b)) {
            return;
        }
        this.neighbors.put(b, getNeighbors(b));

        if (existsKnowledge()) {
            if (getKnowledge().isForbidden(a.getName(), b.getName())) {
                return;
            }
        }

        final Set<Node> naYX = getNaYX(a, b);
        if (!GraphUtils.isClique(naYX, this.graph)) {
            return;
        }

        final List<Node> TNeighbors = getTNeighbors(a, b);
        final int _maxDegree = this.maxDegree == -1 ? 1000 : this.maxDegree;

        final int _max = Math.min(TNeighbors.size(), _maxDegree - this.graph.getIndegree(b));

        Set<Set<Node>> previousCliques = new HashSet<>();
        previousCliques.add(new HashSet<Node>());
        Set<Set<Node>> newCliques = new HashSet<>();

        FOR:
        for (int i = 0; i <= _max; i++) {
            final ChoiceGenerator gen = new ChoiceGenerator(TNeighbors.size(), i);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final Set<Node> T = GraphUtils.asSet(choice, TNeighbors);

                final Set<Node> union = new HashSet<>(naYX);
                union.addAll(T);

                boolean foundAPreviousClique = false;

                for (final Set<Node> clique : previousCliques) {
                    if (union.containsAll(clique)) {
                        foundAPreviousClique = true;
                        break;
                    }
                }

                if (!foundAPreviousClique) {
                    break FOR;
                }

                if (!GraphUtils.isClique(union, this.graph)) {
                    continue;
                }
                newCliques.add(union);

                final double bump = insertEval(a, b, T, naYX, this.hashIndices);

                if (bump > 0.0) {
                    addArrow(a, b, naYX, T, bump);
                }

//                if (mode == Mode.heuristicSpeedup && union.isEmpty() && score.isEffectEdge(bump) &&
//                        !effectEdgesGraph.isAdjacentTo(a, b) && graph.getParents(b).isEmpty()) {
//                    effectEdgesGraph.addUndirectedEdge(a, b);
//                }
            }

            previousCliques = newCliques;
            newCliques = new HashSet<>();
        }
    }

    private void addArrow(final Node a, final Node b, final Set<Node> naYX, final Set<Node> hOrT, final double bump) {
        final Arrow arrow = new Arrow(bump, a, b, hOrT, naYX, this.arrowIndex++);
        this.sortedArrows.add(arrow);
        addLookupArrow(a, b, arrow);
    }

    // Reevaluates arrows after removing an edge from the graph.
    private void reevaluateBackward(final Set<Node> toProcess) {
        class BackwardTask extends RecursiveTask<Boolean> {

            private final Node r;
            private final List<Node> adj;
            private final Map<Node, Integer> hashIndices;
            private final int chunk;
            private final int from;
            private final int to;

            public BackwardTask(final Node r, final List<Node> adj, final int chunk, final int from, final int to,
                                final Map<Node, Integer> hashIndices) {
                this.adj = adj;
                this.hashIndices = hashIndices;
                this.chunk = chunk;
                this.from = from;
                this.to = to;
                this.r = r;
            }

            @Override
            protected Boolean compute() {
                if (this.to - this.from <= this.chunk) {
                    for (int _w = this.from; _w < this.to; _w++) {
                        if (Thread.currentThread().isInterrupted()) {
                            break;
                        }

                        final Node w = this.adj.get(_w);
                        final Edge e = FgesMb.this.graph.getEdge(w, this.r);

                        if (e != null) {
                            if (e.pointsTowards(this.r)) {
                                clearArrow(w, this.r);
                                clearArrow(this.r, w);

                                calculateArrowsBackward(w, this.r);
                            } else if (Edges.isUndirectedEdge(FgesMb.this.graph.getEdge(w, this.r))) {
                                clearArrow(w, this.r);
                                clearArrow(this.r, w);

                                calculateArrowsBackward(w, this.r);
                                calculateArrowsBackward(this.r, w);
                            }
                        }
                    }

                    return true;
                } else {
                    final int mid = (this.to - this.from) / 2;

                    final List<BackwardTask> tasks = new ArrayList<>();

                    tasks.add(new BackwardTask(this.r, this.adj, this.chunk, this.from, this.from + mid, this.hashIndices));
                    tasks.add(new BackwardTask(this.r, this.adj, this.chunk, this.from + mid, this.to, this.hashIndices));

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        for (final Node r : toProcess) {
            this.neighbors.put(r, getNeighbors(r));
            final List<Node> adjacentNodes = this.graph.getAdjacentNodes(r);
            this.pool.invoke(new BackwardTask(r, adjacentNodes, getMinChunk(adjacentNodes.size()), 0,
                    adjacentNodes.size(), this.hashIndices));
        }
    }

    // Calculates the arrows for the removal in the backward direction.
    private void calculateArrowsBackward(final Node a, final Node b) {
        if (existsKnowledge()) {
            if (!getKnowledge().noEdgeRequired(a.getName(), b.getName())) {
                return;
            }
        }

        final Set<Node> naYX = getNaYX(a, b);

        final List<Node> _naYX = new ArrayList<>(naYX);

        final int _depth = _naYX.size();

        for (int i = 0; i <= _depth; i++) {
            final ChoiceGenerator gen = new ChoiceGenerator(_naYX.size(), i);
            int[] choice;

            while ((choice = gen.next()) != null) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final Set<Node> diff = GraphUtils.asSet(choice, _naYX);

                final Set<Node> h = new HashSet<>(_naYX);
                h.removeAll(diff);

                if (existsKnowledge()) {
                    if (!validSetByKnowledge(b, h)) {
                        continue;
                    }
                }

                final double bump = deleteEval(a, b, diff, naYX, this.hashIndices);

                if (bump > 0.0) {
                    addArrow(a, b, naYX, h, bump);
                }
            }
        }
    }

    public double getModelScore() {
        return this.modelScore;
    }

    // Basic data structure for an arrow a->b considered for additiom or removal from the graph, together with
    // associated sets needed to make this determination. For both forward and backward direction, NaYX is needed.
    // For the forward direction, T neighbors are needed; for the backward direction, H neighbors are needed.
    // See Chickering (2002). The totalScore difference resulting from added in the edge (hypothetically) is recorded
    // as the "bump".
    private static class Arrow implements Comparable<Arrow> {

        private final double bump;
        private final Node a;
        private final Node b;
        private final Set<Node> hOrT;
        private final Set<Node> naYX;
        private int index = 0;

        public Arrow(final double bump, final Node a, final Node b, final Set<Node> hOrT, final Set<Node> naYX, final int index) {
            this.bump = bump;
            this.a = a;
            this.b = b;
            this.hOrT = hOrT;
            this.naYX = naYX;
            this.index = index;
        }

        public double getBump() {
            return this.bump;
        }

        public Node getA() {
            return this.a;
        }

        public Node getB() {
            return this.b;
        }

        public Set<Node> getHOrT() {
            return this.hOrT;
        }

        public Set<Node> getNaYX() {
            return this.naYX;
        }

        // Sorting by bump, high to low. The problem is the SortedSet contains won't add a new element if it compares
        // to zero with an existing element, so for the cases where the comparison is to zero (i.e. have the same
        // bump, we need to determine as quickly as possible a determinate ordering (fixed) ordering for two variables.
        // The fastest way to do this is using a hash code, though it's still possible for two Arrows to have the
        // same hash code but not be equal. If we're paranoid, in this case we calculate a determinate comparison
        // not equal to zero by keeping a list. This last part is commened out by default.
        public int compareTo(final Arrow arrow) {
            if (arrow == null) {
                throw new NullPointerException();
            }

            final int compare = Double.compare(arrow.getBump(), getBump());

            if (compare == 0) {
                return Integer.compare(getIndex(), arrow.getIndex());
            }

            return compare;
        }

        public String toString() {
            return "Arrow<" + this.a + "->" + this.b + " bump = " + this.bump + " t/h = " + this.hOrT + " naYX = " + this.naYX + ">";
        }

        public int getIndex() {
            return this.index;
        }
    }

    // Get all adj that are connected to Y by an undirected edge and not adjacent to X.
    private List<Node> getTNeighbors(final Node x, final Node y) {
        final List<Edge> yEdges = this.graph.getEdges(y);
        final List<Node> tNeighbors = new ArrayList<>();

        for (final Edge edge : yEdges) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            final Node z = edge.getDistalNode(y);

            if (this.graph.isAdjacentTo(z, x)) {
                continue;
            }

            tNeighbors.add(z);
        }

        return tNeighbors;
    }

    // Get all adj that are connected to Y.
    private Set<Node> getNeighbors(final Node y) {
        final List<Edge> yEdges = this.graph.getEdges(y);
        final Set<Node> neighbors = new HashSet<>();

        for (final Edge edge : yEdges) {
            if (!Edges.isUndirectedEdge(edge)) {
                continue;
            }

            final Node z = edge.getDistalNode(y);

            neighbors.add(z);
        }

        return neighbors;
    }

    // Evaluate the Insert(X, Y, T) operator (Definition 12 from Chickering, 2002).
    private double insertEval(final Node x, final Node y, final Set<Node> t, final Set<Node> naYX,
                              final Map<Node, Integer> hashIndices) {
        final Set<Node> set = new HashSet<>(naYX);
        set.addAll(t);
        set.addAll(this.graph.getParents(y));
        return scoreGraphChange(y, set, x, hashIndices);
    }

    // Evaluate the Delete(X, Y, T) operator (Definition 12 from Chickering, 2002).
    private double deleteEval(final Node x, final Node y, final Set<Node> diff, final Set<Node> naYX,
                              final Map<Node, Integer> hashIndices) {
        final Set<Node> set = new HashSet<>(diff);
        set.addAll(this.graph.getParents(y));
        set.remove(x);
        return -scoreGraphChange(y, set, x, hashIndices);
    }

    // Do an actual insertion. (Definition 12 from Chickering, 2002).
    private boolean insert(final Node x, final Node y, final Set<Node> T, final double bump) {
        if (this.graph.isAdjacentTo(x, y)) {
            return false; // The initial graph may already have put this edge in the graph.
        }

        Edge trueEdge = null;

        if (this.trueGraph != null) {
            final Node _x = this.trueGraph.getNode(x.getName());
            final Node _y = this.trueGraph.getNode(y.getName());
            trueEdge = this.trueGraph.getEdge(_x, _y);
        }

        if (this.boundGraph != null && !this.boundGraph.isAdjacentTo(x, y)) {
            return false;
        }

        this.graph.addDirectedEdge(x, y);

        if (this.verbose) {
            final String label = this.trueGraph != null && trueEdge != null ? "*" : "";
            TetradLogger.getInstance().log("insertedEdges", this.graph.getNumEdges() + ". INSERT " + this.graph.getEdge(x, y)
                    + " " + T + " " + bump + " " + label);
        }

        final int numEdges = this.graph.getNumEdges();

        if (this.verbose) {
            if (numEdges % 1000 == 0) {
                this.out.println("Num edges added: " + numEdges);
            }
        }

        if (this.verbose) {
            final String label = this.trueGraph != null && trueEdge != null ? "*" : "";
            this.out.println(this.graph.getNumEdges() + ". INSERT " + this.graph.getEdge(x, y)
                    + " " + T + " " + bump + " " + label
                    + " degree = " + GraphUtils.getDegree(this.graph)
                    + " indegree = " + GraphUtils.getIndegree(this.graph));
        }

        for (final Node _t : T) {
            this.graph.removeEdge(_t, y);
            if (this.boundGraph != null && !this.boundGraph.isAdjacentTo(_t, y)) {
                continue;
            }

            this.graph.addDirectedEdge(_t, y);

            if (this.verbose) {
                final String message = "--- Directing " + this.graph.getEdge(_t, y);
                TetradLogger.getInstance().log("directedEdges", message);
                this.out.println(message);
            }
        }

        return true;
    }

    Set<Edge> removedEdges = new HashSet<>();

    // Do an actual deletion (Definition 13 from Chickering, 2002).
    private boolean delete(final Node x, final Node y, final Set<Node> H, final double bump, final Set<Node> naYX) {
        Edge trueEdge = null;

        if (this.trueGraph != null) {
            final Node _x = this.trueGraph.getNode(x.getName());
            final Node _y = this.trueGraph.getNode(y.getName());
            trueEdge = this.trueGraph.getEdge(_x, _y);
        }

        final Edge oldxy = this.graph.getEdge(x, y);

        final Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);

        this.graph.removeEdge(oldxy);
        this.removedEdges.add(Edges.undirectedEdge(x, y));

        if (this.verbose) {
            final int numEdges = this.graph.getNumEdges();
            if (numEdges % 1000 == 0) {
                this.out.println("Num edges (backwards) = " + numEdges);
            }

            if (this.verbose) {
                final String label = this.trueGraph != null && trueEdge != null ? "*" : "";
                final String message = (this.graph.getNumEdges()) + ". DELETE " + x + "-->" + y
                        + " H = " + H + " NaYX = " + naYX + " diff = " + diff + " (" + bump + ") " + label;
                TetradLogger.getInstance().log("deletedEdges", message);
                this.out.println(message);
            }
        }

        for (final Node h : H) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            if (this.graph.isParentOf(h, y) || this.graph.isParentOf(h, x)) {
                continue;
            }

            final Edge oldyh = this.graph.getEdge(y, h);

            this.graph.removeEdge(oldyh);

            this.graph.addEdge(Edges.directedEdge(y, h));

            if (this.verbose) {
                TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldyh + " to "
                        + this.graph.getEdge(y, h));
                this.out.println("--- Directing " + oldyh + " to " + this.graph.getEdge(y, h));
            }

            final Edge oldxh = this.graph.getEdge(x, h);

            if (Edges.isUndirectedEdge(oldxh)) {
                this.graph.removeEdge(oldxh);

                this.graph.addEdge(Edges.directedEdge(x, h));

                if (this.verbose) {
                    TetradLogger.getInstance().log("directedEdges", "--- Directing " + oldxh + " to "
                            + this.graph.getEdge(x, h));
                    this.out.println("--- Directing " + oldxh + " to " + this.graph.getEdge(x, h));
                }
            }
        }

        return true;
    }

    // Test if the candidate insertion is a valid operation
    // (Theorem 15 from Chickering, 2002).
    private boolean validInsert(final Node x, final Node y, final Set<Node> T, final Set<Node> naYX) {
        boolean violatesKnowledge = false;

        if (existsKnowledge()) {
            if (this.knowledge.isForbidden(x.getName(), y.getName())) {
                violatesKnowledge = true;
            }

            for (final Node t : T) {
                if (this.knowledge.isForbidden(t.getName(), y.getName())) {
                    violatesKnowledge = true;
                }
            }
        }

        final Set<Node> union = new HashSet<>(T);
        union.addAll(naYX);
        final boolean clique = GraphUtils.isClique(union, this.graph);
        final boolean noCycle = !existsUnblockedSemiDirectedPath(y, x, union, this.cycleBound);
        return clique && noCycle && !violatesKnowledge;
    }

    private boolean validDelete(final Node x, final Node y, final Set<Node> H, final Set<Node> naYX) {
        boolean violatesKnowledge = false;

        if (existsKnowledge()) {
            for (final Node h : H) {
                if (this.knowledge.isForbidden(x.getName(), h.getName())) {
                    violatesKnowledge = true;
                }

                if (this.knowledge.isForbidden(y.getName(), h.getName())) {
                    violatesKnowledge = true;
                }
            }
        }

        final Set<Node> diff = new HashSet<>(naYX);
        diff.removeAll(H);
        return GraphUtils.isClique(diff, this.graph) && !violatesKnowledge;
    }

    // Adds edges required by knowledge.
    private void addRequiredEdges(final Graph graph) {
        if (!existsKnowledge()) {
            return;
        }

        for (final Iterator<KnowledgeEdge> it = getKnowledge().requiredEdgesIterator(); it.hasNext(); ) {
            final KnowledgeEdge next = it.next();

            final Node nodeA = graph.getNode(next.getFrom());
            final Node nodeB = graph.getNode(next.getTo());

            if (!graph.isAncestorOf(nodeB, nodeA)) {
                graph.removeEdges(nodeA, nodeB);
                graph.addDirectedEdge(nodeA, nodeB);
                TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeA, nodeB));
            }
        }

        for (final Edge edge : graph.getEdges()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final String A = edge.getNode1().getName();
            final String B = edge.getNode2().getName();

            if (this.knowledge.isForbidden(A, B)) {
                final Node nodeA = edge.getNode1();
                final Node nodeB = edge.getNode2();
                if (nodeA == null || nodeB == null) {
                    throw new NullPointerException();
                }

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }

                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }
            } else if (this.knowledge.isForbidden(B, A)) {
                final Node nodeA = edge.getNode2();
                final Node nodeB = edge.getNode1();
                if (nodeA == null || nodeB == null) {
                    throw new NullPointerException();
                }

                if (graph.isAdjacentTo(nodeA, nodeB) && !graph.isChildOf(nodeA, nodeB)) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }
                if (!graph.isChildOf(nodeA, nodeB) && getKnowledge().isForbidden(nodeA.getName(), nodeB.getName())) {
                    if (!graph.isAncestorOf(nodeA, nodeB)) {
                        graph.removeEdges(nodeA, nodeB);
                        graph.addDirectedEdge(nodeB, nodeA);
                        TetradLogger.getInstance().log("insertedEdges", "Adding edge by knowledge: " + graph.getEdge(nodeB, nodeA));
                    }
                }
            }
        }
    }

    // Use background knowledge to decide if an insert or delete operation does not orient edges in a forbidden
    // direction according to prior knowledge. If some orientation is forbidden in the subset, the whole subset is
    // forbidden.
    private boolean validSetByKnowledge(final Node y, final Set<Node> subset) {
        for (final Node node : subset) {
            if (getKnowledge().isForbidden(node.getName(), y.getName())) {
                return false;
            }
        }
        return true;
    }

    // Find all adj that are connected to Y by an undirected edge that are adjacent to X (that is, by undirected or
    // directed edge).
    private Set<Node> getNaYX(final Node x, final Node y) {
        final List<Node> adj = this.graph.getAdjacentNodes(y);
        final Set<Node> nayx = new HashSet<>();

        for (final Node z : adj) {
            if (z == x) {
                continue;
            }
            final Edge yz = this.graph.getEdge(y, z);
            if (!Edges.isUndirectedEdge(yz)) {
                continue;
            }
            if (!this.graph.isAdjacentTo(z, x)) {
                continue;
            }
            nayx.add(z);
        }

        return nayx;
    }

    // Returns true if a path consisting of undirected and directed edges toward 'to' exists of
    // length at most 'bound'. Cycle checker in other words.
    private boolean existsUnblockedSemiDirectedPath(final Node from, final Node to, final Set<Node> cond, final int bound) {
        final Queue<Node> Q = new LinkedList<>();
        final Set<Node> V = new HashSet<>();
        Q.offer(from);
        V.add(from);
        Node e = null;
        int distance = 0;

        while (!Q.isEmpty()) {
            final Node t = Q.remove();
            if (t == to) {
                return true;
            }

            if (e == t) {
                e = null;
                distance++;
                if (distance > (bound == -1 ? 1000 : bound)) {
                    return false;
                }
            }

            for (final Node u : this.graph.getAdjacentNodes(t)) {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                final Edge edge = this.graph.getEdge(t, u);
                final Node c = traverseSemiDirected(t, edge);
                if (c == null) {
                    continue;
                }
                if (cond.contains(c)) {
                    continue;
                }

                if (c == to) {
                    return true;
                }

                if (!V.contains(c)) {
                    V.add(c);
                    Q.offer(c);

                    if (e == null) {
                        e = u;
                    }
                }
            }
        }

        return false;
    }

    // Used to find semidirected paths for cycle checking.
    private static Node traverseSemiDirected(final Node node, final Edge edge) {
        if (node == edge.getNode1()) {
            if (edge.getEndpoint1() == Endpoint.TAIL) {
                return edge.getNode2();
            }
        } else if (node == edge.getNode2()) {
            if (edge.getEndpoint2() == Endpoint.TAIL) {
                return edge.getNode1();
            }
        }
        return null;
    }

    // Runs Meek rules on just the changed adj.
    private Set<Node> reorientNode(final List<Node> nodes) {
        addRequiredEdges(this.graph);
        return meekOrientRestricted(nodes, getKnowledge());
    }

    // Runs Meek rules on just the changed adj.
    private Set<Node> meekOrientRestricted(final List<Node> nodes, final IKnowledge knowledge) {
        final MeekRules rules = new MeekRules();
        rules.setKnowledge(knowledge);
        return rules.orientImplied(this.graph);
    }

    // Maps adj to their indices for quick lookup.
    private void buildIndexing(final List<Node> nodes) {
        this.hashIndices = new ConcurrentHashMap<>();

        int i = -1;

        for (final Node n : nodes) {
            this.hashIndices.put(n, ++i);
        }
    }

    // Removes information associated with an edge x->y.
    private synchronized void clearArrow(final Node x, final Node y) {
        final OrderedPair<Node> pair = new OrderedPair<>(x, y);
        final Set<Arrow> lookupArrows = this.lookupArrows.get(pair);

        if (lookupArrows != null) {
            this.sortedArrows.removeAll(lookupArrows);
        }

        this.lookupArrows.remove(pair);
    }

    // Adds the given arrow for the adjacency i->j. These all are for i->j but may have
    // different T or H or NaYX sets, and so different bumps.
    private void addLookupArrow(final Node i, final Node j, final Arrow arrow) {
        final OrderedPair<Node> pair = new OrderedPair<>(i, j);
        Set<Arrow> arrows = this.lookupArrows.get(pair);

        if (arrows == null) {
            arrows = new ConcurrentSkipListSet<>();
            this.lookupArrows.put(pair, arrows);
        }

        arrows.add(arrow);
    }

    //===========================SCORING METHODS===================//

    /**
     * Scores the given DAG, up to a constant.
     */
    public double scoreDag(final Graph dag) {
        buildIndexing(dag.getNodes());

        double _score = 0.0;

        for (final Node y : dag.getNodes()) {
            if (Thread.currentThread().isInterrupted()) {
                break;
            }

            final Set<Node> parents = new HashSet<>(dag.getParents(y));
            final int[] parentIndices = new int[parents.size()];
            final Iterator<Node> pi = parents.iterator();
            int count = 0;

            while (pi.hasNext()) {
                final Node nextParent = pi.next();
                parentIndices[count++] = this.hashIndices.get(nextParent);
            }

            final int yIndex = this.hashIndices.get(y);
            _score += this.fgesScore.localScore(yIndex, parentIndices);
        }

        return _score;
    }

    private double scoreGraphChange(final Node y, final Set<Node> parents,
                                    final Node x, final Map<Node, Integer> hashIndices) {
        final int yIndex = hashIndices.get(y);

        if (parents.contains(x)) {
            return Double.NaN;//throw new IllegalArgumentException();
        }
        final int[] parentIndices = new int[parents.size()];

        int count = 0;
        for (final Node parent : parents) {
            parentIndices[count++] = hashIndices.get(parent);
        }

        return this.fgesScore.localScoreDiff(hashIndices.get(x), yIndex, parentIndices);
    }

    private List<Node> getVariables() {
        return this.variables;
    }

    // Stores the graph, if its totalScore knocks out one of the top ones.
    private void storeGraph(final Graph graph) {
        if (getnumCPDAGsToStore() > 0) {
            final Graph graphCopy = new EdgeListGraph(graph);
            this.topGraphs.addLast(new ScoredGraph(graphCopy, this.totalScore));
        }

        if (this.topGraphs.size() == getnumCPDAGsToStore() + 1) {
            this.topGraphs.removeFirst();
        }
    }

    public String logEdgeBayesFactorsString(final Graph dag) {
        final Map<Edge, Double> factors = logEdgeBayesFactors(dag);
        return logBayesPosteriorFactorsString(factors, scoreDag(dag));
    }

    public Map<Edge, Double> logEdgeBayesFactors(final Graph dag) {
        final Map<Edge, Double> logBayesFactors = new HashMap<>();
        final double withEdge = scoreDag(dag);

        for (final Edge edge : dag.getEdges()) {
            dag.removeEdge(edge);
            final double withoutEdge = scoreDag(dag);
            final double difference = withEdge - withoutEdge;
            logBayesFactors.put(edge, difference);
            dag.addEdge(edge);
        }

        return logBayesFactors;
    }

    private String logBayesPosteriorFactorsString(final Map<Edge, Double> factors, final double modelScore) {
        final NumberFormat nf = new DecimalFormat("0.00");
        final StringBuilder builder = new StringBuilder();

        final List<Edge> edges = new ArrayList<>(factors.keySet());

        Collections.sort(edges, new Comparator<Edge>() {
            @Override
            public int compare(final Edge o1, final Edge o2) {
                return -Double.compare(factors.get(o1), factors.get(o2));
            }
        });

        builder.append("Edge Posterior Log Bayes Factors:\n\n");

        builder.append("For a DAG in the IMaGES CPDAG with model totalScore m, for each edge e in the "
                + "DAG, the model totalScore that would result from removing each edge, calculating "
                + "the resulting model totalScore m(e), and then reporting m - m(e). The totalScore used is "
                + "the IMScore, L - SUM_i{kc ln n(i)}, L is the maximum likelihood of the model, "
                + "k isthe number of parameters of the model, n(i) is the sample size of the ith "
                + "data set, and c is the penalty penaltyDiscount. Note that the more negative the totalScore, "
                + "the more important the edge is to the posterior probability of the IMaGES model. "
                + "Edges are given in order of their importance so measured.\n\n");

        int i = 0;

        for (final Edge edge : edges) {
            builder.append(++i).append(". ").append(edge).append(" ").append(nf.format(factors.get(edge))).append("\n");
        }

        return builder.toString();
    }
}
