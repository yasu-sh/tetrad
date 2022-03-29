package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static java.lang.Math.floor;
import static java.util.Collections.shuffle;
import static java.util.Collections.sort;


/**
 * Implements a scorer extending Teyssier, M., & Koller, D. (2012). Ordering-based search: A simple and effective
 * algorithm for learning Bayesian networks. arXiv preprint arXiv:1207.1429. You give it a score function
 * and a variable ordering, and it computes the score. You can move any variable left or right, and it will
 * keep track of the score using the Teyssier and Kohler method. You can move a variable to a new position,
 * and you can bookmark a state and come back to it.
 *
 * @author josephramsey
 * @author bryanandrews
 */
public class TeyssierScorer {
    private final List<Node> variables;
    private final Map<Node, Integer> variablesHash;
    private final Score score;
    private final IndependenceTest test;
    private final Map<Integer, LinkedList<Node>> bookmarkedOrders = new HashMap<>();
    private final Map<Integer, LinkedList<Pair>> bookmarkedScores = new HashMap<>();
    private final Map<Integer, Map<Node, Integer>> bookmarkedOrderHashes = new HashMap<>();
    private Map<Node, Map<Set<Node>, Float>> cache = new HashMap<>();
    private Map<Node, Integer> orderHash;
    private LinkedList<Node> pi; // The current permutation.
    private LinkedList<Pair> scores;
    private IKnowledge knowledge = new Knowledge2();
    private LinkedList<Set<Node>> prefixes;

    private boolean useScore = true;
    private boolean useVermaPearl = false;
    private boolean useBackwardScoring = false;
    private boolean cachingScores = true;

    public TeyssierScorer(final IndependenceTest test, final Score score) {
        NodeEqualityMode.setEqualityMode(NodeEqualityMode.Type.OBJECT);

        this.score = score;
        this.test = test;

        if (score != null) {
            this.variables = score.getVariables();
            this.pi = new LinkedList<>(this.variables);
        } else if (test != null) {
            this.variables = test.getVariables();
            this.pi = new LinkedList<>(this.variables);
        } else {
            throw new IllegalArgumentException("Need both a score and a test,");
        }

        this.orderHash = new HashMap<>();
        nodesHash(this.orderHash, this.pi);

        this.variablesHash = new HashMap<>();
        nodesHash(this.variablesHash, this.variables);

        if (score instanceof GraphScore) {
            this.useScore = false;
        }
    }

    /**
     * @param useScore True if the score should be used; false if the test should be used.
     */
    public void setUseScore(final boolean useScore) {
        if (!(this.score instanceof GraphScore)) {
            this.useScore = useScore;
        }
    }

    /**
     * @param cachingScores True if scores should be cached (potentially expensive for memory);
     *                      false if not (potentially expensive for time).
     */
    public void setCachingScores(final boolean cachingScores) {
        this.cachingScores = cachingScores;
    }

    /**
     * @param knowledge Knowledge of forbidden edges.
     */
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    /**
     * @param useVermaPearl True if Pearl's method for building a DAG should be used.
     */
    public void setUseVermaPearl(final boolean useVermaPearl) {
        this.useVermaPearl = useVermaPearl;
        this.useScore = false;
    }

    public void setUseBackwardScoring(final boolean useBackwardScoring) {
        this.useBackwardScoring = useBackwardScoring;
    }

    /**
     * Scores the given permutation. This needs to be done initially before any move or tuck
     * operations are performed.
     *
     * @param order The permutation to score.
     * @return The score of it.
     */
    public float score(final List<Node> order) {
        this.pi = new LinkedList<>(order);
        this.scores = new LinkedList<>();

        for (int i1 = 0; i1 < order.size(); i1++) {
            this.scores.add(null);
        }

        this.prefixes = new LinkedList<>();
        for (int i1 = 0; i1 < order.size(); i1++) this.prefixes.add(null);
        initializeScores();
        return score();
    }

