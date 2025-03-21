package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.*;

import java.util.List;

import static edu.cmu.tetrad.algcomparison.statistic.LatentCommonAncestorTruePositiveBidirected.existsLatentCommonAncestor;
import static edu.cmu.tetrad.search.utils.GraphSearchUtils.dagToPag;

/**
 * The bidirected true positives.
 *
 * @author josephramsey
 */
public class LatentCommonAncestorFalseNegativeBidirected implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "LCAFNB";
    }

    @Override
    public String getDescription() {
        return "Latent Common Ancestor False Negative Bidirected";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        Graph pag = dagToPag(trueGraph);

        int fn = 0;

        List<Node> nodes = trueGraph.getNodes();

        for (int i = 0; i < nodes.size(); i++) {
            for (int j = i + 1; j < nodes.size(); j++) {
                Node x = nodes.get(i);
                Node y = nodes.get(j);

                if (x.getNodeType() == NodeType.MEASURED && y.getNodeType() == NodeType.MEASURED) {
                    if (existsLatentCommonAncestor(trueGraph, new Edge(x, y, Endpoint.CIRCLE, Endpoint.CIRCLE))) {
                        Edge edge2 = estGraph.getEdge(x, y);

                        if (edge2 == null) continue;

                        if (!(edge2 != null && Edges.isBidirectedEdge(edge2)
                                && existsLatentCommonAncestor(trueGraph, edge2))) {
                            fn++;
                        }
                    }
                }
            }
        }

        return fn;
    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
