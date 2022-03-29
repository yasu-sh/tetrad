package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.graph.RandomGraph;
import edu.cmu.tetrad.algcomparison.graph.SingleGraph;
import edu.cmu.tetrad.algcomparison.simulation.Simulation;
import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.MlBayesIm;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;

import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.StatUtils.skewness;
import static java.lang.Math.abs;

/**
 * @author jdramsey
 */
public class SpecialDataClark implements Simulation {
    static final long serialVersionUID = 23L;
    private final RandomGraph randomGraph;
    private BayesPm pm;
    private BayesIm im;
    private List<DataSet> dataSets = new ArrayList<>();
    private List<Graph> graphs = new ArrayList<>();
    private final List<BayesIm> ims = new ArrayList<>();

    public SpecialDataClark(final RandomGraph graph) {
        this.randomGraph = graph;
    }

    @Override
    public void createData(final Parameters parameters, final boolean newModel) {
//        if (!newModel && !dataSets.isEmpty()) return;

        Graph graph = this.randomGraph.createGraph(parameters);

        this.dataSets = new ArrayList<>();
        this.graphs = new ArrayList<>();

        for (int i = 0; i < parameters.getInt("numRuns"); i++) {
            System.out.println("Simulating dataset #" + (i + 1));

            if (parameters.getBoolean("differentGraphs") && i > 0) {
                graph = this.randomGraph.createGraph(parameters);
            }

            this.graphs.add(graph);

            final DataSet dataSet = simulate(graph, parameters);
            dataSet.setName("" + (i + 1));
            this.dataSets.add(dataSet);
        }
    }

    @Override
    public DataModel getDataModel(final int index) {
        return this.dataSets.get(index);
    }


    @Override
    public Graph getTrueGraph(final int index) {
        if (this.graphs.isEmpty()) {
            return new EdgeListGraph();
        } else {
            return this.graphs.get(index);
        }
    }

    @Override
    public String getDescription() {
        return "Bayes net simulation using " + this.randomGraph.getDescription();
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = new ArrayList<>();

        if (!(this.randomGraph instanceof SingleGraph)) {
            parameters.addAll(this.randomGraph.getParameters());
        }

        if (this.pm == null) {
            parameters.addAll(BayesPm.getParameterNames());
        }

        if (this.im == null) {
            parameters.addAll(MlBayesIm.getParameterNames());
        }

        parameters.add("numRuns");
        parameters.add("differentGraphs");
        parameters.add("sampleSize");
        return parameters;
    }

    @Override
    public int getNumDataModels() {
        return this.dataSets.size();
    }

    @Override
    public DataType getDataType() {
        return DataType.Discrete;
    }

    private DataSet simulate(final Graph graph, final Parameters parameters) {
        final int N = parameters.getInt("sampleSize");

        try {

            final GeneralizedSemPm pm = new GeneralizedSemPm(graph);
            final Graph g = pm.getGraph();


            for (final String p : pm.getParameters()) {
                double coef = RandomUtil.getInstance().nextUniform(0.3, 0.6);

                if (RandomUtil.getInstance().nextDouble() < 0.5) {
                    coef *= -1;
                }

                pm.setParameterExpression(p, "" + coef);
            }

            for (final Node x : g.getNodes()) {
                if (!(x.getNodeType() == NodeType.ERROR)) {
                    final String error;

                    final double s = RandomUtil.getInstance().nextUniform(.1, .4);

                    final double f = getF(s, N);

                    if (s > 0) {
                        error = "pow(Uniform(0, 1), " + (1.0 + f) + ")";
                    } else {
                        error = "-pow(Uniform(0, 1), " + (1.0 + f) + ")";
                    }

                    pm.setNodeExpression(pm.getErrorNode(x), error);
                }
            }

            final GeneralizedSemIm im = new GeneralizedSemIm(pm);

//            System.out.println(im);

            return im.simulateData(N, false);
        } catch (final Exception e) {
            throw new IllegalArgumentException("Sorry, I couldn't simulate from that Bayes IM; perhaps not all of\n" +
                    "the parameters have been specified.");
        }
    }

    private double getF(final double s, final int N) {
        double high = 100.0;
        double low = 0.0;

        while (high - low > 1e-10) {
            final double midpoint = (high + low) / 2.0;

            if (skewf(midpoint, N) < s) {
                low = midpoint;
            } else {
                high = midpoint;
            }
        }

        return high;
    }

    private double skewf(final double f, final int N) {
        final double[] s = new double[N];

        for (int i = 0; i < N; i++) {
            s[i] = Math.pow(RandomUtil.getInstance().nextUniform(0, 1), abs((1 + f)));
        }

        return skewness(s);
    }


}