    /**
     * @return The score of the current permutation.
     */
    public float score() {
        return sum();
    }

    private float sum() {
        float score = 0;

        for (int i = 0; i < this.pi.size(); i++) {
            final float score1 = this.scores.get(i).getScore();
            score += score1;
        }

        return score;
    }

    /**
     * Performs a tuck operation. If pi[x] < pi[y], moves y to index of x; otherwise moves x to index of y.
     *
     * @param x The first variable.
     * @param y The second variable.
     */
    public void tuck(final Node x, final Node y) {
        if (index(x) < index(y)) {
            moveTo(y, index(x));
        } else if (index(x) > index(y)) {
            moveTo(x, index(y));
        }
    }

    /**
     * Moves v to a new index.
     *
     * @param v       The variable to move.
     * @param toIndex The index to move v to.
     */
    public void moveTo(final Node v, final int toIndex) {
        if (!this.pi.contains(v)) return;

        final int vIndex = index(v);

        if (vIndex == toIndex) return;

        if (lastMoveSame(vIndex, toIndex)) return;

        this.pi.remove(v);
        this.pi.add(toIndex, v);

        if (toIndex < vIndex) {
            updateScores(toIndex, vIndex);
        } else {
            updateScores(vIndex, toIndex);
        }
    }

    /**
     * Swaps m and n in the permutation.
     *
     * @param m The first variable.
     * @param n The second variable.
     * @return True iff the swap was done.
     */
    public boolean swap(final Node m, final Node n) {
        final int i = this.orderHash.get(m);
        final int j = this.orderHash.get(n);

        this.pi.set(i, n);
        this.pi.set(j, m);

        if (!validKnowledgeOrder(this.pi)) {
            this.pi.set(i, m);
            this.pi.set(j, n);
            return false;
        }

        if (i < j) {
            updateScores(i, j);
        } else {
            updateScores(j, i);
        }

        return true;
    }

    /**
     * Returns true iff x->y or y->x is a covered edge. x->y is a covered edge if
     * parents(x) = parents(y) \ {x}
     *
     * @param x The first variable.
     * @param y The second variable.
     * @return True iff x->y or y->x is a covered edge.
     */
    public boolean coveredEdge(final Node x, final Node y) {
        if (!adjacent(x, y)) return false;
        final Set<Node> px = getParents(x);
        final Set<Node> py = getParents(y);
        px.remove(y);
        py.remove(x);
        return px.equals(py);
    }

    /**
     * @return A copy of the current permutation.
     */
    public List<Node> getPi() {
        return new ArrayList<>(this.pi);
    }

    /**
     * Returns the current permutation without making a copy. Could be dangerous!
     *
     * @return the current permutation.
     */
    public List<Node> getOrderShallow() {
        return this.pi;
    }

    /**
     * Return the index of v in the current permutation.
     *
     * @param v The variable.
     * @return Its index.
     */
    public int index(final Node v) {
        if (!this.orderHash.containsKey(v)) {
            System.out.println();
        }

        final Integer integer = this.orderHash.get(v);

        if (integer == null)
            throw new IllegalArgumentException("First 'evaluate' a permutation containing variable "
                    + v + ".");

        return integer;
    }

    /**
     * Returns the parents of the node at index p.
     *
     * @param p The index of the node.
     * @return Its parents.
     */
    public Set<Node> getParents(final int p) {
        return new HashSet<>(this.scores.get(p).getParents());
    }

    /**
     * Returns the parents of a node v.
     *
     * @param v The variable.
     * @return Its parents.
     */
    public Set<Node> getParents(final Node v) {
        return getParents(index(v));
    }

    /**
     * Returns the nodes adjacent to v.
     *
     * @param v The variable.
     * @return Its adjacent nodes.
     */
    public Set<Node> getAdjacentNodes(final Node v) {
        final Set<Node> adj = new HashSet<>();

        for (final Node w : this.pi) {
            if (getParents(v).contains(w) || getParents(w).contains(v)) {
                adj.add(w);
            }
        }

        return adj;
    }

