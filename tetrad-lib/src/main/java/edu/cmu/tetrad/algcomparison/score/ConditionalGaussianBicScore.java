package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.ConditionalGaussianScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 */
@edu.cmu.tetrad.annotation.Score(
        name = "CG-BIC (Conditional Gaussian BIC Score)",
        command = "cg-bic-score",
        dataType = DataType.Mixed
)
@Mixed
public class ConditionalGaussianBicScore implements ScoreWrapper {

    static final long serialVersionUID = 23L;
    private DataModel dataSet;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        ConditionalGaussianScore conditionalGaussianScore =
                new ConditionalGaussianScore(SimpleDataLoader.getMixedDataSet(dataSet),
                        parameters.getDouble("penaltyDiscount"),
                        parameters.getBoolean("discretize"));
        conditionalGaussianScore.setNumCategoriesToDiscretize(parameters.getInt("numCategoriesToDiscretize"));
        conditionalGaussianScore.setStructurePrior(parameters.getDouble("structurePrior"));
        return conditionalGaussianScore;
    }

    @Override
    public String getDescription() {
        return "Conditional Gaussian BIC Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();

        parameters.add(Params.PENALTY_DISCOUNT);
        parameters.add(Params.STRUCTURE_PRIOR);
        parameters.add(Params.DISCRETIZE);
        parameters.add(Params.NUM_CATEGORIES_TO_DISCRETIZE);
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}
