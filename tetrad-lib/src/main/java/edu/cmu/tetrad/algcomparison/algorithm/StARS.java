package edu.cmu.tetrad.algcomparison.algorithm;

import edu.cmu.tetrad.algcomparison.utils.TakesExternalGraph;
import edu.cmu.tetrad.data.BootstrapSampler;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.Parameters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;

/**
 * StARS
 *
 * @author jdramsey
 */
public class StARS implements Algorithm, TakesExternalGraph {

    static final long serialVersionUID = 23L;
    private final double low;
    private final double high;
    private final String parameter;
    private final Algorithm algorithm;
    private Graph externalGraph = null;
    private DataSet _dataSet;

    public StARS(final Algorithm algorithm, final String parameter, final double low, final double high) {
        if (low >= high) {
            throw new IllegalArgumentException("Must have low < high");
        }
        this.algorithm = algorithm;
        this.low = low;
        this.high = high;
        this.parameter = parameter;
    }

    @Override
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        this._dataSet = (DataSet) dataSet;

//        int numVars = Math.min(50, ((DataSet) dataSet).getNumColumns());
//        int[] cols = new int[numVars];
//        for (int i = 0; i < numVars; i++) cols[i] = i;
        this._dataSet = (DataSet) dataSet;//.subsetColumns(cols);

        final double percentageB = parameters.getDouble("percentSubsampleSize");
        final double tolerance = parameters.getDouble("StARS.tolerance");
        final double beta = parameters.getDouble("StARS.cutoff");
        final int numSubsamples = parameters.getInt("numSubsamples");

        final Parameters _parameters = new Parameters(parameters);

        final List<DataSet> samples = new ArrayList<>();

        for (int i = 0; i < numSubsamples; i++) {
            final BootstrapSampler sampler = new BootstrapSampler();
            sampler.setWithoutReplacements(true);
            samples.add(sampler.sample(this._dataSet, (int) (percentageB * this._dataSet.getNumRows())));
        }

//        double pFrom = low;
//        double pTo = high;
        double maxD = Double.NEGATIVE_INFINITY;
        double _lambda = Double.NaN;

        for (double lambda = this.low; lambda <= this.high; lambda += 0.5) {
            final double D = getD(parameters, this.parameter, lambda, samples, this.algorithm);
            System.out.println("lambda = " + lambda + " D = " + D);

            if (D > maxD && D < beta) {
                maxD = D;
                _lambda = lambda;
            }
        }

//        double D1 = getD(parameters, parameter, low, samples, algorithm);
//        System.out.println("D1 (low) = " + D1);
//        double D2 = getD(parameters, parameter, high, samples, algorithm);
//        System.out.println("D2 (high) = " + D2);
//
//        double lastD;
//        double pMid;
//        double pBest;
//        double bestD;
//
//        if (D1 > D2 && D1 < beta) {
//            lastD = D1;
//            pBest = low;
//            bestD = D1;
//        } else if (D2 > D1 && D2 < beta) {
//            lastD = D2;
//            pBest = high;
//            bestD = D2;
//        } else {
//            lastD = Double.NEGATIVE_INFINITY;
//            pBest = (low + high) / 2.0;
//            bestD = Double.NEGATIVE_INFINITY;
//        }
//
//        System.out.println("lastD = " + lastD);
//
//        while (abs(pFrom - pTo) > tolerance) {
//            pMid = (pFrom + pTo) / 2.0;
//            double D = getD(parameters, parameter, pMid, samples, algorithm);
//            System.out.println("pFrom = " + pFrom + " pTo = " + pTo + " pMid = " + pMid + " D = " + D);
//
//            if (D1 > D2) {
//                if (D > bestD && D < beta) {
//                    pTo = pMid;
//                    pBest = pMid;
//                    bestD = D;
//                } else {
//                    pFrom = pMid;
//                }
//            } else {
//                if (D > bestD && D < beta) {
//                    pFrom = pMid;
//                    pBest = pMid;
//                    bestD = D;
//                } else {
//                    pTo = pMid;
//                }
//            }
//
//            lastD = D;
//            System.out.println("lastD = " + lastD + " pBest = " + pBest);
//        }
//
//        if (D1 > bestD) {
//            pBest = low;
//        }
//
//        if (D2 > bestD) {
//            pBest = high;
//        }
        System.out.println("FINAL: lambda = " + _lambda + " D = " + maxD);

