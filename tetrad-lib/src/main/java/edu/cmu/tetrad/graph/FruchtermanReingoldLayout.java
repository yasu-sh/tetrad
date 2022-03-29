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

package edu.cmu.tetrad.graph;


import java.util.*;

/**
 * Lays out a graph by linearly summing repulsive force between all nodes and
 * attractive force between adjacent nodes.
 *
 * @author Joseph Ramsey
 */
public final class FruchtermanReingoldLayout {

    /**
     * The graph being laid out.
     */
    private final Graph graph;

    /**
     * Array of e for the graph. The ith edge is e[i][0]-->e[i][[1].
     */
    private int[][] edges;

    /**
     * The position of each node. The position of the ith node is (pos[i][0],
     * pos[i][1]).
     */
    private double[][] nodePosition;

    /**
     * The disposition of each node. The disposition of the ith node is
     * (disp[i][0], disp[i][1]).
     */
    private double[][] nodeDisposition;

    /**
     * Optimal distance between vertices.
     */
    private double optimalDistance = 100;

    /**
     * Temperature.
     */
    private double temperature;

    /**
     * Leftmost x position to help layout components left to right.
     */
    private double leftmostX = -50.;

    //==============================CONSTRUCTORS===========================//

    public FruchtermanReingoldLayout(final Graph graph) {
        if (graph == null) {
            throw new NullPointerException();
        }

        this.graph = graph;
    }

    //============================PUBLIC METHODS==========================//

    public void doLayout() {
        GraphUtils.circleLayout(this.graph, 300, 300, 200);

        final List<List<Node>> components =
                GraphUtils.connectedComponents(this.graph());

        Collections.sort(components, new Comparator<List<Node>>() {
            public int compare(final List<Node> o1, final List<Node> o2) {
                final int i1 = o1.size();
                final int i2 = o2.size();
                return i2 < i1 ? -1 : i2 == i1 ? 0 : 1;
            }
        });

        for (final List<Node> component1 : components) {
            layoutComponent(component1);
        }
    }

    private void layoutComponent(final List<Node> nodes) {
        final int numNodes = nodes.size();
        this.nodePosition = new double[numNodes][2];
        this.nodeDisposition = new double[numNodes][2];

        for (int i = 0; i < numNodes; i++) {
            final Node node = nodes.get(i);
            nodePosition()[i][0] = node.getCenterX();
            nodePosition()[i][1] = node.getCenterY();

            //pos[i][0] = RandomUtil.nextInt(600);
            //pos[i][1] = RandomUtil.nextInt(600);
        }

        final List<Edge> edges = new ArrayList<>(GraphUtils.undirectedGraph(graph()).getEdges());

        for (final Iterator<Edge> i = edges.iterator(); i.hasNext(); ) {
            final Edge edge = i.next();
            if (!nodes.contains(edge.getNode1()) ||
                    !nodes.contains(edge.getNode2())) {
                i.remove();
            }
        }

        this.edges = new int[edges.size()][2];

        for (int i = 0; i < edges.size(); i++) {
            final Edge edge = edges.get(i);
            final int v = nodes.indexOf(edge.getNode1());
            final int u = nodes.indexOf(edge.getNode2());
            this.edges()[i][0] = v;
            this.edges()[i][1] = u;
        }

        final double avgDegree = 2 * this.graph.getNumEdges() / this.graph.getNumNodes();

        setOptimalDistance(20.0 + 20.0 * avgDegree);
        setTemperature(5.0);

        for (int i = 0; i < numIterations(); i++) {

            // Calculate repulsive forces.
            for (int v = 0; v < numNodes; v++) {
                nodeDisposition()[v][0] = 0.1;
                nodeDisposition()[v][1] = 0.1;

                for (int u = 0; u < numNodes; u++) {
                    final double deltaX = nodePosition()[u][0] - nodePosition()[v][0];
                    final double deltaY = nodePosition()[u][1] - nodePosition()[v][1];

                    double norm = norm(deltaX, deltaY);

                    if (norm == 0.0) {
                        norm = 0.1;
//                        continue;
                    }
//
//                    if (norm > 4.0 * getOptimalDistance()) {
//                        continue;
//                    }

                    final double repulsiveForce = fr(norm);

                    nodeDisposition()[v][0] += (deltaX / norm) * repulsiveForce;
                    nodeDisposition()[v][1] += (deltaY / norm) * repulsiveForce;
                }
            }

            // Calculate attractive forces.
            for (int j = 0; j < edges.size(); j++) {
                final int u = this.edges()[j][0];
                final int v = this.edges()[j][1];

                final double deltaX = nodePosition()[v][0] - nodePosition()[u][0];
                final double deltaY = nodePosition()[v][1] - nodePosition()[u][1];

                double norm = norm(deltaX, deltaY);

                if (norm == 0.0) {
                    norm = 0.1;
//                    continue;
                }

//                if (norm < 1.5 * getOptimalDistance()) {
//                    continue;
//                }

                final double attractiveForce = fa(norm);
                final double attractX = (deltaX / norm) * attractiveForce;
                final double attractY = (deltaY / norm) * attractiveForce;

                nodeDisposition()[v][0] -= attractX;
                nodeDisposition()[v][1] -= attractY;

                if (Double.isNaN(nodeDisposition()[v][0]) ||
                        Double.isNaN(nodeDisposition()[v][1])) {
                    throw new IllegalStateException("Undefined disposition.");
                }

                nodeDisposition()[u][0] += attractX;
                nodeDisposition()[u][1] += attractY;

                if (Double.isNaN(nodeDisposition()[u][0]) ||
                        Double.isNaN(nodeDisposition()[u][1])) {
                    throw new IllegalStateException("Undefined disposition.");
                }
            }

            for (int v = 0; v < numNodes; v++) {
                final double norm = norm(nodeDisposition()[v][0], nodeDisposition()[v][1]);

//                if (norm == 0.0) {
//                    continue;
//                }

                nodePosition()[v][0] += (nodeDisposition()[v][0] / norm) *
                        Math.min(norm, getTemperature());
                nodePosition()[v][1] += (nodeDisposition()[v][1] / norm) *
                        Math.min(norm, getTemperature());

                if (Double.isNaN(nodePosition()[v][0]) ||
                        Double.isNaN(nodePosition()[v][1])) {
                    throw new IllegalStateException("Undefined position.");
                }
            }
        }

        shiftComponentToRight(nodes);
    }

