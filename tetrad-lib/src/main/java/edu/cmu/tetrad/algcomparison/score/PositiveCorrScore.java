package edu.cmu.tetrad.algcomparison.score;

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.search.score.IndTestScore;
import edu.cmu.tetrad.search.score.Score;
import edu.cmu.tetrad.search.work_in_progress.IndTestPositiveCorr;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for Fisher Z test.
 *
 * @author josephramsey
 */
public class PositiveCorrScore implements ScoreWrapper {
    static final long serialVersionUID = 23L;
    private DataModel dataSet;
    double alpha = 0.001;

    @Override
    public Score getScore(DataModel dataSet, Parameters parameters) {
        this.dataSet = dataSet;
        double alpha = parameters.getDouble("alpha");
        this.alpha = alpha;
        IndTestPositiveCorr test = new IndTestPositiveCorr((DataSet) dataSet, alpha);
        return new IndTestScore(test);
    }

    @Override
    public String getDescription() {
        return "Fisher Z Score";
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("alpha");
        return parameters;
    }

    @Override
    public Node getVariable(String name) {
        return this.dataSet.getVariable(name);
    }
}
