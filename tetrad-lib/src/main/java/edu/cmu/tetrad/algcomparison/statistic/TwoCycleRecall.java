package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.algcomparison.statistic.utils.ArrowConfusion;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

/**
 * The 2-cycle recall. This counts 2-cycles manually, wherever they occur in the graphs. The true positives are the
 * number of 2-cycles in both the true and estimated graphs. Thus, if the true contains X-&gt;Y,Y-&gt;X and estimated
 * graph does not contain it, one false negative is counted.
 *
 * @author josephramsey, rubens (November 2016)
 */
public class TwoCycleRecall implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "2CR";
    }

    @Override
    public String getDescription() {
        return "2-cycle recall";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        ArrowConfusion adjConfusion = new ArrowConfusion(trueGraph, estGraph);
        double TwoCycleTp = adjConfusion.getTwoCycleTp();
        double TwoCycleFn = adjConfusion.getTwoCycleFn();
        //        if (recall == 0) recall = Double.NaN;
        return TwoCycleTp / (TwoCycleTp + TwoCycleFn);

    }

    @Override
    public double getNormValue(double value) {
        return value;
    }
}
