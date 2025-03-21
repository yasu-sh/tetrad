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

package edu.cmu.tetrad.graph;

import java.beans.PropertyChangeListener;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Represents the graphical structure of a structural equation model. The linear structure of the structural equation
 * model is constructed by adding non-error nodes to the graph and connecting them with directed edges. As this is done,
 * the graph automatically maintains the invariant that endogenous non-error nodes are associated with explicit error
 * nodes in the graph and exogenous non-error nodes are not. An associated error node for a node N is an error node that
 * has N as its only child, E--&gt;N. Error nodes for exogenous nodes are always implicit in the graph. So as nodes
 * become endogenous, error nodes are added for them, and as they become exogenous, error nodes are removed for them.
 * Correlated errors are represented using directed edges among exogenous nodes. Directed edges may therefore be added
 * among any exogenous nodes in the graph, though the easiest way to add (or remove) exogenous nodes is to determine
 * which non-exogenous nodes N1, N2 they are representing correlated errors for and then to use this formulation:
 * <pre>
 *     addBidirectedEdge(getExogenous(node1), getExogenous(node2));
 *     removeEdge(getExogenous(node1), getExogenous(node2));
 * </pre>
 * This avoids the problem of not knowing whether the exogenous node for a node is itself or its associated error node.
 *
 * @author josephramsey
 */
public final class SemGraph implements Graph {
    static final long serialVersionUID = 23L;

    /**
     * The underlying graph that stores all the information. This needs to be an EdgeListGraph or something at least
     * that can allow nodes to quickly be added or removed.
     *
     * @serial
     */
    private final Graph graph;

    /**
     * A map from nodes to their error nodes, if they exist. This includes variables nodes to their error nodes and
     * error nodes to themselves. Added because looking them up in the graph was not scalable.
     *
     * @serial
     */
    private Map<Node, Node> errorNodes = new HashMap<>();

    /**
     * True if error terms for exogenous terms should be shown.
     *
     * @serial
     */
    private boolean showErrorTerms;

    private boolean pag;
    private boolean cpdag;

    private final Map<String, Object> attributes = new HashMap<>();
    private final Paths paths;

    private Set<Triple> underLineTriples = new HashSet<>();
    private Set<Triple> dottedUnderLineTriples = new HashSet<>();
    private Set<Triple> ambiguousTriples = new HashSet<>();

    //=========================CONSTRUCTORS============================//

    /**
     * Constructs a new, empty SemGraph.
     */
    public SemGraph() {
        this.graph = new EdgeListGraph();
        this.paths = new Paths(this);
    }

    /**
     * Constructs a new SemGraph from the nodes and edges of the given graph.
     */
    public SemGraph(Graph graph) {
        if (graph instanceof SemGraph) {
            if (graph.isTimeLagModel()) {
                this.graph = new TimeLagGraph((TimeLagGraph) ((SemGraph) graph).graph);
            } else {
                this.graph = new EdgeListGraph(graph);
            }

            this.errorNodes =
                    new HashMap<>(((SemGraph) graph).errorNodes);

            for (Node node : graph.getNodes()) {
                if (!this.errorNodes.containsKey(node)) {
                    addErrorNode(node);
                }
            }

            this.showErrorTerms = ((SemGraph) graph).showErrorTerms;
            setShowErrorTerms(this.showErrorTerms);
        } else if (graph instanceof TimeLagGraph) {
            this.graph = new TimeLagGraph((TimeLagGraph) graph);
        } else {
            this.graph = new EdgeListGraph(graph.getNodes());

            for (Node node : this.graph.getNodes()) {
                addErrorNode(node);
            }

            setShowErrorTerms(true);

            for (Edge edge : graph.getEdges()) {
                if (Edges.isDirectedEdge(edge)) {
                    addEdge(edge);
                } else if (Edges.isBidirectedEdge(edge)) {
                    Node node1 = edge.getNode1();
                    Node node2 = edge.getNode2();

                    addBidirectedEdge(getExogenous(node1), getExogenous(node2));
                } else {
                    throw new IllegalArgumentException("A SEM graph may contain " +
                            "only directed and bidirected edges: " + edge);
                }
            }

            setShowErrorTerms(false);

        }

        this.paths = new Paths(this);
    }

