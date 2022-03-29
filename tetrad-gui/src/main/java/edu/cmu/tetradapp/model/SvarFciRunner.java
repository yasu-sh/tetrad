///////////////////////////////////////////////////////////////////////////////
// For information as to what this class does, see the Javadoc, below.       //
// Copyright (C) 1998, 1999, 2000, 2001, 2002, 2003, 2004, 2005, 2006,       //
// 2007, 2008, 2009, 2010, 2014, 2015 by Peter Spirtes, Richard Scheines, Joseph   //
// Ramsey, and Clark Glymour.                                                //
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

import edu.cmu.tetrad.data.IKnowledge;
import edu.cmu.tetrad.data.Knowledge2;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.search.IndTestType;
import edu.cmu.tetrad.search.IndependenceTest;
import edu.cmu.tetrad.search.SearchGraphUtils;
import edu.cmu.tetrad.search.SvarFci;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;
import edu.cmu.tetradapp.util.IonInput;

import java.util.ArrayList;
import java.util.List;


/**
 * Extends AbstractAlgorithmRunner to produce a wrapper for the FCI algorithm.
 *
 * @author Joseph Ramsey
 * @author Daniel Malinsky
 */
public class SvarFciRunner extends AbstractAlgorithmRunner
        implements IndTestProducer, IonInput {
    static final long serialVersionUID = 23L;
    private IKnowledge knowledge;

    //=========================CONSTRUCTORS================================//

    public SvarFciRunner(DataWrapper dataWrapper, Parameters params) {
        super(dataWrapper, params, null);
    }

    /**
     * Constucts a wrapper for the given EdgeListGraph.
     */
    public SvarFciRunner(GraphSource graphWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graphWrapper.getGraph(), params, knowledgeBoxModel);
    }


    public SvarFciRunner(DataWrapper dataWrapper, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(dataWrapper, params, knowledgeBoxModel);
    }

    public SvarFciRunner(Graph graph, Parameters params) {
        super(graph, params);
    }

    public SvarFciRunner(Graph graph, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(graph, params, knowledgeBoxModel);
    }

    public SvarFciRunner(TimeLagGraphWrapper model, Parameters params/*, KnowledgeBoxModel knowledgeBoxModel*/) {
        super(model.getGraph(), params);
        this.knowledge = model.getKnowledge();
    }

    public SvarFciRunner(GraphWrapper graphWrapper, Parameters params) {
        super(graphWrapper.getGraph(), params);
    }

    public SvarFciRunner(DagWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getDag(), params);
    }

    public SvarFciRunner(SemGraphWrapper dagWrapper, Parameters params) {
        super(dagWrapper.getGraph(), params);
    }

    public SvarFciRunner(IndependenceFactsModel model, Parameters params) {
        super(model, params, null);
    }

    public SvarFciRunner(IndependenceFactsModel model, Parameters params, KnowledgeBoxModel knowledgeBoxModel) {
        super(model, params, knowledgeBoxModel);
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static SvarFciRunner serializableInstance() {
        return new SvarFciRunner(Dag.serializableInstance(), new Parameters());
    }

    //=================PUBLIC METHODS OVERRIDING ABSTRACT=================//

    /**
     * Executes the algorithm, producing (at least) a result workbench. Must be
     * implemented in the extending class.
     */
    public void execute() {
        if (this.knowledge == null) {
            this.knowledge = (IKnowledge) getParams().get("knowledge", new Knowledge2());
        } /*else {knowledge = this.knowledge;}*/
        Parameters searchParams = getParams();

        Parameters params = searchParams;

//            Cfci fciSearch =
//                    new Cfci(getIndependenceTest(), knowledge);
//            fciSearch.setMaxIndegree(params.depth());
//            Graph graph = fciSearch.search();
//
//            if (knowledge.isDefaultToKnowledgeLayout()) {
//                SearchGraphUtils.arrangeByKnowledgeTiers(graph, knowledge);
//            }
//
//            setResultGraph(graph);
        Graph graph;

        if (params.getBoolean("rfciUsed", false)) {
            System.out.println("WARNING: there is no RFCI option for SavarFCI! Just using SvarFCI.");
//            Rfci fci = new Rfci(getIndependenceTest());
            SvarFci fci = new SvarFci(getIndependenceTest());
            fci.setKnowledge(this.knowledge);
            fci.setCompleteRuleSetUsed(true);
            fci.setMaxPathLength(params.getInt("maxReachablePathLength", -1));
            fci.setDepth(params.getInt("depth", -1));
            graph = fci.search();
        } else {
            SvarFci fci = new SvarFci(getIndependenceTest());
            fci.setKnowledge(this.knowledge);
            fci.setCompleteRuleSetUsed(true);
            fci.setPossibleDsepSearchDone(params.getBoolean("possibleDsepDone", true));
            fci.setMaxPathLength(params.getInt("maxReachablePathLength", -1));
            fci.setDepth(params.getInt("depth", -1));
            graph = fci.search();
        }

        if (getSourceGraph() != null) {
            GraphUtils.arrangeBySourceGraph(graph, getSourceGraph());
        } else if (this.knowledge.isDefaultToKnowledgeLayout()) {
            SearchGraphUtils.arrangeByKnowledgeTiers(graph, this.knowledge);
        } else {
            GraphUtils.circleLayout(graph, 200, 200, 150);
        }

        setResultGraph(graph);
    }

    public IndependenceTest getIndependenceTest() {
        Object dataModel = getDataModel();

        if (dataModel == null) {
            dataModel = getSourceGraph();
        }

        Parameters params = getParams();
        IndTestType testType;

        if (getParams() instanceof Parameters) {
            Parameters _params = params;
            testType = (IndTestType) _params.get("indTestType", IndTestType.FISHER_Z);
        } else {
            Parameters _params = params;
            testType = (IndTestType) _params.get("indTestType", IndTestType.FISHER_Z);
        }

        return new IndTestChooser().getTest(dataModel, params, testType);
    }

    public Graph getGraph() {
        return getResultGraph();
    }


    /**
     * @return the names of the triple classifications. Coordinates with
     */
    public List<String> getTriplesClassificationTypes() {
        List<String> names = new ArrayList<>();
//        names.add("Definite ColliderDiscovery");
//        names.add("Definite Noncolliders");
        return names;
    }

    /**
     * @return the list of triples corresponding to <code>getTripleClassificationNames</code>.
     */
    public List<List<Triple>> getTriplesLists(Node node) {
        List<List<Triple>> triplesList = new ArrayList<>();
        Graph graph = getGraph();
//        triplesList.add(DataGraphUtils.getDefiniteCollidersFromGraph(node, graph));
//        triplesList.add(DataGraphUtils.getDefiniteNoncollidersFromGraph(node, graph));
        return triplesList;
    }

    public boolean supportsKnowledge() {
        return true;
    }

    @Override
    public String getAlgorithmName() {
        return "FCI";
    }
}


