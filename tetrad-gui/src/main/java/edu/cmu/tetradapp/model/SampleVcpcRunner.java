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

import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.work_in_progress.SampleVcpc;
import edu.cmu.tetrad.search.test.IndependenceTest;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.search.utils.MeekRules;
import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IndTestType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the PC algorithm.
 *
 * @author josephramsey
 */
public class SampleVcpcRunner extends AbstractAlgorithmRunner
        implements IndTestProducer {
    static final long serialVersionUID = 23L;
    private IndependenceFactsModel independenceFactsModel;
    private Graph trueGraph;
    private SemPm semPm;

    private SemIm semIm;


    private Set<Edge> sVcpcAdjacent;
    private Set<Edge> sVcpcApparent;
    private Set<Edge> sVcpcDefinite;

    //============================CONSTRUCTORS============================//

    /**
     * Constructs a wrapper for the given DataWrapper. The DataWrapper must
     * contain a DataSet that is either a DataSet or a DataSet or a DataList
     * containing either a DataSet or a DataSet as its selected model.
     */
    public SampleVcpcRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }

    public SampleVcpcRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public SampleVcpcRunner(SemImWrapper semImWrapper, Parameters params, DataWrapper dataWrapper) {
        super(dataWrapper, params, null);
        this.semIm = semImWrapper.getSemIm();
    }


    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcRunner(Graph graph, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graph, params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcRunner(GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcRunner(GraphSource graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public SampleVcpcRunner(GraphSource graphWrapper, Parameters params, IndependenceFactsModel model) {
        super(graphWrapper.getGraph(), params);
        this.independenceFactsModel = model;
    }


    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SampleVcpcRunner(GraphSource graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    public SampleVcpcRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    public SampleVcpcRunner(DagWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getDag(), params, knowledgeBoxModel);
    }

    public SampleVcpcRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    public SampleVcpcRunner(SemGraphWrapper dagWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dagWrapper.getGraph(), params, knowledgeBoxModel);
    }

    public SampleVcpcRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params) {
        super(dataWrapper, params, null);
        this.trueGraph = graphWrapper.getGraph();
    }

    public SampleVcpcRunner(DataWrapper dataWrapper, GraphWrapper graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
        this.trueGraph = graphWrapper.getGraph();
    }

    public SampleVcpcRunner(IndependenceFactsModel model, Parameters params) {
        super(model, params, null);
    }

    public SampleVcpcRunner(IndependenceFactsModel model, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static SampleVcpcRunner serializableInstance() {
        return new SampleVcpcRunner(Dag.serializableInstance(), new Parameters());
    }

    //===================PUBLIC METHODS OVERRIDING ABSTRACT================//

    public void execute() {
        Knowledge knowledge = (Knowledge) getParams().get("knowledge", new Knowledge());


        SampleVcpc svcpc = new SampleVcpc(getIndependenceTest());

        svcpc.setKnowledge(knowledge);
        svcpc.setMeekPreventCycles(this.isMeekPreventCycles());
        svcpc.setDepth(getParams().getInt("depth", -1));
        if (this.independenceFactsModel != null) {
            svcpc.setFacts();
        }

        svcpc.setSemIm(this.semIm);

        if (this.semIm != null) {
            svcpc.setSemIm(this.semIm);
        }

        Graph graph = svcpc.search();

        if (getSourceGraph() != null) {
            LayoutUtil.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (knowledge.isDefaultToKnowledgeLayout()) {
            GraphSearchUtils.arrangeByKnowledgeTiers(graph, knowledge);
        } else {
            LayoutUtil.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);
        setSvcpcFields(svcpc);
    }

    //
    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        IndTestType testType = (IndTestType) (getParams()).get("indTestType", IndTestType.FISHER_Z);
        return new IndTestChooser().getTest(dataModel, getParams(), testType);
    }


    public Graph getGraph() {
        return getResultGraph();
    }

    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
        names.add("Ambiguous Triples");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = getGraph();
        triplesList.add(GraphUtils.getAmbiguousTriplesFromGraph(node, graph));
        return triplesList;
    }

    public Set<Edge> getAdj() {
        return new HashSet<>(this.sVcpcAdjacent);
    }

    public Set<Edge> getAppNon() {
        return new HashSet<>(this.sVcpcApparent);
    }

    public Set<Edge> getDefNon() {
        return new HashSet<>(this.sVcpcDefinite);
    }

    public boolean supportsKnowledge() {
        return true;
    }

    public MeekRules getMeekRules() {
        MeekRules meekRules = new MeekRules();
        meekRules.setMeekPreventCycles(this.isMeekPreventCycles());
        meekRules.setKnowledge((Knowledge) getParams().get("knowledge", new Knowledge()));
        return meekRules;
    }

    @Override
    public String getAlgorithmName() {
        return "Sample-VCPC";
    }

    public SemIm getSemIm() {
        return this.semIm;
    }

    public SemPm getSemPm() {
        return this.semPm;
    }
    //========================== Private Methods ===============================//

    private boolean isMeekPreventCycles() {
        Parameters params = getParams();
        if (params instanceof Parameters) {
            return params.getBoolean("MeekPreventCycles", false);
        }
        return false;
    }

    private void setSvcpcFields(SampleVcpc svcpc) {
        this.sVcpcAdjacent = svcpc.getAdjacencies();
        this.sVcpcApparent = svcpc.getApparentNonadjacencies();
        this.sVcpcDefinite = svcpc.getDefiniteNonadjacencies();
        List<Node> sVcpcNodes = getGraph().getNodes();
    }

}


