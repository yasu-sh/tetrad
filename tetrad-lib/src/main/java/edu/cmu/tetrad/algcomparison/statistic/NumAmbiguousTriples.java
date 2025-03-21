package edu.cmu.tetrad.algcomparison.statistic;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;

import static org.apache.commons.math3.util.FastMath.tanh;

/**
 * The adjacency precision. The true positives are the number of adjacencies in both the true and estimated graphs.
 *
 * @author josephramsey
 */
public class NumAmbiguousTriples implements Statistic {
    static final long serialVersionUID = 23L;

    @Override
    public String getAbbreviation() {
        return "AMB";
    }

    @Override
    public String getDescription() {
        return "Number of Ambiguous Triples";
    }

    @Override
    public double getValue(Graph trueGraph, Graph estGraph, DataModel dataModel) {
        return estGraph.getAmbiguousTriples().size();
    }

    @Override
    public double getNormValue(double value) {
        return 1 - tanh(value);
    }
}
