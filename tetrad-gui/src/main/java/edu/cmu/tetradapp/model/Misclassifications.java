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

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.MisclassificationUtils;
import edu.cmu.tetrad.search.utils.GraphSearchUtils;
import edu.cmu.tetrad.session.DoNotAddOldModel;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;

import java.io.IOException;
import java.io.ObjectInputStream;

import static edu.cmu.tetrad.search.utils.GraphSearchUtils.dagToPag;


/**
 * Compares a target workbench with a reference workbench by counting errors of
 * omission and commission.  (for edge presence only, not orientation).
 *
 * @author josephramsey
 * @author Erin Korber (added remove latents functionality July 2004)
 */
public final class Misclassifications implements SessionModel, DoNotAddOldModel {
    static final long serialVersionUID = 23L;

    private final Graph targetGraph;
    private final Graph referenceGraph;
    private final Parameters params;
    private String name;


    //=============================CONSTRUCTORS==========================//

    /**
     * Compares the results of a PC to a reference workbench by counting errors
     * of omission and commission. The counts can be retrieved using the methods
     * <code>countOmissionErrors</code> and <code>countCommissionErrors</code>.
     */
    public Misclassifications(GraphSource model1, GraphSource model2, Parameters params) {
        if (params == null) {
            throw new NullPointerException("Parameters must not be null");
        }

        if (model1 == null || model2 == null) {
            throw new NullPointerException("Null graph source>");
        }

        this.params = params;

        String referenceName = params.getString("referenceGraphName", null);

        String model1Name = model1.getName();
        String model2Name = model2.getName();

        if (referenceName.equals(model1Name)) {
            this.referenceGraph = model1.getGraph();
            this.targetGraph = model2.getGraph();
        } else if (referenceName.equals(model2Name)) {
            this.referenceGraph = model2.getGraph();
            this.targetGraph = model1.getGraph();
        } else {
            this.referenceGraph = model1.getGraph();
            this.targetGraph = model2.getGraph();
        }

        TetradLogger.getInstance().log("info", "Graph Comparison");

    }

    //==============================PUBLIC METHODS========================//

    public DataSet getDataSet() {
        return (DataSet) this.params.get("dataSet", null);
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getComparisonString() {
        String refName = getParams().getString("referenceGraphName", null);
        String targetName = getParams().getString("targetGraphName", null);


        Graph comparisonGraph = getComparisonGraph(referenceGraph, params);

        return "True graph from " + refName + "\nTarget graph from " + targetName +
                "\n\n\n" +
                "Edge Misclassification Table:" +
                "\n" +
                MisclassificationUtils.edgeMisclassifications(targetGraph, comparisonGraph) +
                "\n\n" +
                "Endpoint Misclassification Table:" +
                "\n\n" +
                MisclassificationUtils.endpointMisclassification(targetGraph, comparisonGraph);
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

    public Parameters getParams() {
        return this.params;
    }

    public static Graph getComparisonGraph(Graph graph, Parameters params) {
        String type = params.getString("graphComparisonType");

        if ("DAG".equals(type)) {
            params.set("graphComparisonType", "DAG");
            return new EdgeListGraph(graph);
        } else if ("CPDAG".equals(type)) {
            params.set("graphComparisonType", "CPDAG");
            return GraphSearchUtils.cpdagForDag(graph);
        } else if ("PAG".equals(type)) {
            params.set("graphComparisonType", "PAG");
            return dagToPag(graph);
        } else {
            params.set("graphComparisonType", "DAG");
            return new EdgeListGraph(graph);
        }
    }

    public Graph getReferenceGraph() {
        return this.referenceGraph;
    }
}


