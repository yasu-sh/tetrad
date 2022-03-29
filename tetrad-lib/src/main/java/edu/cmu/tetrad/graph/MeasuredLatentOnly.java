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

package edu.cmu.tetrad.graph;


/**
 * This graph constraint permitting only measured or latent nodes to be added to
 * the graph.
 *
 * @author Joseph Ramsey
 */
public final class MeasuredLatentOnly implements GraphConstraint {
    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS===========================//

    public MeasuredLatentOnly() {

    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static MeasuredLatentOnly serializableInstance() {
        return new MeasuredLatentOnly();
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * @return true.
     */
    public boolean isEdgeAddable(final Edge edge, final Graph graph) {
        return true;
    }

    /**
     * @return true iff the given node is either an observed or a latent node.
     */
    public boolean isNodeAddable(final Node node, final Graph graph) {
        final NodeType type = node.getNodeType();
        return type == NodeType.MEASURED || type == NodeType.LATENT;
    }

    /**
     * @return true;
     */
    public boolean isEdgeRemovable(final Edge edge, final Graph graph) {
        return true;
    }

    /**
     * @return true.
     */
    public boolean isNodeRemovable(final Node node, final Graph graph) {
        return true;
    }

    /**
     * @return a string representation of the constraint.
     */
    public String toString() {
        return "<Measured and latent nodes only.>";
    }
}





