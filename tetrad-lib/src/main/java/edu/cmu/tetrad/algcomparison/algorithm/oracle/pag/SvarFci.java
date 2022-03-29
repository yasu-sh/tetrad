package edu.cmu.tetrad.algcomparison.algorithm.oracle.pag;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.independence.IndependenceWrapper;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.algcomparison.utils.TakesIndependenceWrapper;
import edu.cmu.tetrad.annotation.AlgType;
import edu.cmu.tetrad.annotation.Bootstrapping;
import edu.cmu.tetrad.annotation.TimeSeries;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.search.TsDagToPag;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingTest;
import edu.pitt.dbmi.algo.resampling.ResamplingEdgeEnsemble;

import java.util.ArrayList;
import java.util.List;

/**
 * SvarFCI.
 *
 * @author jdramsey
 * @author dmalinsky
 */
@edu.cmu.tetrad.annotation.Algorithm(
        name = "SvarFCI",
        command = "svar-fci",
        algoType = AlgType.allow_latent_common_causes
)
@TimeSeries
@Bootstrapping
public class SvarFci implements Algorithm, HasKnowledge, TakesIndependenceWrapper {

    static final long serialVersionUID = 23L;
    private IndependenceWrapper test;
    private IKnowledge knowledge = null;

    public SvarFci() {
    }

    public SvarFci(final IndependenceWrapper test) {
        this.test = test;
    }

    @Override
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        if (parameters.getInt(Params.NUMBER_RESAMPLING) < 1) {
            if (this.knowledge != null) {
                dataSet.setKnowledge(this.knowledge);
            }
            final edu.cmu.tetrad.search.SvarFci search = new edu.cmu.tetrad.search.SvarFci(this.test.getTest(dataSet, parameters));
            search.setDepth(parameters.getInt(Params.DEPTH));
            search.setKnowledge(this.knowledge);
            search.setVerbose(parameters.getBoolean(Params.VERBOSE));

            return search.search();
        } else {
            final SvarFci svarFci = new SvarFci(this.test);

            final DataSet data = (DataSet) dataSet;
            final GeneralResamplingTest search = new GeneralResamplingTest(data, svarFci, parameters.getInt(Params.NUMBER_RESAMPLING));
            search.setKnowledge(this.knowledge);

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

    @Override
    public Graph getComparisonGraph(final Graph graph) {
        return new TsDagToPag(new EdgeListGraph(graph)).convert();
    }

    public String getDescription() {
        return "SvarFCI (SVAR Fast Causal Inference) using " + this.test.getDescription();
    }

    @Override
    public DataType getDataType() {
        return this.test.getDataType();
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new ArrayList<>();

        parameters.add(Params.VERBOSE);
        return parameters;
    }

    @Override
    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    @Override
    public void setKnowledge(final IKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    @Override
    public IndependenceWrapper getIndependenceWrapper() {
        return this.test;
    }

    @Override
    public void setIndependenceWrapper(final IndependenceWrapper test) {
        this.test = test;
    }
}