        System.out.println(this.parameter + " = " + getValue(_lambda, parameters));
        _parameters.set(this.parameter, getValue(_lambda, parameters));

        return this.algorithm.search(dataSet, _parameters);
    }

    private static double getD(final Parameters params, final String paramName, final double paramValue, final List<DataSet> samples,
                               final Algorithm algorithm) {
        params.set(paramName, paramValue);

        final List<Graph> graphs = new ArrayList<>();

//        for (DataSet d : samples) {
//            Graph e = GraphUtils.undirectedGraph(algorithm.search(d, params));
//            e = GraphUtils.replaceNodes(e, samples.get(0).getVariables());
//            graphs.add(e);
//        }
        final ForkJoinPool pool = ForkJoinPoolInstance.getInstance().getPool();

        class StabilityAction extends RecursiveAction {

            private final int chunk;
            private final int from;
            private final int to;

            private StabilityAction(final int chunk, final int from, final int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected void compute() {
                if (this.to - this.from <= this.chunk) {
                    for (int s = this.from; s < this.to; s++) {
                        Graph e = algorithm.search(samples.get(s), params);
                        e = GraphUtils.replaceNodes(e, samples.get(0).getVariables());
                        graphs.add(e);
                    }
                } else {
                    final int mid = (this.to + this.from) / 2;

                    final StabilityAction left = new StabilityAction(this.chunk, this.from, mid);
                    final StabilityAction right = new StabilityAction(this.chunk, mid, this.to);

                    left.fork();
                    right.compute();
                    left.join();
                }
            }
        }

        final int chunk = 1;

        pool.invoke(new StabilityAction(chunk, 0, samples.size()));

        final int p = samples.get(0).getNumColumns();
        final List<Node> nodes = graphs.get(0).getNodes();

        double D = 0.0;
        int count = 0;

        for (int i = 0; i < p; i++) {
            for (int j = i + 1; j < p; j++) {
                double theta = 0.0;
                final Node x = nodes.get(i);
                final Node y = nodes.get(j);

                for (int k = 0; k < graphs.size(); k++) {
                    if (graphs.get(k).isAdjacentTo(x, y)) {
                        theta += 1.0;
                    }
                }

                theta /= graphs.size();
                final double xsi = 2 * theta * (1.0 - theta);

//                if (xsi != 0){
                D += xsi;
                count++;
//                }
            }
        }

        D /= (double) count;
        return D;
    }

    private static double getValue(final double value, final Parameters parameters) {
        if (parameters.getBoolean("logScale")) {
            return Math.round(Math.pow(10.0, value) * 1000000000.0) / 1000000000.0;
        } else {
            return Math.round(value * 1000000000.0) / 1000000000.0;
        }
    }

    @Override
    public Graph getComparisonGraph(final Graph graph) {
        return this.algorithm.getComparisonGraph(graph);
    }

    @Override
    public String getDescription() {
        return "StARS for " + this.algorithm.getDescription() + " parameter = " + this.parameter;
    }

    @Override
    public DataType getDataType() {
        return this.algorithm.getDataType();
    }

    @Override
    public List<String> getParameters() {
        final List<String> parameters = this.algorithm.getParameters();
        parameters.add("depth");
        parameters.add("verbose");
        parameters.add("StARS.percentageB");
        parameters.add("StARS.tolerance");
        parameters.add("StARS.cutoff");
        parameters.add("numSubsamples");

        return parameters;
    }

    @Override
    public Graph getExternalGraph() {
        // TODO Auto-generated method stub
        return this.externalGraph;
    }

    @Override
    public void setExternalGraph(final Graph externalGraph) {
        // TODO Auto-generated method stub
        this.externalGraph = externalGraph;
    }

    @Override
    public void setExternalGraph(final Algorithm algorithm) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

}