    /**
     * Copy constructor.
     */
    public SemGraph(SemGraph graph) {
        if (graph.isTimeLagModel()) {
            this.graph = new TimeLagGraph((TimeLagGraph) graph.graph);
        } else {
            this.graph = new EdgeListGraph(graph.getGraph());
        }

        this.errorNodes = new HashMap<>(graph.errorNodes);
        this.pag = graph.pag;
        this.cpdag = graph.cpdag;

        if (graph.showErrorTerms) {
            for (Node node : this.graph.getNodes()) {
                if (!errorNodes().containsKey(node)) {
                    addErrorNode(node);
                }
            }
        }

        this.showErrorTerms = graph.showErrorTerms;
        setShowErrorTerms(this.showErrorTerms);

//        for (Edge edge : graph.getEdges()) {
//            if (graph.isHighlighted(edge)) {
//                setHighlighted(edge, true);
//            }
//        }

        this.paths = new Paths(this);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemGraph serializableInstance() {
        return new SemGraph();
    }

    //============================PUBLIC METHODS==========================//

    /**
     * @return the error node associated with the given node, or null if the node has no associated error node.
     */
    public Node getErrorNode(Node node) {
        return this.errorNodes.get(node);
    }

    public boolean isParameterizable(Node node) {
        return getGraph().isParameterizable(node);
    }

    /**
     * @return a tier order, including error terms, if they are shown.
     * @throws IllegalStateException if the graph is cyclic.
     */
    public List<Node> getFullTierOrdering() {
        if (paths.existsDirectedCycle()) {
            throw new IllegalStateException("The tier ordering method assumes acyclicity.");
        }

        List<Node> found = new LinkedList<>();
        Set<Node> notFound = new HashSet<>(getNodes());

        while (!notFound.isEmpty()) {
            for (Iterator<Node> it = notFound.iterator(); it.hasNext(); ) {
                Node node = it.next();

                List<Node> parents = getParents(node);
//                parents.removeAll(errorTerms);

                if (new HashSet<>(found).containsAll(parents)) {
                    found.add(node);
                    it.remove();
                }
            }
        }

        return found;
    }

    /**
     * @return true iff either node associated with edge is an error term.
     */
    public static boolean isErrorEdge(Edge edge) {
        return (edge.getNode1().getNodeType() == NodeType.ERROR ||
                (edge.getNode2().getNodeType() == NodeType.ERROR));
    }

    /**
     * @return the variable node for this node--that is, the associated node, if this is an error node, or the node
     * itself, if it is not.
     */
    public Node getVarNode(Node node) {
        boolean isError = node.getNodeType() == NodeType.ERROR;

        if (!containsNode(node)) {
            throw new NullPointerException("Node is not in graph: " + node);
        }

        if (isError) {
            return GraphUtils.getAssociatedNode(node, this);
        } else {
            return node;
        }
    }

    /**
     * @param node the node you want the exogenous node for.
     * @return the exogenous node for that node.
     */
    public Node getExogenous(Node node) {
        return isExogenous(node) ? node : this.errorNodes.get(node);
    }

    public void transferNodesAndEdges(Graph graph)
            throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    public void transferAttributes(Graph graph)
            throws IllegalArgumentException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Paths paths() {
        return this.paths;
    }

    public List<String> getNodeNames() {
        return getGraph().getNodeNames();
    }

    public void fullyConnect(Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public void reorientAllWith(Endpoint endpoint) {
        throw new UnsupportedOperationException();
    }

    public List<Node> getAdjacentNodes(Node node) {
        return getGraph().getAdjacentNodes(node);
    }

    public List<Node> getNodesInTo(Node node, Endpoint endpoint) {
        return getGraph().getNodesInTo(node, endpoint);
    }

    public List<Node> getNodesOutTo(Node node, Endpoint n) {
        return getGraph().getNodesOutTo(node, n);
    }

    public List<Node> getNodes() {
        return getGraph().getNodes();
    }

    public boolean removeEdge(Node node1, Node node2) {
        List<Edge> edges = getEdges(node1, node2);

        if (edges.size() > 1) {
            throw new IllegalStateException(
                    "There is more than one edge between " + node1 + " and " +
                            node2);
        }

        return removeEdges(edges);
    }

    public boolean removeEdges(Node node1, Node node2) {
        return removeEdges(getEdges(node1, node2));
    }

    public boolean isAdjacentTo(Node nodeX, Node nodeY) {
        return getGraph().isAdjacentTo(nodeX, nodeY);
    }

    public boolean setEndpoint(Node node1, Node node2, Endpoint endpoint) {
        return getGraph().setEndpoint(node1, node2, endpoint);
    }

    public Endpoint getEndpoint(Node node1, Node node2) {
        return getGraph().getEndpoint(node1, node2);
    }

    public boolean equals(Object o) {
        return (o instanceof SemGraph) && getGraph().equals(o);
    }

    public Graph subgraph(List<Node> nodes) {
        return getGraph().subgraph(nodes);
    }


    public boolean addDirectedEdge(Node nodeA, Node nodeB) {
        return addEdge(Edges.directedEdge(nodeA, nodeB));
    }

    public boolean addUndirectedEdge(Node nodeA, Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addNondirectedEdge(Node nodeA, Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addPartiallyOrientedEdge(Node nodeA, Node nodeB) {
        throw new UnsupportedOperationException();
    }

    public boolean addBidirectedEdge(Node nodeA, Node nodeB) {
        return addEdge(Edges.bidirectedEdge(nodeA, nodeB));
    }

    public boolean addEdge(Edge edge) {
        Node node1 = edge.getNode1();
        Node node2 = edge.getNode2();

        if (!getGraph().containsNode(node1) || !getGraph().containsNode(node2)) {
            if (node1.getNodeType() == NodeType.ERROR || node2.getNodeType() == NodeType.ERROR) {
                return false;
            }

            throw new IllegalArgumentException(
                    "Nodes for edge must be in graph already: " + edge);
        }

        if (Edges.isDirectedEdge(edge)) {
            if (getGraph().containsEdge(edge)) {
                return false;
            }

            Node head = Edges.getDirectedEdgeHead(edge);

            if (head.getNodeType() == NodeType.ERROR) {
                return false;
            }

            getGraph().addEdge(edge);
            adjustErrorForNode(head);
            return true;
        } else if (Edges.isBidirectedEdge(edge)) {
            if (getGraph().containsEdge(edge)) {
                return false;
            }

            getGraph().addEdge(edge);
            return true;
        } else {
            throw new IllegalArgumentException(
                    "Only directed and bidirected edges allowed.");
        }
    }

    public boolean addNode(Node node) {
        if (node.getNodeType() == NodeType.ERROR) {
            throw new IllegalArgumentException("Error nodes cannot be added " +
                    "directly to the graph: " + node);
        }

        return getGraph().addNode(node);
    }

    public void addPropertyChangeListener(PropertyChangeListener l) {
        getGraph().addPropertyChangeListener(l);
    }

    public boolean containsEdge(Edge edge) {
        return getGraph().containsEdge(edge);
    }

    public boolean containsNode(Node node) {
        return getGraph().containsNode(node);
    }

    public Set<Edge> getEdges() {
        return getGraph().getEdges();
    }

    public List<Edge> getEdges(Node node) {
        return getGraph().getEdges(node);
    }

    public List<Edge> getEdges(Node node1, Node node2) {
        return getGraph().getEdges(node1, node2);
    }

    public Node getNode(String name) {
        return getGraph().getNode(name);
    }

    public int getNumEdges() {
        return getGraph().getNumEdges();
    }

    public int getNumNodes() {
        return getGraph().getNumNodes();
    }

    public int getNumEdges(Node node) {
        return getGraph().getNumEdges(node);
    }

    public boolean removeEdge(Edge edge) {
        if (!getGraph().containsEdge(edge)) {
            throw new IllegalArgumentException(
                    "Can only remove edges that are " +
                            "already in the graph: " + edge);
        }

        if (Edges.isDirectedEdge(edge)) {
            Node head = Edges.getDirectedEdgeHead(edge);
            Node tail = Edges.getDirectedEdgeTail(edge);

            if (tail.getNodeType() != NodeType.ERROR) {
                getGraph().removeEdge(edge);
                adjustErrorForNode(head);
                return true;
            } else {
                return false;
            }
        } else if (Edges.isBidirectedEdge(edge)) {
            return getGraph().removeEdge(edge);
        } else {
            throw new IllegalArgumentException(
                    "Only directed and bidirected edges allowed.");
        }
    }

    public boolean removeEdges(Collection<Edge> edges) {
        boolean change = false;

        for (Edge edge : edges) {
            boolean _change = removeEdge(edge);
            change = change || _change;
        }

        return change;
    }

    public boolean removeNode(Node node) {
        if (!getGraph().containsNode(node)) {
            throw new IllegalArgumentException(
                    "Graph must contain node: " + node);
        }

        if (node.getNodeType() == NodeType.ERROR) {
            throw new IllegalArgumentException(
                    "Error nodes cannot be removed " +
                            "directly from the graph: " + node);
        }

        Node errorNode = getErrorNode(node);

        if (errorNode != null) {
            getGraph().removeNode(errorNode);
            this.errorNodes.remove(errorNode);
            this.errorNodes.remove(node);
        }

        List<Node> children = getGraph().getChildren(node);
        getGraph().removeNode(node);

        for (Node child : children) {
            adjustErrorForNode(child);
        }

        return true;
    }

    public void clear() {
        getGraph().clear();
    }

    public boolean removeNodes(List<Node> nodes) {
        return getGraph().removeNodes(nodes);
    }

    public boolean isDefNoncollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefNoncollider(node1, node2, node3);
    }

    public boolean isDefCollider(Node node1, Node node2, Node node3) {
        return getGraph().isDefCollider(node1, node2, node3);
    }

    public List<Node> getChildren(Node node) {
        return getGraph().getChildren(node);
    }

    public int getDegree() {
        return getGraph().getDegree();
    }

    public Edge getEdge(Node node1, Node node2) {
        return getGraph().getEdge(node1, node2);
    }

    @Override
    public Edge getDirectedEdge(Node node1, Node node2) {
        return graph.getDirectedEdge(node1, node2);
    }

    public List<Node> getParents(Node node) {
        return getGraph().getParents(node);
    }

    public int getIndegree(Node node) {
        return getGraph().getIndegree(node);
    }

    @Override
    public int getDegree(Node node) {
        return getGraph().getDegree(node);
    }

    public int getOutdegree(Node node) {
        return getGraph().getOutdegree(node);
    }

    public boolean isChildOf(Node node1, Node node2) {
        return getGraph().isChildOf(node1, node2);
    }

    public boolean isParentOf(Node node1, Node node2) {
        return getGraph().isParentOf(node1, node2);
    }

    public boolean isExogenous(Node node) {
        return getGraph().isExogenous(node) || isShowErrorTerms() && getErrorNode(node) == null;
    }

    public String toString() {
        return getGraph().toString();
    }


    public boolean isShowErrorTerms() {
        return this.showErrorTerms;
    }

    public void setShowErrorTerms(boolean showErrorTerms) {
        this.showErrorTerms = showErrorTerms;

        List<Node> nodes = getNodes();

        for (Node node : nodes) {
            if (!isParameterizable(node)) {
                continue;
            }

            if (!(node.getNodeType() == NodeType.ERROR)) {
                adjustErrorForNode(node);
            }
        }
    }

    public boolean isTimeLagModel() {
        return this.graph instanceof TimeLagGraph;
    }

    public TimeLagGraph getTimeLagGraph() {
        if (!isTimeLagModel()) {
            throw new IllegalArgumentException("Not a time lag model.");
        }

        return new TimeLagGraph((TimeLagGraph) this.graph);
    }

    @Override
    public Set<Node> getSepset(Node n1, Node n2) {
        return this.graph.getSepset(n1, n2);
    }

    @Override
    public void setNodes(List<Node> nodes) {
        this.graph.setNodes(nodes);
    }

    //========================PRIVATE METHODS===========================//

    /**
     * Creates a new error node, names it, and adds it to the graph.
     *
     * @param node the node which the new error node which be attached to.
     */
    private void addErrorNode(Node node) {
        if (this.errorNodes.get(node) != null) {
            throw new IllegalArgumentException("Node already in map.");
        }

        List<Node> nodes = getGraph().getNodes();

        Node error = errorNodes().get(node);

        if (error == null) {
            error = new GraphNode("E" + "_" + node.getName());
            error.setCenter(node.getCenterX() + 50, node.getCenterY() + 50);
            error.setNodeType(NodeType.ERROR);
            this.errorNodes.put(node, error);
        }

        for (Node possibleError : nodes) {
            if (error.getName().equals(possibleError.getName())) {
                moveAttachedBidirectedEdges(possibleError, node);
                if (getGraph().containsNode(possibleError)) {
                    getGraph().removeNode(possibleError);
                }
                this.errorNodes.remove(node);
                this.errorNodes.remove(possibleError);
            }
        }

        getGraph().addNode(error);
        this.errorNodes.put(node, error);
        this.errorNodes.put(error, error);
        addDirectedEdge(error, node);
    }

    private Map<Node, Node> errorNodes() {
        if (this.errorNodes == null) {
            this.errorNodes = new HashMap<>();
        }

        return this.errorNodes;
    }

    private void moveAttachedBidirectedEdges(Node node1, Node node2) {
        if (node1 == null || node2 == null) {
            throw new IllegalArgumentException("Node must not be null.");
        }

        Graph graph = getGraph();
        List<Edge> edges = graph.getEdges(node1);

        if (edges == null) {
            System.out.println();
            edges = new ArrayList<>();
        }

        List<Edge> attachedEdges = new LinkedList<>(edges);

        for (Edge edge : attachedEdges) {
            if (Edges.isBidirectedEdge(edge)) {
                graph.removeEdge(edge);
                Node distal = edge.getDistalNode(node1);
                graph.addBidirectedEdge(node2, distal);
            }
        }
    }

    /**
     * If the specified node is exogenous and has an error node, moves any edges attached to its error node to the node
     * itself and removes the error node. If the specified node is endogenous and has no error node, adds an error node
     * and moves any bidirected edges attached to the node to its error node.
     */
    private void adjustErrorForNode(Node node) {
        Node errorNode = getErrorNode(node);
        if (!this.showErrorTerms) {
//            if (!isShowErrorTerms() || shouldBeExogenous(node)) {

            if (errorNode != null && this.graph.containsNode(errorNode)) {
                moveAttachedBidirectedEdges(errorNode, node);
                getGraph().removeNode(errorNode);
                this.errorNodes.remove(node);
                this.errorNodes.remove(errorNode);
            }
        } else {

            if (errorNode == null) {
                addErrorNode(node);
            }

            errorNode = getErrorNode(node);
            moveAttachedBidirectedEdges(node, errorNode);
        }
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.)
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (getGraph() == null) {
            throw new NullPointerException();
        }
    }

    private Graph getGraph() {
        return this.graph;
    }

    public void resetErrorPositions() {
        for (Node node : getNodes()) {
            Node error = errorNodes().get(node);

            if (error != null) {
                error.setCenter(node.getCenterX() + 50, node.getCenterY() + 50);
            }
        }
    }

    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    @Override
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

    public Set<Triple> getAmbiguousTriples() {
        return new HashSet<>(this.ambiguousTriples);
    }

    public void setAmbiguousTriples(Set<Triple> triples) {
        this.ambiguousTriples.clear();

        for (Triple triple : triples) {
            addAmbiguousTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public Set<Triple> getUnderLines() {
        return new HashSet<>(this.underLineTriples);
    }

    public Set<Triple> getDottedUnderlines() {
        return new HashSet<>(this.dottedUnderLineTriples);
    }

    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isAmbiguousTriple(Node x, Node y, Node z) {
        return this.ambiguousTriples.contains(new Triple(x, y, z));
    }

    /**
     * States whether r-s-r is an underline triple or not.
     */
    public boolean isUnderlineTriple(Node x, Node y, Node z) {
        return this.underLineTriples.contains(new Triple(x, y, z));
    }

    public void addAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.add(new Triple(x, y, z));
    }

    public void addUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.underLineTriples.add(new Triple(x, y, z));
    }

    public void addDottedUnderlineTriple(Node x, Node y, Node z) {
        Triple triple = new Triple(x, y, z);

        if (!triple.alongPathIn(this)) {
            return;
        }

        this.dottedUnderLineTriples.add(triple);
    }

    public void removeAmbiguousTriple(Node x, Node y, Node z) {
        this.ambiguousTriples.remove(new Triple(x, y, z));
    }

    public void removeUnderlineTriple(Node x, Node y, Node z) {
        this.underLineTriples.remove(new Triple(x, y, z));
    }

    public void removeDottedUnderlineTriple(Node x, Node y, Node z) {
        this.dottedUnderLineTriples.remove(new Triple(x, y, z));
    }

    public void setUnderLineTriples(Set<Triple> triples) {
        this.underLineTriples.clear();

        for (Triple triple : triples) {
            addUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public void setDottedUnderLineTriples(Set<Triple> triples) {
        this.dottedUnderLineTriples.clear();

        for (Triple triple : triples) {
            addDottedUnderlineTriple(triple.getX(), triple.getY(), triple.getZ());
        }
    }

    public void removeTriplesNotInGraph() {
        for (Triple triple : new HashSet<>(this.ambiguousTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY())
                    || !containsNode(triple.getZ())) {
                this.ambiguousTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY())
                    || !isAdjacentTo(triple.getY(), triple.getZ())) {
                this.ambiguousTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(this.underLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY())
                    || !containsNode(triple.getZ())) {
                this.underLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || !isAdjacentTo(triple.getY(), triple.getZ())) {
                this.underLineTriples.remove(triple);
            }
        }

        for (Triple triple : new HashSet<>(this.dottedUnderLineTriples)) {
            if (!containsNode(triple.getX()) || !containsNode(triple.getY()) || !containsNode(triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
                continue;
            }

            if (!isAdjacentTo(triple.getX(), triple.getY()) || isAdjacentTo(triple.getY(), triple.getZ())) {
                this.dottedUnderLineTriples.remove(triple);
            }
        }
    }
}






