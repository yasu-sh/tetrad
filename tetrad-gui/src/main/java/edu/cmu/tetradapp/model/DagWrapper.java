///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015, 2022 by Peter Spirtes, Richard        //
// Scheines, Joseph Ramsey, and Clark Glymour.                               //
//                                                                           //
// This program is free software; you can redistribute it and/or modify      //
// it under the terms of the GNU General Public License as published by      //
// the Free Software Foundation; either version 2 of the License, or         //
// (at your option) any later version.                                       //
//                                                                           //
// This program is distributed in the hope that it will be useful,           //
// but WITHOUT ANY WARRANTY; without even the implied warranty of            //
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the             //
// GNU General Public License for more details.                              //
//                                                                           //
// You should have received a copy of the GNU General Public License         //
// along with this program; if not, write to the Free Software               //
// Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA //
///////////////////////////////////////////////////////////////////////////////
package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.data.KnowledgeBoxInput;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.test.MsepTest;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.session.SimulationParamsSource;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds a tetrad dag with all of the constructors necessary for it to serve as
 * a model for the tetrad application.
 *
 * @author josephramsey
 */
public class DagWrapper implements GraphSource, KnowledgeBoxInput, IndTestProducer,
        SimulationParamsSource, MultipleGraphSource {

    static final long serialVersionUID = 23L;
    private int numModels = 1;
    private int modelIndex;
    private String modelSourceName;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private List<Dag> dags;
    private Map<String, String> allParamSettings;
    private Parameters parameters;
    private Dag graph;

    //=============================CONSTRUCTORS==========================//
    public DagWrapper(Dag graph) {
        if (graph == null) {
            throw new NullPointerException("Tetrad dag must not be null.");
        }
        setGraph(graph);
        this.parameters = new Parameters();
        log();
    }

    // Do not, repeat not, get rid of these params. -jdramsey 7/4/2010
    public DagWrapper(Parameters params) {
        this.parameters = params;
        if (params.getString("newGraphInitializationMode", "manual").equals("manual")) {
            setDag(new Dag());
        } else if (params.getString("newGraphInitializationMode", "manual").equals("random")) {
//            RandomUtil.getInstance().setSeed(new Date().getTime());
            setDag(new Dag(edu.cmu.tetradapp.util.GraphUtils.makeRandomGraph(getGraph(), params)));
        }
        log();
    }

    public DagWrapper(GraphSource graphSource, Parameters parameters) {
        if (graphSource instanceof Simulation) {
            Simulation simulation = (Simulation) graphSource;
            List<Graph> graphs = simulation.getGraphs();

            this.dags = new ArrayList<>();

            for (Graph graph : graphs) {
                this.dags.add(new Dag(graph));
            }

            this.numModels = this.dags.size();
            this.modelIndex = 0;
            this.modelSourceName = simulation.getName();
        } else {
            setGraph(new EdgeListGraph(graphSource.getGraph()));
        }

        log();
    }

    public DagWrapper(AbstractAlgorithmRunner wrapper) {
        this(new Dag(wrapper.getResultGraph()));
    }

    public DagWrapper(DataWrapper wrapper) {
        if (wrapper instanceof Simulation) {
            Simulation simulation = (Simulation) wrapper;

            List<Graph> graphs = simulation.getGraphs();

            this.dags = new ArrayList<>();

            for (Graph graph : graphs) {
                this.dags.add(new Dag(graph));
            }

            this.numModels = this.dags.size();
            this.modelIndex = 0;
            this.modelSourceName = simulation.getName();
        } else {
            setGraph(new EdgeListGraph(wrapper.getVariables()));
        }

        LayoutUtil.circleLayout(getGraph(), 200, 200, 150);
    }

    public DagWrapper(BayesPmWrapper wrapper) {
        this(new Dag(wrapper.getBayesPm().getDag()));
    }

    public DagWrapper(BayesImWrapper wrapper) {
        this(new Dag(wrapper.getBayesIm().getBayesPm().getDag()));
    }

    public DagWrapper(BayesEstimatorWrapper wrapper) {
        this(new Dag(wrapper.getEstimatedBayesIm().getBayesPm().getDag()));
    }

    public DagWrapper(CptInvariantUpdaterWrapper wrapper) {
        this(new Dag(wrapper.getBayesUpdater().getManipulatedGraph()));
    }

    public DagWrapper(SemPmWrapper wrapper) {
        this(new Dag(wrapper.getSemPm().getGraph()));
    }

    public DagWrapper(SemImWrapper wrapper) {
        this(new Dag(wrapper.getSemIm().getSemPm().getGraph()));
    }

    public DagWrapper(SemEstimatorWrapper wrapper) {
        this(new Dag(wrapper.getSemEstimator().getEstimatedSem().getSemPm()
                .getGraph()));
    }

    public DagWrapper(RegressionRunner wrapper) {
        this(new Dag(wrapper.getResultGraph()));
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DagWrapper serializableInstance() {
        return new DagWrapper(Dag.serializableInstance());
    }

    //================================PUBLIC METHODS=======================//
    public Graph getDag() {
        return this.dags.get(getModelIndex());
    }

    public void setDag(Dag graph) {
        this.dags = new ArrayList<>();
        this.dags.add(graph);
        log();
    }

    //============================PRIVATE METHODS========================//
    private void log() {
        TetradLogger.getInstance().log("info", "Directed Acyclic Graph (DAG)");
        TetradLogger.getInstance().log("graph", getGraph() + "");
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();
    }

    public Graph getGraph() {
        return getDag();
    }

    @Override
    public IndependenceTest getIndependenceTest() {
        return new MsepTest(getGraph());
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Graph getSourceGraph() {
        return getGraph();
    }

    public Graph getResultGraph() {
        return getGraph();
    }

    public List<String> getVariableNames() {
        return getGraph().getNodeNames();
    }

    public List<Node> getVariables() {
        return getGraph().getNodes();
    }

    @Override
    public Map<String, String> getParamSettings() {
        Map<String, String> paramSettings = new HashMap<>();
        paramSettings.put("# Nodes", Integer.toString(getDag().getNumNodes()));
        paramSettings.put("# Edges", Integer.toString(getDag().getNumEdges()));
        return paramSettings;
    }

    @Override
    public void setAllParamSettings(Map<String, String> paramSettings) {
        this.allParamSettings = paramSettings;
    }

    @Override
    public Map<String, String> getAllParamSettings() {
        return this.allParamSettings;
    }

    public Parameters getParameters() {
        return this.parameters;
    }

    public int getNumModels() {
        return this.numModels;
    }

    public int getModelIndex() {
        return this.modelIndex;
    }

    public String getModelSourceName() {
        return this.modelSourceName;
    }

    public void setModelIndex(int modelIndex) {
        this.modelIndex = modelIndex;
    }

    public void setGraph(Graph graph) {
        this.dags = new ArrayList<>();
        this.dags.add(new Dag(graph));
    }

    public List<Graph> getGraphs() {
        return new ArrayList<>(this.dags);
    }
}
