package edu.cmu.tetrad.algcomparison.algorithm.other;

import cern.colt.matrix.DoubleMatrix2D;
import cern.colt.matrix.impl.DenseDoubleMatrix2D;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * @author jdramsey
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "GLASSO",
        command = "glasso",
        algoType = AlgType.produce_undirected_graphs,
        dataType = DataType.Continuous
)
@Bootstrapping
@Experimental
public class Glasso implements Algorithm {

    static final long serialVersionUID = 23L;

    public Graph search(final DataModel ds, final Parameters parameters) {
        final DataSet _data = (DataSet) ds;

        for (int j = 0; j < _data.getNumColumns(); j++) {
            for (int i = 0; i < _data.getNumRows(); i++) {
                if (Double.isNaN(_data.getDouble(i, j))) {
                    throw new IllegalArgumentException("Please remove or impute missing values.");
                }
            }
        }

        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            final DoubleMatrix2D cov = new DenseDoubleMatrix2D(DataUtils.getContinuousDataSet(ds)
                    .getCovarianceMatrix().toArray());

            final edu.cmu.tetrad.search.Glasso glasso = new edu.cmu.tetrad.search.Glasso(cov);
            glasso.setMaxit((int) parameters.getInt(Params.MAXIT));
            glasso.setIa(parameters.getBoolean(Params.IA));
            glasso.setIs(parameters.getBoolean(Params.IS));
            glasso.setItr(parameters.getBoolean(Params.ITR));
            glasso.setIpen(parameters.getBoolean(Params.IPEN));
            glasso.setThr(parameters.getDouble(Params.THR));
            glasso.setRhoAllEqual(1.0);

            final edu.cmu.tetrad.search.Glasso.Result result = glasso.search();
            final Matrix wwi = new Matrix(result.getWwi().toArray());

            final List<Node> variables = ds.getVariables();
            final Graph resultGraph = new EdgeListGraph(variables);

            for (int i = 0; i < variables.size(); i++) {
                for (int j = i + 1; j < variables.size(); j++) {
                    if (wwi.get(i, j) != 0.0 && wwi.get(i, j) != 0.0) {
                        resultGraph.addUndirectedEdge(variables.get(i), variables.get(j));
                    }
                }
            }

            return resultGraph;
        } else {
            final Glasso algorithm = new Glasso();

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

    public Graph getComparisonGraph(final Graph graph) {
        return GraphUtils.undirectedGraph(graph);
    }

    public String getDescription() {
        return "GLASSO (Graphical LASSO)";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        final List<String> params = new ArrayList<>();
        params.add(Params.MAXIT);
        params.add(Params.IA);
        params.add(Params.IS);
        params.add(Params.ITR);
        params.add(Params.IPEN);
        params.add(Params.THR);

        params.add(Params.VERBOSE);
        return params;
    }
}
