package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.AdjacencyConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * Calculates the Matthew's correlation coefficient for adjacencies. See this page in
 * Wikipedia:
 * </p>
 * https://en.wikipedia.org/wiki/Matthews_correlation_coefficient
 * </p>
 * We calculate the correlation directly from the confusion matrix.
 *
 * @author jdramsey
 */
public class MathewsCorrAdj implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "McAdj";
    }

    @Override
    public String getDescription() {
        return "Matthew's correlation coefficient for adjacencies";
    }

    @Override
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        final AdjacencyConfusion adjConfusion = new AdjacencyConfusion(trueGraph, estGraph);
        final int adjTp = adjConfusion.getAdjTp();
        final int adjFp = adjConfusion.getAdjFp();
        final int adjFn = adjConfusion.getAdjFn();
        final int adjTn = adjConfusion.getAdjTn();
        return mcc(adjTp, adjFp, adjTn, adjFn);
    }

    @Override
    public double getNormValue(final double value) {
        return 0.5 + 0.5 * value;
    }

    private double mcc(final double adjTp, final double adjFp, final double adjTn, final double adjFn) {
        final double a = adjTp * adjTn - adjFp * adjFn;
        double b = (adjTp + adjFp) * (adjTp + adjFn) * (adjTn + adjFp) * (adjTn + adjFn);

        if (b == 0) b = 1;

        return a / Math.sqrt(b);
    }
}
