package edu.pitt.dbmi.algo.resampling.task;

import edu.cmu.tetrad.algcomparison.algorithm.Algorithm;
import edu.cmu.tetrad.algcomparison.algorithm.MultiDataSetAlgorithm;
import edu.cmu.tetrad.algcomparison.utils.HasKnowledge;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.algo.resampling.GeneralResamplingSearch;

import java.io.PrintStream;
import java.util.List;

/**
 * Mar 19, 2017 9:45:44 PM
 *
 * @author Chirayu (Kong) Wongchokprasitti, PhD
 */
public class GeneralResamplingSearchRunnable implements Runnable {

    private DataSet dataSet = null;

    private List<DataModel> dataSets = null;

    private Algorithm algorithm = null;

    private MultiDataSetAlgorithm multiDataSetAlgorithm = null;

    private final Parameters parameters;

    private final GeneralResamplingSearch resamplingAlgorithmSearch;

    private final boolean verbose;

    /**
     * An initial graph to start from.
     */
    private Graph externalGraph = null;

    /**
     * Specification of forbidden and required edges.
     */
    private IKnowledge knowledge = new Knowledge2();

    private PrintStream out = System.out;

    public GeneralResamplingSearchRunnable(final DataSet dataSet, final Algorithm algorithm, final Parameters parameters,
                                           final GeneralResamplingSearch resamplingAlgorithmSearch, final boolean verbose) {
        this.dataSet = dataSet;
        this.algorithm = algorithm;
        this.parameters = parameters;
        this.resamplingAlgorithmSearch = resamplingAlgorithmSearch;
        this.verbose = verbose;
    }

    public GeneralResamplingSearchRunnable(final List<DataModel> dataSets, final MultiDataSetAlgorithm multiDataSetAlgorithm, final Parameters parameters,
                                           final GeneralResamplingSearch resamplingAlgorithmSearch, final boolean verbose) {
        this.dataSets = dataSets;
        this.multiDataSetAlgorithm = multiDataSetAlgorithm;
        this.parameters = parameters;
        this.resamplingAlgorithmSearch = resamplingAlgorithmSearch;
        this.verbose = verbose;
    }

    /**
     * @return the background knowledge.
     */

    public IKnowledge getKnowledge() {
        return this.knowledge;
    }

    /**
     * Sets the background knowledge.
     *
     * @param knowledge the knowledge object, specifying forbidden and required edges.
     */
    public void setKnowledge(final IKnowledge knowledge) {
        if (knowledge == null)
            throw new NullPointerException();
        this.knowledge = knowledge;
    }

    public Graph getExternalGraph() {
        return this.externalGraph;
    }

    public void setExternalGraph(final Graph externalGraph) {
        this.externalGraph = externalGraph;
    }

    /**
     * Sets the output stream that output (except for log output) should be sent
     * to. By detault System.out.
     */
    public void setOut(final PrintStream out) {
        this.out = out;
    }

    /**
     * @return the output stream that output (except for log output) should be
     * sent to.
     */
    public PrintStream getOut() {
        return this.out;
    }

    @Override
    public void run() {
        //System.out.println("#dataSet rows: " + dataSet.getNumRows());

        final long start;
        final long stop;
        start = System.currentTimeMillis();
        if (this.verbose) {
            this.out.println("thread started ... ");
        }

        Graph graph = null;

        if (this.dataSet != null) {
            if (this.algorithm instanceof HasKnowledge) {
                ((HasKnowledge) this.algorithm).setKnowledge(this.knowledge);
                if (this.verbose) {
                    this.out.println("knowledge being set ... ");
                }
            }
            graph = this.algorithm.search(this.dataSet, this.parameters);
        } else {
            if (this.multiDataSetAlgorithm instanceof HasKnowledge) {
                ((HasKnowledge) this.multiDataSetAlgorithm).setKnowledge(this.knowledge);
                if (this.verbose) {
                    this.out.println("knowledge being set ... ");
                }
            }
            graph = this.multiDataSetAlgorithm.search(this.dataSets, this.parameters);
        }

        graph.getEdges();

        stop = System.currentTimeMillis();
        if (this.verbose) {
            this.out.println("processing time of resampling for a thread was: "
                    + (stop - start) / 1000.0 + " sec");
        }
        this.resamplingAlgorithmSearch.addPAG(graph);
    }

}