    /**
     * Returns the DAG build for the current permutation, or its CPDAG.
     *
     * @param cpDag True iff the CPDAG should be returned, False if the DAG.
     * @return This graph.
     */
    public Graph getGraph(final boolean cpDag) {
        final List<Node> order = getPi();
        final Graph G1 = new EdgeListGraph(this.variables);

        for (int p = 0; p < order.size(); p++) {
            for (final Node z : getParents(p)) {
                G1.addDirectedEdge(z, order.get(p));
            }
        }

        GraphUtils.replaceNodes(G1, this.variables);

        if (cpDag) {
            return SearchGraphUtils.cpdagForDag(G1);
        } else {
            return G1;
        }
    }

    /**
     * Returns a list of adjacent node pairs in the current graph.
     *
     * @return This list.
     */
    public List<NodePair> getAdjacencies() {
        final List<Node> order = getPi();
        final Set<NodePair> pairs = new HashSet<>();

        for (int i = 0; i < order.size(); i++) {
            for (int j = 0; j < i; j++) {
                final Node x = order.get(i);
                final Node y = order.get(j);

                if (adjacent(x, y)) {
                    pairs.add(new NodePair(x, y));
                }
            }
        }

        return new ArrayList<>(pairs);
    }

    public Map<Node, Set<Node>> getAdjMap() {
        final Map<Node, Set<Node>> adjMap = new HashMap<>();
        for (final Node node1 : getPi()) {
            if (!adjMap.containsKey(node1)) {
                adjMap.put(node1, new HashSet<>());
            }
            for (final Node node2 : getParents(node1)) {
                if (!adjMap.containsKey(node2)) {
                    adjMap.put(node2, new HashSet<>());
                }
                adjMap.get(node1).add(node2);
                adjMap.get(node2).add(node1);
            }
        }
        return adjMap;
    }


    public Map<Node, Set<Node>> getChildMap() {
        final Map<Node, Set<Node>> childMap = new HashMap<>();
        for (final Node node1 : getPi()) {
            for (final Node node2 : getParents(node1)) {
                if (!childMap.containsKey(node2)) {
                    childMap.put(node2, new HashSet<>());
                }
                childMap.get(node2).add(node1);
            }
        }
        return childMap;
    }

    public Set<Node> getAncestors(final Node node) {
        final Set<Node> ancestors = new HashSet<>();
        collectAncestorsVisit(node, ancestors);

        return ancestors;
    }

    private void collectAncestorsVisit(final Node node, final Set<Node> ancestors) {
        if (ancestors.contains(node)) {
            return;
        }

        ancestors.add(node);
        final Set<Node> parents = getParents(node);

        if (!parents.isEmpty()) {
            for (final Node parent : parents) {
                collectAncestorsVisit(parent, ancestors);
            }
        }
    }

    /**
     * Returns a list of edges for the current graph as a list of ordered pairs.
     *
     * @return This list.
     */
    public List<OrderedPair<Node>> getEdges() {
        final List<Node> order = getPi();
        final List<OrderedPair<Node>> edges = new ArrayList<>();

        for (final Node y : order) {
            for (final Node x : getParents(y)) {
                edges.add(new OrderedPair<>(x, y));
            }
        }

        return edges;
    }

    /**
     * @return The number of edges in the current graph.
     */
    public int getNumEdges() {
        int numEdges = 0;

        for (int p = 0; p < this.pi.size(); p++) {
            numEdges += getParents(p).size();
        }

        return numEdges;
    }

    /**
     * Returns the node at index j in pi.
     *
     * @param j The index.
     * @return The node at that index.
     */
    public Node get(final int j) {
        return this.pi.get(j);
    }

