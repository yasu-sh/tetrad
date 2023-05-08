package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.score.SemBicScorer;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * Difference between the true and estiamted BIC scores.
 *
 * @author josephramsey
 */
public class BicDiff implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "BicDiff";
    }

    @Override
    public String getDescription() {
        return "Difference between the true and estimated BIC scores";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        double _true = SemBicScorer.scoreDag(GraphSearchUtils.dagFromCPDAG(trueGraph), dataModel);
        double est = SemBicScorer.scoreDag(GraphSearchUtils.dagFromCPDAG(estGraph), dataModel);
        return (_true - est);
    }

    @Override
    public double getNormValue(double value) {
        return tanh(value / 1e6);
    }
}

