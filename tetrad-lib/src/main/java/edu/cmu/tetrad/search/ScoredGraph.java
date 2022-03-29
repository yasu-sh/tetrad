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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.util.TetradSerializable;

/**
 * Stores a graph with a score.
 *
 * @author Joseph Ramsey
 */
public class ScoredGraph implements Comparable, TetradSerializable {
    static final long serialVersionUID = 23L;
    private final Graph graph;
    private final Double score;

    public ScoredGraph(final Graph graph, final Double score) {
        this.graph = graph;
        this.score = score;
    }

    public static ScoredGraph serializableInstance() {
        return new ScoredGraph(new EdgeListGraph(), 0.0);
    }

    public Graph getGraph() {
        return this.graph;
    }

    public double getScore() {
        return this.score;
    }

    public int hashCode() {
        return this.score.hashCode();
    }

    public boolean equals(final Object o) {
        final ScoredGraph _scoredGraph = (ScoredGraph) o;

        if (!this.score.equals(_scoredGraph.getScore())) {
            return false;
        }

        if (!this.graph.equals(_scoredGraph.getGraph())) {
            return false;
        }

        return true;
    }

    public int compareTo(final Object o) {
        final Double thisScore = getScore();
        final Double otherScore = ((ScoredGraph) o).getScore();
        return thisScore.compareTo(otherScore);
    }
}


