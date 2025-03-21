package edu.cmu.tetrad.algcomparison.simulation;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.annotation.Experimental;
import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.TimeLagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.graph.LagGraphParams;
import edu.cmu.tetrad.study.gene.tetrad.gene.graph.RandomActiveLagGraph;
import edu.cmu.tetrad.study.gene.tetrad.gene.history.LaggedFactor;
import edu.cmu.tetrad.study.gene.tetradapp.model.BooleanGlassGeneIm;
import edu.cmu.tetrad.study.gene.tetradapp.model.BooleanGlassGenePm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.*;

/**
 * A version of the Lee and Hastic simulation which is guaranteed to generate a discrete data set.
 *
 * @author josephramsey
 */
@Experimental
public class BooleanGlassSimulation implements Simulation {
    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private List<DataSet> dataSets = new ArrayList<>();
    private Graph graph = new EdgeListGraph();

    public BooleanGlassSimulation(RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(Parameters parameters, boolean newModel) {
        if (parameters.getLong(Params.SEED) != -1L) {
            RandomUtil.getInstance().setSeed(parameters.getLong(Params.SEED));
        }

        this.graph = this.randomGraph.createGraph(parameters);

        LagGraphParams params = new LagGraphParams(parameters);

        params.setIndegree(2);
        params.setMlag(1);

        RandomActiveLagGraph _graph = new RandomActiveLagGraph(params);
        BooleanGlassGenePm pm = new BooleanGlassGenePm(_graph);
        BooleanGlassGeneIm im = new BooleanGlassGeneIm(pm, parameters);
        DataModelList data = im.simulateData();

        List<DataSet> dataSets = new ArrayList<>();

        for (DataModel model : data) {
            dataSets.add((DataSet) model);
        }

        this.dataSets = dataSets;

        List<String> factors = new ArrayList<>(_graph.getFactors());

        Map<String, Node> nodes = new HashMap<>();

        for (String factor : factors) {
            nodes.put(factor, new ContinuousVariable(factor));
        }

        TimeLagGraph graph = new TimeLagGraph();
        graph.setMaxLag(_graph.getMaxLag());

        for (String factor : factors) {
            graph.addNode(nodes.get(factor));
        }

        for (String factor : factors) {
            for (Object o : _graph.getParents(factor)) {
                LaggedFactor laggedFactor = (LaggedFactor) o;
                String _factor = laggedFactor.getFactor();
                int lag = laggedFactor.getLag();
                Node node1 = graph.getNode(_factor + ":" + lag);
                Node node2 = graph.getNode(factor);
                graph.addDirectedEdge(node1, node2);
            }
        }

        BooleanGlassSimulation.topToBottomLayout(graph);

        this.graph = graph;
    }

    public static void topToBottomLayout(TimeLagGraph graph) {

        final int xStart = 65;
        final int yStart = 50;
        final int xSpace = 100;
        final int ySpace = 100;
        List<Node> lag0Nodes = graph.getLag0Nodes();

        lag0Nodes.sort(Comparator.comparingInt(Node::getCenterX));

        int x = xStart - xSpace;

        for (Node node : lag0Nodes) {
            x += xSpace;
            int y = yStart - ySpace;
            TimeLagGraph.NodeId id = graph.getNodeId(node);

            for (int lag = graph.getMaxLag(); lag >= 0; lag--) {
                y += ySpace;
                Node _node = graph.getNode(id.getName(), lag);

                if (_node == null) {
                    System.out.println("Couldn't find " + null);
                    continue;
                }

                _node.setCenterX(x);
                _node.setCenterY(y);
            }
        }
    }

    @Override
    public Graph getTrueGraph(int index) {
        return this.graph;
    }

    @Override
    public DataModel getDataModel(int index) {
        return this.dataSets.get(index);
    }

    @Override
    public String getDescription() {
        return "Boolean Glass Simulation " + this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        List<String> parameters = new ArrayList<>();
        parameters.add("lagGraphVarsPerInd");
        parameters.add("lagGraphMlag");
        parameters.add("lagGraphIndegree");
        parameters.add("numDishes");
        parameters.add("includeDishAndChipColumns");
        parameters.add("numChipsPerDish");
        parameters.add("numCellsPerDish");
        parameters.add("stepsGenerated");
        parameters.add("firstStepStored");
        parameters.add("interval");
        parameters.add("rawDataSaved");
        parameters.add("measuredDataSaved");
        parameters.add("initSync");
        parameters.add("antilogCalculated");
        parameters.add("dishDishVariability");
        parameters.add("sampleSampleVariability");
        parameters.add("chipChipVariability");
        parameters.add("pixelDigitalization");
        parameters.add(Params.SEED);

        return parameters;
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

}
