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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IonInput;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;

/**
 * Holds a tetrad-style graph with all of the constructors necessary for it to
 * serve as a model for the tetrad application.
 *
 * @author josephramsey
 */
public class GraphSelectionWrapper implements GraphSource, KnowledgeBoxInput, IonInput, IndTestProducer {
    static final long serialVersionUID = 23L;
    private final Parameters params;
    private List<Node> selectedNodes;
    private List<Graph> graphs = new ArrayList<>();

    public enum Type {
        Subgraph, Adjacents, Adjacents_of_Adjacents, Adjacents_of_Adjacents_of_Adjacents, Parents, Children, Ancestors, Descendants,
        Markov_Blankets, Treks, Trek_Edges,
        Paths, Path_Edges, Directed_Paths, Directed_Path_Edges, Y_Structures,
        Pag_Y_Structures, Indegree, Out_Degree, Degree
    }

    public enum nType {equals, atMost, atLeast}

    //=============================CONSTRUCTORS==========================//

    public GraphSelectionWrapper(GraphSource graphWrapper, Parameters parameters) {
        this(graphWrapper.getGraph(), parameters);
    }

    public GraphSelectionWrapper(List<Graph> graphs, Parameters params) {
        if (graphs == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.params = params;


        init(params, graphs);
    }


    public GraphSelectionWrapper(Graph graph, Parameters params) {
        if (graph == null) {
            throw new NullPointerException("Graph must not be null.");
        }

        this.params = params;

        List<Graph> graphs = new ArrayList<>();
        graphs.add(graph);

        init(params, graphs);
    }

    public GraphSelectionWrapper(Graph graphs, Parameters params, String message) {
        this(graphs, params);
        TetradLogger.getInstance().log("info", message);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static GraphSelectionWrapper serializableInstance() {
        return new GraphSelectionWrapper(Dag.serializableInstance(), new Parameters());
    }

    //===============================================METHODS================================//

    private void init(Parameters params, List<Graph> graphs) {
        setGraphs(graphs);

        calculateSelection();
        List<Graph> selectionGraphs = getSelectionGraphs(params);

        for (int i = 0; i < graphs.size(); i++) {
            Graph graph = selectionGraphs.get(i);
            LayoutUtil.fruchtermanReingoldLayout(graph);
        }

        // No variable is selected by default - Updated 11/19/2018 by Zhou

        log();
    }


    public List<Node> getSelectedVariables() {
        return this.selectedNodes;
    }

    private List<Graph> getSelectionGraphs(Parameters params) {
        return (List<Graph>) params.get("selectionGraphs",
                Collections.singletonList(new EdgeListGraph()));
    }

    public void calculateSelection() {
        List<Graph> selectedGraphs = new ArrayList<>();

        for (int i = 0; i < getGraphs().size(); i++) {
            selectedGraphs.add(calculateSelectionGraph(i));
        }

        this.params.set("selectionGraphs", selectedGraphs);
    }

    public List<Graph> getGraphs() {

        if (this.graphs == null || this.graphs.isEmpty()) {
            List<Graph> _graphs = Collections.singletonList(new EdgeListGraph());
            this.params.set("graphs", _graphs);
            return _graphs;
        } else {
            return this.graphs;
        }
    }

    private Graph calculateSelectionGraph(int k) {
        List<Node> selectedVariables = getSelectedVariables();
        selectedVariables = GraphUtils.replaceNodes(selectedVariables, getSelectedGraph(k).getNodes());
        Graph selectedGraph;

        if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Subgraph.toString())) {
            selectedGraph = getSelectedGraph(k).subgraph(selectedVariables);
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "subgraph").equals(Type.Adjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Adjacents_of_Adjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            for (Node node : new HashSet<>(adj)) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Adjacents_of_Adjacents_of_Adjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            for (Node node : new HashSet<>(adj)) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            for (Node node : new HashSet<>(adj)) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "subgraph").equals(Type.Adjacents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getAdjacentNodes(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "parents").equals(Type.Parents.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getParents(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "children").equals(Type.Children.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).getChildren(node)));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "ancestors").equals(Type.Ancestors.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).paths().getAncestors(Collections.singletonList(node))));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "descendants").equals(Type.Descendants.toString())) {
            Set<Node> adj = new HashSet<>(selectedVariables);

            for (Node node : selectedVariables) {
                adj.addAll((getSelectedGraph(k).paths().getDescendants(Collections.singletonList(node))));
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(adj)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Descendants.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Edge> ys = yStructures(getGraphAtIndex(k), node, k);
                edges.addAll(ys);
            }

            Graph subGraph = new EdgeListGraph();

            for (Edge edge : edges) {
                if (!subGraph.containsNode(edge.getNode1())) {
                    subGraph.addNode(edge.getNode1());
                }

                if (!subGraph.containsNode(edge.getNode2())) {
                    subGraph.addNode(edge.getNode2());
                }

                subGraph.addEdge(edge);
            }

            selectedGraph = subGraph;
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Pag_Y_Structures.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Edge> ys = pagYStructures(getGraphAtIndex(k), node, k);
                edges.addAll(ys);
            }

            Graph subGraph = new EdgeListGraph();

            for (Edge edge : edges) {
                if (!subGraph.containsNode(edge.getNode1())) {
                    subGraph.addNode(edge.getNode1());
                }

                if (!subGraph.containsNode(edge.getNode2())) {
                    subGraph.addNode(edge.getNode2());
                }

                subGraph.addEdge(edge);
            }

            selectedGraph = subGraph;
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Markov_Blankets.toString())) {
            Set<Node> _nodes = new HashSet<>();

            for (Node node : selectedVariables) {
                Set<Node> mb = mb(getGraphAtIndex(k), node);
                mb.add(node);
                _nodes.addAll(mb);
            }

            selectedGraph = (getSelectedGraph(k).subgraph(new ArrayList<>(_nodes)));
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Treks.toString())) {
            Graph g = new EdgeListGraph(selectedVariables);

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);
                    List<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, getN() + 1);

                    if (this.params.getString("nType", "atLeast").equals(nType.atMost.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    }
                }
            }

            selectedGraph = g;
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Trek_Edges.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);

                    if (this.params.getString("nType", "atLeast").equals(nType.atMost.toString())) {
                        List<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, getN() + 1);
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.atLeast.toString())) {
                        List<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, -1);
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        List<List<Node>> paths = getGraphAtIndex(k).paths().treks(x, y, getN() + 1);
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                            }
                        }
                    }
                }
            }

            selectedGraph = graphFromEdges(edges, new ArrayList<>());
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Paths.toString())) {
            Graph g = new EdgeListGraph(selectedVariables);

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);
                    List<List<Node>> paths = getGraphAtIndex(k).paths().allPathsFromTo(x, y, getN());

                    if (this.params.getString("nType", "atLeast").equals(nType.atMost.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                g.addUndirectedEdge(x, y);
                                break;
                            }
                        }
                    }
                }
            }

            selectedGraph = g;
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Path_Edges.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = i + 1; j < selectedVariables.size(); j++) {
                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);

                    if (this.params.getString("nType", "atLeast").equals(nType.atMost.toString())) {
                        List<List<Node>> paths = getGraphAtIndex(k).paths().allPathsFromTo(x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.atLeast.toString())) {
                        List<List<Node>> paths = getGraphAtIndex(k).paths().allPathsFromTo(x, y, -1);
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        List<List<Node>> paths = getGraphAtIndex(k).paths().allPathsFromTo(x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                            }
                        }
                    }
                }
            }

            selectedGraph = graphFromEdges(edges, new ArrayList<>());
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Directed_Paths.toString())) {
            Graph g = new EdgeListGraph(selectedVariables);

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = 0; j < selectedVariables.size(); j++) {
                    if (i == j) continue;

                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);

                    if (this.params.getString("nType", "atLeast").equals(nType.atMost.toString())) {
                        List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPathsFromTo(x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                g.addDirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.atLeast.toString())) {
                        List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPathsFromTo(x, y, -1);
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                g.addDirectedEdge(x, y);
                                break;
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPathsFromTo(x, y, getN());
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                g.addDirectedEdge(x, y);
                                break;
                            }
                        }
                    }
                }
            }

            selectedGraph = g;
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Directed_Path_Edges.toString())) {
            Set<Edge> edges = new HashSet<>();

            for (int i = 0; i < selectedVariables.size(); i++) {
                for (int j = 0; j < selectedVariables.size(); j++) {
                    if (i == j) continue;

                    Node x = selectedVariables.get(i);
                    Node y = selectedVariables.get(j);
                    List<List<Node>> paths = getGraphAtIndex(k).paths().allDirectedPathsFromTo(x, y, getN());

                    if (this.params.getString("nType", "atLeast").equals(nType.atMost.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() <= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && !paths.isEmpty()) {
                        for (List<Node> path : paths) {
                            if (path.size() >= getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                            }
                        }
                    } else if (this.params.getString("nType", "atLeast").equals(nType.equals.toString())) {
                        for (List<Node> path : paths) {
                            if (path.size() == getN() + 1) {
                                edges.addAll(getEdgesFromPath(path, getGraphAtIndex(k)));
                            }
                        }
                    }
                }
            }

            selectedGraph = graphFromEdges(edges, new ArrayList<>());
            this.params.set("highlightInEditor", selectedVariables);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Indegree.toString())) {
            Set<Edge> g = new HashSet<>();
            List<Node> nodes = new ArrayList<>();

            for (Node n : selectedVariables) {
                List<Node> h = (getSelectedGraph(k).getParents(n));

                if (this.params.getString("nType", "atLeast").equals(nType.atMost.toString()) && h.size() <= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add((getSelectedGraph(k).getEdge(m, n)));
                    }
                } else if (this.params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && h.size() >= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add((getSelectedGraph(k).getEdge(m, n)));
                    }
                } else if (this.params.getString("nType", "atLeast").equals(nType.equals.toString()) && h.size() == getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add((getSelectedGraph(k).getEdge(m, n)));
                    }
                }
            }

            selectedGraph = graphFromEdges(g, new ArrayList<>());
            this.params.set("highlightInEditor", nodes);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Out_Degree.toString())) {
            Set<Edge> g = new HashSet<>();
            List<Node> nodes = new ArrayList<>();

            for (Node n : selectedVariables) {
                List<Node> h = (getSelectedGraph(k).getChildren(n));

                if (this.params.getString("nType", "atLeast").equals(nType.atMost.toString()) && h.size() <= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add((getSelectedGraph(k).getEdge(m, n)));
                    }
                } else if (this.params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && h.size() >= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add((getSelectedGraph(k).getEdge(m, n)));
                    }
                } else if (this.params.getString("nType", "atLeast").equals(nType.equals.toString()) && h.size() == getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add((getSelectedGraph(k).getEdge(m, n)));
                    }
                }
            }

            selectedGraph = graphFromEdges(g, nodes);
            this.params.set("highlightInEditor", nodes);
        } else if (this.params.getString("graphSelectionType", "Subgraph").equals(Type.Degree.toString())) {
            Set<Edge> g = new HashSet<>();
            List<Node> nodes = new ArrayList<>();

            for (Node n : selectedVariables) {
                List<Node> h = (getSelectedGraph(k).getAdjacentNodes(n));

                if (this.params.getString("nType", "atLeast").equals(nType.atMost.toString()) && h.size() <= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add((getSelectedGraph(k).getEdge(m, n)));
                    }
                } else if (this.params.getString("nType", "atLeast").equals(nType.atLeast.toString()) && h.size() >= getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add((getSelectedGraph(k).getEdge(m, n)));
                    }
                } else if (this.params.getString("nType", "atLeast").equals(nType.equals.toString()) && h.size() == getN()) {
                    nodes.add(n);
                    for (Node m : h) {
                        g.add((getSelectedGraph(k).getEdge(m, n)));
                    }
                }
            }

            selectedGraph = graphFromEdges(g, nodes);
            this.params.set("highlightInEditor", nodes);
        } else {
            throw new IllegalArgumentException("Unrecognized selection type: " + this.params.getString("graphSelectionType", "subgraph"));
        }

        return selectedGraph;
    }

    private Graph getGraphAtIndex(int k) {
        return getGraphs().get(k);
    }

    private Graph getSelectedGraph(int i) {
        List<Graph> graphs = getGraphs();

        if (graphs != null && graphs.size() > 0) {
            return graphs.get(i);
        } else {
            return new EdgeListGraph();
        }
    }

    // Sorry, this has to return the selection graph since its used downstream in the interface.
    public Graph getGraph() {
        return getSelectionGraphs(this.params).get(0);
    }

    public void setGraphs(List<Graph> graphs) {
        this.graphs = graphs;

        List<Graph> selectionGraphs = new ArrayList<>();

        for (int i = 0; i < graphs.size(); i++) {
            selectionGraphs.add(new EdgeListGraph());
        }

        setSelectedVariables(new ArrayList<>());
        this.params.set("selectionGraphs", selectionGraphs);

        List<Node> highlighted = (List<Node>) this.params.get("highlightInEditor", new ArrayList<>());
        highlighted.retainAll(getSelectedGraph(0).getNodes());
        this.params.set("highlightInEditor", highlighted);
        List<Node> selected = getSelectedVariables();
        selected.retainAll(getSelectedGraph(0).getNodes());
        setSelectedVariables(selected);

        log();
    }

    public Graph getSelectionGraph(int i) {
        List<Graph> selectionGraphs = (List<Graph>) this.params.get("selectionGraphs", new ArrayList<>());

        if (selectionGraphs == null || selectionGraphs.isEmpty()) {
            for (int j = 0; j < getGraphs().size(); j++) {
                selectionGraphs.add(new EdgeListGraph());
            }

            this.params.set("selectionGraphs", selectionGraphs);
        }

        return selectionGraphs.get(i);
    }

    public Graph getOriginalGraph() {
        return getSelectedGraph(0);
    }

    public void setDialogText(String dialogText) {
        this.params.set("dialogText", dialogText);
    }

    public String getDialogText() {
        return this.params.getString("dialogText", "");
    }

    public Type getType() {
        String graphSelectionType = this.params.getString("graphSelectionType", "subgraph");

        for (Type type : Type.values()) {
            if (type.toString().equals(graphSelectionType)) {
                return type;
            }
        }

        throw new IllegalArgumentException();
    }

    public void setType(Type type) {
        this.params.set("graphSelectionType", type.toString());
    }

    public String getName() {
        return this.params.getString("name", null);
    }

    public void setName(String name) {
        this.params.set("name", name);
    }

    public Graph getSourceGraph() {
        return getSelectedGraph(0);
    }

    public Graph getResultGraph() {
        return (getSelectionGraphs(this.params)).get(0);
    }

    public List<String> getVariableNames() {
        return getSelectedGraph(0).getNodeNames();
    }

    public void setSelectedVariables(List<Node> variables) {
        this.selectedNodes = variables;
    }


    public List<Node> getVariables() {
        return getSelectedGraph(0).getNodes();
    }

    public void setN(int n) {
        if (n < 0) throw new IllegalArgumentException();
        this.params.set("n", n);
    }

    public int getN() {
        return this.params.getInt("n", 0);
    }

    public void setNType(nType NType) {
        this.params.set("nType", NType.toString());
    }

    public String getNType() {
        return this.params.getString("nType", "atLeast");
    }

    public List<Node> getHighlightInEditor() {
        return (List<Node>) this.params.get("highlightInEditor", new ArrayList<Node>());
    }


    //===========================================PRIVATE METHODS====================================//

    // Calculates a graph from give nodes and edges. The nodes are always included in the graph, plus
    // whatever nodes and edges are in the edges set.
    private Graph graphFromEdges(Set<Edge> edges, List<Node> nodes) {
        Graph selectedGraph = new EdgeListGraph(nodes);

        for (Edge edge : edges) {
            if (!selectedGraph.containsNode(edge.getNode1())) selectedGraph.addNode(edge.getNode1());
            if (!selectedGraph.containsNode(edge.getNode2())) selectedGraph.addNode(edge.getNode2());
            selectedGraph.addEdge(edge);
        }

        return selectedGraph;
    }

    // Calculates the Markov blanket of a node in a params.getGraph().
    private Set<Node> mb(Graph graph, Node z) {
        Set<Node> mb = new HashSet<>(graph.getAdjacentNodes(z));

        for (Node c : graph.getChildren(z)) {
            for (Node p : graph.getParents(c)) {
                if (p != z) {
                    mb.add(p);
                }
            }
        }

        return mb;
    }

    private Set<Edge> yStructures(Graph graph, Node z, int i) {
        Set<Edge> edges = new HashSet<>();

        List<Edge> parents = new ArrayList<>();

        for (Node node : graph.getAdjacentNodes(z)) {
            Edge edge = graph.getEdge(node, z);
            if (Edges.isDirectedEdge(edge) && edge.pointsTowards(z)) {
                parents.add(edge);
            }
        }

        List<Node> children = getSelectedGraph(i).getChildren(z);

        if (parents.size() > 1 && children.size() > 0) {
            edges.addAll(parents);

            for (Node node : children) {
                edges.add(getSelectedGraph(i).getEdge(node, z));
            }
        }

        return edges;
    }

    private Set<Edge> pagYStructures(Graph graph, Node z, int i) {
        Set<Edge> edges = new HashSet<>();

        List<Edge> parents = new ArrayList<>();

        for (Node node : graph.getAdjacentNodes(z)) {
            Edge edge = graph.getEdge(node, z);
            if (Edges.isPartiallyOrientedEdge(edge) && edge.pointsTowards(z)) {
                parents.add(edge);
            }
        }

        List<Node> children = getSelectedGraph(i).getChildren(z);

        if (parents.size() > 1 && children.size() > 0) {
            edges.addAll(parents);

            for (Node node : children) {
                edges.add(getSelectedGraph(i).getEdge(node, z));
            }
        }

        return edges;
    }

    private void log() {
        TetradLogger.getInstance().log("info", "General Graph");
    }

    private Set<Edge> getEdgesFromPath(List<Node> path, Graph graph) {
        Set<Edge> edges = new HashSet<>();

        for (int m = 1; m < path.size(); m++) {
            Node n0 = path.get(m - 1);
            Node n1 = path.get(m);
            Edge edge = graph.getEdge(n0, n1);
            if (edge != null) {
                edges.add(edge);
            }
        }

        return edges;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return new MsepTest(getGraph());
    }
}





