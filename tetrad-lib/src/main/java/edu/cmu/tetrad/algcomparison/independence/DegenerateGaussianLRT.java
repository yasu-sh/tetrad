package edu.cmu.tetrad.algcomparison.independence;

import edu.cmu.tetrad.annotation.Mixed;
import edu.cmu.tetrad.annotation.TestOfIndependence;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.SimpleDataLoader;
import edu.cmu.tetrad.search.test.IndTestDegenerateGaussianLrt;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

import java.util.ArrayList;
import java.util.List;

/**
 * Wrapper for DG LRT.
 *
 * @author bandrews
 */
@TestOfIndependence(
        name = "DG-LRT (Degenerate Gaussian Likelihood Ratio Test)",
        command = "dg-lr-test",
        dataType = DataType.Mixed
)
@Mixed
public class DegenerateGaussianLRT implements IndependenceWrapper {

    static final long serialVersionUID = 23L;

    @Override
    public IndependenceTest getTest(DataModel dataSet, Parameters parameters) {
        IndTestDegenerateGaussianLrt test = new IndTestDegenerateGaussianLrt(SimpleDataLoader.getMixedDataSet(dataSet));
        test.setAlpha(parameters.getDouble(Params.ALPHA));
        return test;
    }

    @Override
    public String getDescription() {
        return "Degenerate Gaussian Likelihood Ratio Test";
    }

    @Override
    public DataType getDataType() {
        return DataType.Mixed;
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add(Params.ALPHA);
        return parameters;
    }

}
