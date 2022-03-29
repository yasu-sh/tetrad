package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both
 * the true and estimated graphs.
 *
 * @author jdramsey
 */
public class AdjacencyFN implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AFN";
    }

    @Override
    public String getDescription() {
        return "Adjacency False Negatives";
    }

    @Override
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        final AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        return adjConfusion.getAdjFn();
    }

    @Override
    public double getNormValue(final double value) {
        return value;
    }
}
