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

import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.TetradSerializable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <p>Stores a map from pairs of nodes to separating sets--that is, for each unordered pair of nodes {node1, node2} in
 * a
 * graph, stores a set of nodes conditional on which node1 and node2 are independent (where the nodes are considered as
 * variables) or stores null if the pair was not judged to be independent. (Note that if a sepset is non-null and empty,
 * that should mean that the compared nodes were found to be independent conditional on the empty set, whereas if a
 * sepset is null, that should mean that no set was found yet conditional on which the compared nodes are independent.
 * So at the end of the search, a null sepset carries different information from an empty sepset.)&gt; 0 <p>We cast the
 * variable-like objects to Node to allow them either to be variables explicitly or else to be graph nodes that in some
 * model could be considered as variables. This allows us to use d-separation as a graphical indicator of what
 * independence in models ideally should be.&gt; 0
 *
 * @author josephramsey
 */
public final class SepsetMap implements TetradSerializable {
    static final long serialVersionUID = 23L;

    private Map<Set<Node>, Set<Node>> sepsets = new ConcurrentHashMap<>();
    private Map<Set<Node>, Double> pValues = new ConcurrentHashMap<>();
    private final Map<Node, HashSet<Node>> parents = new HashMap<>();

    //=============================CONSTRUCTORS===========================//

    /**
     * Constructor.
     */
    public SepsetMap() {
    }

    /**
     * Copy constructor.
     *
     * @param map A given sepset map.
     */
    public SepsetMap(SepsetMap map) {
        this.sepsets = new HashMap<>(map.sepsets);
        this.pValues = new HashMap<>(map.pValues);
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SepsetMap serializableInstance() {
        return new SepsetMap();
    }

    //=============================PUBLIC METHODS========================//

    /**
     * Sets the sepset for {x, y} to be z. Note that {x, y} is unordered.
     */
    public void set(Node x, Node y, Set<Node> z) {
        Set<Node> pair = new HashSet<>(2);
        pair.add(x);
        pair.add(y);
        if (z == null) {
            this.sepsets.remove(pair);
        } else {
            this.sepsets.put(pair, z);
        }
    }

    /**
     * Retrieves the sepset previously set for {a, b}, or null if no such set was previously set.
     */
    public Set<Node> get(Node a, Node b) {
        Set<Node> pair = new HashSet<>(2);
        pair.add(a);
        pair.add(b);

        return this.sepsets.get(pair);
    }

    /**
     * Looks up the p-value for {x, y}
     */
    public double getPValue(Node x, Node y) {
        Set<Node> pair = new HashSet<>(2);
        pair.add(x);
        pair.add(y);

        return this.pValues.get(pair);
    }

    /**
     * Sets the parents of x to the (ordered) set z.
     */
    public void set(Node x, LinkedHashSet<Node> z) {
        if (this.parents.get(x) != null) {
            this.parents.get(x).addAll(z);
        } else {
            this.parents.put(x, z);
        }
    }

    /**
     * Returns the parents of the node x.
     */
    public HashSet<Node> get(Node x) {
        return this.parents.get(x) == null ? new HashSet<>() : this.parents.get(x);
    }

    /**
     * Checks equality of this to another sepset map.
     */
    public boolean equals(Object o) {
        if (o == null) {
            return false;
        }

        if (!(o instanceof SepsetMap)) {
            return false;
        }

        SepsetMap _sepset = (SepsetMap) o;
        return this.sepsets.equals(_sepset.sepsets);
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help).
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.sepsets == null) {
            throw new NullPointerException();
        }
    }

    /**
     * Returns the number of {x, y} in the key set of the map.
     */
    public int size() {
        return this.sepsets.keySet().size();
    }

    /**
     * Returns a string representation of this sepset map.
     */
    public String toString() {
        return this.sepsets.toString();
    }

    /**
     * Adds all entries in the given sepset map to the current one.
     */
    public void addAll(SepsetMap newSepsets) {
        this.sepsets.putAll(newSepsets.sepsets);
    }
}





