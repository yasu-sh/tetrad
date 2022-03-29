package edu.cmu.tetrad.algcomparison.algorithm.external;

import edu.cmu.tetrad.algcomparison.algorithm.ExternalAlgorithm;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataType;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.pitt.dbmi.data.reader.Delimiter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

/**
 * An API to allow results from external algorithms to be included in a report through the algrorithm
 * comparison tool. This one is for matrix generated by PC in pcalg. See below. This script can generate
 * the files in R.
 * <p>
 * library("MASS");
 * library("pcalg");
 * <p>
 * path<-"/Users/user/tetrad/comparison-final";
 * simulation<-1;
 * <p>
 * subdir<-"pc.solve.confl.TRUE";
 * dir.create(paste(path, "/save/", simulation, "/", subdir, sep=""));
 * <p>
 * for (i in 1:10) {
 * data<-read.table(paste(path, "/save/", simulation, "/data/data.", i, ".txt", sep=""), header=TRUE)
 * n<-nrow(data)
 * C<-cor(data)
 * v<-names(data)
 * suffStat<-list(C = C, n=n)
 * pc.fit<-pc(suffStat=suffStat, indepTest=gaussCItest, alpha=0.001, labels=v,
 * solve.conf=TRUE)
 * A<-as(pc.fit, "amat")
 * name<-paste(path, "/save/", simulation, "/", subdir, "/graph.", i, ".txt", sep="")
 * print(name)
 * write.matrix(A, file=name, sep="\t")
 * }
 *
 * @author jdramsey
 */
public class ExternalAlgorithmPcalgPc extends ExternalAlgorithm {
    static final long serialVersionUID = 23L;
    private final String extDir;
    private String shortDescription = null;

    public ExternalAlgorithmPcalgPc(final String extDir) {
        this.extDir = extDir;
        this.shortDescription = new File(extDir).getName().replace("_", " ");
    }

    public ExternalAlgorithmPcalgPc(final String extDir, final String shortDecription) {
        this.extDir = extDir;
        this.shortDescription = shortDecription;
    }

    /**
     * Reads in the relevant graph from the file (see above) and returns it.
     */
    public Graph search(final DataModel dataSet, final Parameters parameters) {
        final int index = getIndex(dataSet);

        final File file = new File(this.path, "/results/" + this.extDir + "/" + (this.simIndex + 1) + "/graph." + index + ".txt");

        System.out.println(file.getAbsolutePath());

        try {
            final DataSet dataSet2 = DataUtils.loadContinuousData(file, "//", '\"' ,
                    "*", true, Delimiter.TAB);
            System.out.println("Loading graph from " + file.getAbsolutePath());
            final Graph graph = loadGraphPcAlgMatrix(dataSet2);

            GraphUtils.circleLayout(graph, 225, 200, 150);

            return graph;
        } catch (final IOException e) {
            throw new RuntimeException("Couldn't parse graph.");
        }
    }

    /**
     * Returns the CPDAG of the supplied DAG.
     */
    public Graph getComparisonGraph(final Graph graph) {
        return new EdgeListGraph(graph);
    }

    public String getDescription() {
        if (this.shortDescription == null) {
            return "Load data from " + this.path + "/" + this.extDir;
        } else {
            return this.shortDescription;
        }
    }

    @Override
    public DataType getDataType() {
        return DataType.Continuous;
    }

    @Override
    public long getElapsedTime(final DataModel dataSet, final Parameters parameters) {
        final int index = getIndex(dataSet);

        final File file = new File(this.path, "/elapsed/" + this.extDir + "/" + (this.simIndex + 1) + "/graph." + index + ".txt");

        try {
            final BufferedReader r = new BufferedReader(new FileReader(file));
            final String l = r.readLine(); // Skip the first line.
            return Long.parseLong(l);
        } catch (final IOException e) {
            return -99;
        }
    }

    public static Graph loadGraphPcAlgMatrix(final DataSet dataSet) {
        final List<Node> vars = dataSet.getVariables();

        final Graph graph = new EdgeListGraph(vars);

        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < dataSet.getNumColumns(); j++) {
                if (i == j) continue;
                final int g = dataSet.getInt(i, j);
                final int h = dataSet.getInt(j, i);


                if (g == 1 && h == 1 && !graph.isAdjacentTo(vars.get(i), vars.get(j))) {
                    graph.addUndirectedEdge(vars.get(i), vars.get(j)); //
                } else if (g == 1 && h == 0) {
                    graph.addDirectedEdge(vars.get(j), vars.get(i));
                }
            }
        }

        return graph;
    }
}