    private void shiftComponentToRight(final List<Node> componentNodes) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;

        for (int i = 0; i < componentNodes.size(); i++) {
            if (nodePosition()[i][0] < minX) {
                minX = nodePosition()[i][0];
            }
            if (nodePosition()[i][1] < minY) {
                minY = nodePosition()[i][1];
            }
        }

        this.leftmostX = leftmostX() + 100.;

        for (int i = 0; i < componentNodes.size(); i++) {
            nodePosition()[i][0] += leftmostX() - minX;
            nodePosition()[i][1] += 40.0 - minY;
        }

        for (int i = 0; i < componentNodes.size(); i++) {
            if (nodePosition()[i][0] > leftmostX()) {
                this.leftmostX = nodePosition()[i][0];
            }
        }

        for (int i = 0; i < componentNodes.size(); i++) {
            final Node node = componentNodes.get(i);
            node.setCenterX((int) nodePosition()[i][0]);
            node.setCenterY((int) nodePosition()[i][1]);
        }
    }

    //============================PRIVATE METHODS=========================//  \

    private double fa(final double d) {
        return (d * d) / getOptimalDistance();
    }

    private double fr(final double d) {
        return -(getOptimalDistance() * getOptimalDistance()) / d;
    }

    private double norm(final double x, final double y) {
        return Math.sqrt(x * x + y * y);
    }

    private Graph graph() {
        return this.graph;
    }

    private int[][] edges() {
        return this.edges;
    }

    private double[][] nodePosition() {
        return this.nodePosition;
    }

    private double[][] nodeDisposition() {
        return this.nodeDisposition;
    }

    private int numIterations() {
        return 500;
    }

    private double leftmostX() {
        return this.leftmostX;
    }

    private double getOptimalDistance() {
        return this.optimalDistance;
    }

    private void setOptimalDistance(final double optimalDistance) {
        this.optimalDistance = optimalDistance;
    }

    private double getTemperature() {
        return this.temperature;
    }

    private void setTemperature(final double temperature) {
        this.temperature = temperature;
    }
}





