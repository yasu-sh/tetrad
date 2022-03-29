package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.TailConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The arrow precision. This counts arrowheads maniacally, wherever they occur in the graphs.
 * The true positives are the number of arrowheads in both the true and estimated graphs.
 * Thus, if the true contains X*->Y and estimated graph either does not contain an edge from
 * X to Y or else does not contain an arrowhead at X for an edge from X to Y, one false
 * positive is counted. Similarly for false negatives.
 *
 * @author jdramsey
 */
public class TailPrecision implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "TP";
    }

    @Override
    public String getDescription() {
        return "Tail precision";
    }

    @Override
    public double getValue(final Graph trueGraph, final Graph estGraph, final DataModel dataModel) {
        final TailConfusion adjConfusion = new TailConfusion(trueGraph, estGraph);
        final double arrowsTp = adjConfusion.getArrowsTp();
        final double arrowsFp = adjConfusion.getArrowsFp();
        return arrowsTp / (arrowsTp + arrowsFp);
    }

    @Override
    public double getNormValue(final double value) {
        return value;
    }
}