    /**
     * Bookmarks the current pi as index key.
     *
     * @param key This bookmark may be retrieved using the index 'key', an integer.
     *            This bookmark will be stored until it is retrieved and then removed.
     */
    public void bookmark(final int key) {
        this.bookmarkedOrders.put(key, new LinkedList<>(this.pi));
        this.bookmarkedScores.put(key, new LinkedList<>(this.scores));
        this.bookmarkedOrderHashes.put(key, new HashMap<>(this.orderHash));
    }

    /**
     * Bookmarks the current pi with index Integer.MIN_VALUE.
     */
    public void bookmark() {
        bookmark(Integer.MIN_VALUE);
    }

    /**
     * Retrieves the bookmarked state for index 'key' and removes that bookmark.
     *
     * @param key The integer key for this bookmark.
     */
    public void goToBookmark(final int key) {
        if (!this.bookmarkedOrders.containsKey(key)) {
            throw new IllegalArgumentException("That key was not bookmarked recently.");
        }

        this.pi = this.bookmarkedOrders.remove(key);
        this.scores = this.bookmarkedScores.remove(key);
        this.orderHash = this.bookmarkedOrderHashes.remove(key);
    }

    /**
     * Retries the bookmark with key = Integer.MIN_VALUE and removes the bookmark.
     */
    public void goToBookmark() {
        goToBookmark(Integer.MIN_VALUE);
    }

    /**
     * Clears all bookmarks.
     */
    public void clearBookmarks() {
        this.bookmarkedOrders.clear();
        this.bookmarkedScores.clear();
        this.bookmarkedOrderHashes.clear();
    }

    /**
     * @return The size of pi, the current permutation.
     */
    public int size() {
        return this.pi.size();
    }

    /**
     * Shuffles the current permutation and rescores it.
     */
    public void shuffleVariables() {
        this.pi = new LinkedList<>(this.pi);
        shuffle(this.pi);
        score(this.pi);
    }

    public List<Node> getShuffledVariables() {
        final List<Node> variables = getPi();
        shuffle(variables);
        return variables;
    }

    /**
     * Returns True iff a is adjacent to b in the current graph.
     *
     * @param a The first node.
     * @param b The second node.
     * @return True iff adj(a, b).
     */
    public boolean adjacent(final Node a, final Node b) {
        if (a == b) return false;
        return getParents(a).contains(b) || getParents(b).contains(a);
    }

    /**
     * Returns true iff [a, b, c] is a collider.
     *
     * @param a The first node.
     * @param b The second node.
     * @param c The third node.
     * @return True iff a->b<-c in the current DAG.
     */
    public boolean collider(final Node a, final Node b, final Node c) {
        return getParents(b).contains(a) && getParents(b).contains(c);
    }

    /**
     * Returns true iff [a, b, c] is a triangle.
     *
     * @param a The first node.
     * @param b The second node.
     * @param c The third node.
     * @return True iff adj(a, b) & adj(b, c) & adj(a, c).
     */
    public boolean triangle(final Node a, final Node b, final Node c) {
        return adjacent(a, b) && adjacent(b, c) && adjacent(a, c);
    }

