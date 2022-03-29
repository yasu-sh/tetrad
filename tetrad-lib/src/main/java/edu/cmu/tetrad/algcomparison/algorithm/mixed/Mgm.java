package edu.cmu.tetrad.algcomparison.algorithm.mixed;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.csb.mgm.MGM;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "MGM",
        command = "mgm",
        algoType = AlgType.produce_undirected_graphs
)
@Bootstrapping
public class Mgm implements Algorithm {

    static final long serialVersionUID = 23L;

    public Mgm() {
    }

    @Override
    public Graph search(final DataModel ds, final Parameters parameters) {
        final DataSet _data = (DataSet) ds;

        for (int j = 0; j < _data.getNumColumns(); j++) {
            for (int i = 0; i < _data.getNumRows(); i++) {
                if (ds.getVariables().get(j) instanceof ContinuousVariable) {
                    if (Double.isNaN(_data.getDouble(i, j))) {
                        throw new IllegalArgumentException("Please remove or impute missing values.");
                    }
                } else if (ds.getVariables().get(j) instanceof DiscreteVariable) {
                    if (_data.getDouble(i, j) == -99) {
                        throw new IllegalArgumentException("Please remove or impute missing values.");
                    }
                }
            }
        }

        // Notify the user that you need at least one continuous and one discrete variable to run MGM
        final List<Node> variables = ds.getVariables();
        boolean hasContinuous = false;
        boolean hasDiscrete = false;

        for (final Node node : variables) {
            if (node instanceof ContinuousVariable) {
                hasContinuous = true;
            }

            if (node instanceof DiscreteVariable) {
                hasDiscrete = true;
            }
        }

        if (!hasContinuous || !hasDiscrete) {
            throw new IllegalArgumentException("You need at least one continuous and one discrete variable to run MGM.");
        }

        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            final DataSet _ds = DataUtils.getMixedDataSet(ds);

            final double mgmParam1 = parameters.getDouble(Params.MGM_PARAM1);
            final double mgmParam2 = parameters.getDouble(Params.MGM_PARAM2);
            final double mgmParam3 = parameters.getDouble(Params.MGM_PARAM3);

            final double[] lambda = {
                    mgmParam1,
                    mgmParam2,
                    mgmParam3
            };

            final MGM m = new MGM(_ds, lambda);

            return m.search();
        } else {
            final Mgm algorithm = new Mgm();

            final DataSet data = (DataSet) ds;
            final GeneralResamplingTest search = new GeneralResamplingTest(data, algorithm, parameters.getInt(Params.NUMBER_RESAMPLING));

            search.setPercentResampleSize(parameters.getDouble(Params.PERCENT_RESAMPLE_SIZE));
            search.setResamplingWithReplacement(parameters.getBoolean(Params.RESAMPLING_WITH_REPLACEMENT));

            ResamplingEdgeEnsemble edgeEnsemble = ResamplingEdgeEnsemble.Highest;
            switch (parameters.getInt(Params.RESAMPLING_ENSEMBLE, 1)) {
                case 0:
                    edgeEnsemble = ResamplingEdgeEnsemble.Preserved;
                    break;
                case 1:
                    edgeEnsemble = ResamplingEdgeEnsemble.Highest;
                    break;
                case 2:
                    edgeEnsemble = ResamplingEdgeEnsemble.Majority;
            }
            search.setEdgeEnsemble(edgeEnsemble);
            search.setAddOriginalDataset(parameters.getBoolean(Params.ADD_ORIGINAL_DATASET));

            search.setParameters(parameters);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));
            return search.search();
        }
    }

    // Need to marry the parents on this.
    @Override
    public Graph getComparisonGraph(final Graph graph) {
        return GraphUtils.undirectedGraph(graph);
    }

    @Override
    public String getDescription() {
        return "Returns the output of the MGM (Mixed Graphical Model) algorithm (a Markov random field)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new ArrayList<>();
        parameters.add(Params.MGM_PARAM1);
        parameters.add(Params.MGM_PARAM2);
        parameters.add(Params.MGM_PARAM3);
        parameters.add(Params.VERBOSE);
        return parameters;
    }
}
