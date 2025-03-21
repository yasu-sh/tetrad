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

package edu.cmu.tetrad.search.utils;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;

import java.text.NumberFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Contains utilities for logging search steps.
 *
 * @author josephramsey
 */
public class LogUtilsSearch {
    public static String edgeOrientedMsg(String reason, Edge edge) {
        return "Orienting edge (" + reason + "): " + edge;
    }

    public static String colliderOrientedMsg(String note, Node x, Node y, Node z) {
        return "Orienting collider (" + note + "): " + x.getName() + " *-> " +
                y.getName() + " <-* " + z.getName();
    }

    public static String colliderOrientedMsg(Node x, Node y, Node z) {
        return "Orienting collider: " + x.getName() + " *-> " +
                y.getName() + " <-* " + z.getName();
    }

    public static String colliderOrientedMsg(Node x, Node y, Node z, Set<Node> sepset) {
        return "Orienting collider: " + x.getName() + " *-&gt; " +
                y.getName() + " <-* " + z.getName() + "\t(Sepset = " + sepset +
                ")";
    }

    public static String determinismDetected(Set<Node> sepset, Node x) {
        return "Determinism detected: " + sepset + " -> " + x.getName();
    }

    public static String independenceFactMsg(Node x, Node y, Set<Node> condSet, double pValue) {
        StringBuilder sb = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        sb.append("Independence accepted: ");
        sb.append(LogUtilsSearch.independenceFact(x, y, condSet));

        if (!Double.isNaN(pValue)) {
            sb.append("\tp = ").append(nf.format(pValue));
        }

        return sb.toString();
    }

    public static String dependenceFactMsg(Node x, Node y, Set<Node> condSet, double pValue) {
        StringBuilder sb = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        sb.append("Dependent: ");
        sb.append(LogUtilsSearch.independenceFact(x, y, condSet));

        if (!Double.isNaN(pValue)) {
            sb.append("\tp = ").append(nf.format(pValue));
        }

        return sb.toString();
    }


    public static String independenceFact(Node x, Node y, Set<Node> condSet) {
        StringBuilder sb = new StringBuilder();

        sb.append(x.getName());
        sb.append(" _||_ ");
        sb.append(y.getName());

        Iterator<Node> it = condSet.iterator();

        if (it.hasNext()) {
            sb.append(" | ");
            sb.append(it.next());
        }

        while (it.hasNext()) {
            sb.append(", ");
            sb.append(it.next());
        }

        return sb.toString();
    }
}





