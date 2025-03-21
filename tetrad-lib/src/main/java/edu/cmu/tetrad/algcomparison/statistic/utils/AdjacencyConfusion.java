package edu.cmu.tetrad.algcomparison.statistic.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Edges;
import edu.cmu.tetrad.graph.Graph;

import java.util.HashSet;
import java.util.Set;

/**
 * A confusion matrix for adjacencies--i.e. TP, FP, TN, FN for counts of adjacencies.
 *
 * @author josephramsey
 */
public class AdjacencyConfusion {
    private int tp;
    private int fp;
    private int fn;
    private final int tn;

    public AdjacencyConfusion(Graph truth, Graph est) {
        this.tp = 0;
        this.fp = 0;
        this.fn = 0;

        Set<Edge> allUnoriented = new HashSet<>();
        for (Edge edge : truth.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (Edge edge : est.getEdges()) {
            allUnoriented.add(Edges.undirectedEdge(edge.getNode1(), edge.getNode2()));
        }

        for (Edge edge : allUnoriented) {
            if (est.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !truth.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.fp++;
            }

            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    !est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.fn++;
            }

            if (truth.isAdjacentTo(edge.getNode1(), edge.getNode2()) &&
                    est.isAdjacentTo(edge.getNode1(), edge.getNode2())) {
                this.tp++;
            }
        }

        int allEdges = truth.getNumNodes() * (truth.getNumNodes() - 1) / 2;

        this.tn = allEdges - this.fn - this.fp - this.fn;
    }

    public int getTp() {
        return this.tp;
    }

    public int getFp() {
        return this.fp;
    }

    public int getFn() {
        return this.fn;
    }

    public int getTn() {
        return this.tn;
    }

}
