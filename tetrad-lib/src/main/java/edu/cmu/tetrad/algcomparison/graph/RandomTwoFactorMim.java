package edu.cmu.tetrad.algcomparison.graph;

import edu.cmu.tetrad.data.DataGraphUtils;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a random graph by adding forward edges.
 *
 * @author josephramsey
 */
public class RandomTwoFactorMim implements RandomGraph {
    static final long serialVersionUID = 23L;

    @Override
    public Graph createGraph(Parameters parameters) {
        int numStructuralNodes = parameters.getInt("numStructuralNodes", 3);
        int maxStructuralEdges = parameters.getInt("numStructuralEdges", 3);
        int measurementModelDegree = parameters.getInt("measurementModelDegree", 3);
        int numLatentMeasuredImpureParents = parameters.getInt("latentMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureParents = parameters.getInt("measuredMeasuredImpureParents", 0);
        int numMeasuredMeasuredImpureAssociations = parameters.getInt("measuredMeasuredImpureAssociations", 0);

        return DataGraphUtils.randomBifactorModel(numStructuralNodes, maxStructuralEdges, measurementModelDegree,
                numLatentMeasuredImpureParents, numMeasuredMeasuredImpureParents,
                numMeasuredMeasuredImpureAssociations);
    }

    @Override
    public String getDescription() {
        return "Random two-factor MIM (Multiple Indicator Model)";
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("numStructuralNodes");
        parameters.add("numStructuralEdges");
        parameters.add("measurementModelDegree");
        parameters.add("latentMeasuredImpureParents");
        parameters.add("measuredMeasuredImpureParents");
        parameters.add("measuredMeasuredImpureAssociations");
        return parameters;
    }
}