    /**
     * True iff the nodes in W form a clique in the current DAG.
     *
     * @param W The nodes.
     * @return True iff these nodes form a clique.
     */
    public boolean clique(final List<Node> W) {
        for (int i = 0; i < W.size(); i++) {
            for (int j = i + 1; j < W.size(); j++) {
                if (!adjacent(W.get(i), W.get(j))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * A convenience method to reset the score cache if it becomes larger than a certain
     * size.
     *
     * @param maxSize The maximum size of the score cache; it the if the score cache is
     *                larger than this it will be cleared.
     */
    public void resetCacheIfTooBig(final int maxSize) {
        if (this.cache.size() > maxSize) {
            this.cache = new HashMap<>();
            System.out.println("Clearing cacche...");
            System.gc();
        }
    }

    private boolean validKnowledgeOrder(final List<Node> order) {
        for (int i = 0; i < order.size(); i++) {
            for (int j = i + 1; j < order.size(); j++) {
                if (this.knowledge.isForbidden(order.get(i).getName(), order.get(j).getName())) {
                    return false;
                }
            }
        }

        return true;
    }

    private void initializeScores() {
        for (int i1 = 0; i1 < this.pi.size(); i1++) this.prefixes.set(i1, null);

        for (int i = 0; i < this.pi.size(); i++) {
            recalculate(i);
            this.orderHash.put(this.pi.get(i), i);
        }

        updateScores(0, this.pi.size() - 1);
    }

    public void updateScores(final int i1, final int i2) {
        for (int i = i1; i <= i2; i++) {
            recalculate(i);
            this.orderHash.put(this.pi.get(i), i);
        }
    }

    private float score(final Node n, final Set<Node> pi) {
        if (this.cachingScores) {
            this.cache.computeIfAbsent(n, w -> new HashMap<>());
            final Float score = this.cache.get(n).get(pi);

            if (score != null) {
                return score;
            }
        }

        final int[] parentIndices = new int[pi.size()];

        int k = 0;

        for (final Node p : pi) {
            parentIndices[k++] = this.variablesHash.get(p);
        }

        final float v = (float) this.score.localScore(this.variablesHash.get(n), parentIndices);

        if (this.cachingScores) {
            this.cache.computeIfAbsent(n, w -> new HashMap<>());
            this.cache.get(n).put(new HashSet<>(pi), v);
        }

        return v;
    }

    private Set<Node> getPrefix(final int i) {
        final Set<Node> prefix = new HashSet<>();

        for (int j = 0; j < i; j++) {
            prefix.add(this.pi.get(j));
        }

        return prefix;
    }

    private void recalculate(final int p) {
        if (this.prefixes.get(p) == null || !this.prefixes.get(p).containsAll(getPrefix(p))) {
            final Pair p1 = this.scores.get(p);
            final Pair p2 = getParentsInternal(p);
            this.scores.set(p, p2);
        }
    }

    private void nodesHash(final Map<Node, Integer> nodesHash, final List<Node> variables) {
        for (int i = 0; i < variables.size(); i++) {
            nodesHash.put(variables.get(i), i);
        }
    }

    private boolean lastMoveSame(final int i1, final int i2) {
        if (i1 <= i2) {
            for (int i = i1; i <= i2; i++) {
                if (!getPrefix(i).equals(this.prefixes.get(i))) return false;
            }
        } else {
            for (int i = i2; i <= i1; i++) {
                if (!getPrefix(i).equals(this.prefixes.get(i))) return false;
            }
        }

        return true;
    }

    @NotNull
    private Pair getGrowShrinkScore(final int p) {
        final Node n = this.pi.get(p);

        final Set<Node> parents = new HashSet<>();
        boolean changed = true;

        float sMax = score(n, new HashSet<>());
        final List<Node> prefix = new ArrayList<>(getPrefix(p));

        // Backward scoring only from the prefix variables
        if (this.useBackwardScoring) {
            parents.addAll(prefix);
            sMax = score(n, parents);
            changed = false;
        }

        // Grow-shrink
        while (changed) {
            changed = false;

            // Let z be the node that maximizes the score...
            Node z = null;

            for (final Node z0 : prefix) {
                if (parents.contains(z0)) continue;

                if (this.knowledge.isForbidden(z0.getName(), n.getName())) continue;
                parents.add(z0);

                final float s2 = score(n, parents);

                if (s2 >= sMax) {
                    sMax = s2;
                    z = z0;
                }

                parents.remove(z0);
            }

            if (z != null) {
                parents.add(z);
                changed = true;
            }

        }

        boolean changed2 = true;

        while (changed2) {
            changed2 = false;

            Node w = null;

            for (final Node z0 : new HashSet<>(parents)) {
                parents.remove(z0);

                final float s2 = score(n, parents);

                if (s2 >= sMax) {
                    sMax = s2;
                    w = z0;
                }

                parents.add(z0);
            }

            if (w != null) {
                parents.remove(w);
                changed2 = true;
            }
        }

        if (this.useScore) {
            return new Pair(parents, Float.isNaN(sMax) ? Float.POSITIVE_INFINITY : sMax);
        } else {
            return new Pair(parents, -parents.size());
        }
    }

    private Pair getGrowShrinkIndependent(final int p) {
        final Node n = this.pi.get(p);

        final Set<Node> parents = new HashSet<>();

        final Set<Node> prefix = getPrefix(p);

        boolean changed1 = true;

        while (changed1) {
            changed1 = false;

            for (final Node z0 : prefix) {
                if (parents.contains(z0)) continue;
                if (this.knowledge.isForbidden(z0.getName(), n.getName())) continue;

                if (this.test.isDependent(n, z0, new ArrayList<>(parents))) {
                    parents.add(z0);
                    changed1 = true;
                }
            }
        }

        boolean changed2 = true;

        while (changed2) {
            changed2 = false;

            for (final Node z1 : new HashSet<>(parents)) {
                final Set<Node> _p = new HashSet<>(parents);
                _p.remove(z1);

                if (this.test.isIndependent(n, z1, new ArrayList<>(_p))) {
                    parents.remove(z1);
                    changed2 = true;
                }
            }
        }

        return new Pair(parents, -parents.size());
    }

    private Pair getParentsInternal(final int p) {
        if (this.useVermaPearl) {
            return getVermaPearlParents(p);
        } else {
            if (this.useScore) {
                return getGrowShrinkScore(p);
            } else {
                return getGrowShrinkIndependent(p);
            }
        }
    }

    /**
     * Returns the parents of the node at index p, calculated using Pearl's method.
     *
     * @param p The index.
     * @return The parents, as a Pair object (parents + score).
     */
    private Pair getVermaPearlParents(final int p) {
        final Node x = this.pi.get(p);
        final Set<Node> parents = new HashSet<>();
        final Set<Node> prefix = getPrefix(p);

        for (final Node y : prefix) {
            final Set<Node> minus = new HashSet<>(prefix);
            minus.remove(y);
            final ArrayList<Node> z = new ArrayList<>(minus);
            sort(z);

            if (this.test.isDependent(x, y, z)) {
                parents.add(y);
            }
        }

        return new Pair(parents, -parents.size());
    }

    public Set<Set<Node>> getSkeleton() {
        final List<Node> order = getPi();
        final Set<Set<Node>> skeleton = new HashSet<>();

        for (final Node y : order) {
            for (final Node x : getParents(y)) {
                final Set<Node> adj = new HashSet<>();
                adj.add(x);
                adj.add(y);
                skeleton.add(adj);
            }
        }

        return skeleton;
    }


    public void moveToNoUpdate(final Node v, final int toIndex) {
        bookmark(-55);

        if (!this.pi.contains(v)) return;

        final int vIndex = index(v);

        if (vIndex == toIndex) return;

        if (lastMoveSame(vIndex, toIndex)) return;

        this.pi.remove(v);
        this.pi.add(toIndex, v);

        if (!validKnowledgeOrder(this.pi)) {
            goToBookmark(-55);
        }

    }

    private static class Pair {
        private final Set<Node> parents;
        private final float score;

        private Pair(final Set<Node> parents, final float score) {
            this.parents = parents;
            this.score = score;
        }

        public Set<Node> getParents() {
            return this.parents;
        }

        public float getScore() {
            return this.score;
        }

        public int hashCode() {
            return this.parents.hashCode() + (int) floor(10000D * this.score);
        }

        public boolean equals(final Object o) {
            if (o == null) return false;
            if (!(o instanceof Pair)) return false;
            final Pair thatPair = (Pair) o;
            return this.parents.equals(thatPair.parents) && this.score == thatPair.score;
        }
    }
}
