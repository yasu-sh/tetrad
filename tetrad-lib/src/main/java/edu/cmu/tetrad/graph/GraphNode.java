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
package edu.cmu.tetrad.graph;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * Implements a basic node in a graph--that is, a node that is not itself a variable.
 *
 * @author josephramsey
 * @author Willie Wheeler
 */
public class GraphNode implements Node {

    static final long serialVersionUID = 23L;

    /**
     * The name of the node.
     *
     * @serial
     */
    private String name = "??";

    /**
     * The type of the node.
     *
     * @serial
     * @see edu.cmu.tetrad.graph.NodeType
     */
    private NodeType nodeType = NodeType.MEASURED;

    /**
     * Node variable type (domain, interventional status, interventional value..) of this node variable
     */
    private NodeVariableType nodeVariableType = NodeVariableType.DOMAIN;

    /**
     * The x coordinate of the center of the node.
     *
     * @serial
     */
    private int centerX = -1;

    /**
     * The y coordinate of the center of the node.
     *
     * @serial
     */
    private int centerY = -1;

    /**
     * Fires property change events.
     */
    private transient PropertyChangeSupport pcs;

    private final Map<String, Object> attributes = new HashMap<>();

    //============================CONSTRUCTORS==========================//

    /**
     * Constructs a new Tetrad node with the given (non-null) string.
     */
    public GraphNode(String name) {
        setName(name);
    }

    /**
     * Copy constructor.
     */
    public GraphNode(GraphNode node) {
        this.name = node.name;
        this.nodeType = node.nodeType;
        this.centerX = node.centerX;
        this.centerY = node.centerY;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static GraphNode serializableInstance() {
        return new GraphNode("X");
    }

    //============================PUBLIC METHODS========================//

    /**
     * @return the name of the variable.
     */
    public final String getName() {
        return this.name;
    }

    /**
     * @return the node type.
     * @see edu.cmu.tetrad.graph.NodeType
     */
    public final NodeType getNodeType() {
        return this.nodeType;
    }

    /**
     * Sets the node type.
     *
     * @see edu.cmu.tetrad.graph.NodeType
     */
    public final void setNodeType(NodeType nodeType) {
        if (nodeType == null) {
            throw new NullPointerException("Node type must not be null.");
        }
        this.nodeType = nodeType;
    }

    /**
     * Sets the name of this variable.
     */
    public final void setName(String name) {
        if (name == null) {
            throw new NullPointerException("Name must not be null.");
        }

        String oldName = this.name;
        this.name = name;
        getPcs().firePropertyChange("name", oldName, this.name);
    }

    /**
     * @return the x coordinate of the center of the node.
     */
    public final int getCenterX() {
        return this.centerX;
    }

    /**
     * Sets the x coordinate of the center of this node.
     */
    public final void setCenterX(int centerX) {
        this.centerX = centerX;
    }

    /**
     * @return the y coordinate of the center of the node.
     */
    public final int getCenterY() {
        return this.centerY;
    }

    /**
     * Sets the y coordinate of the center of this node.
     */
    public final void setCenterY(int centerY) {
        this.centerY = centerY;
    }

    /**
     * Sets the (x, y) coordinates of the center of this node.
     */
    public final void setCenter(int centerX, int centerY) {
        this.centerX = centerX;
        this.centerY = centerY;
    }

    /**
     * @return the existing property change support object for this class, if there is one, or else creates a new one
     * and returns that.
     */
    private PropertyChangeSupport getPcs() {
        if (this.pcs == null) {
            this.pcs = new PropertyChangeSupport(this);
        }
        return this.pcs;
    }

    /**
     * Adds a property change listener.
     */
    public final void addPropertyChangeListener(PropertyChangeListener l) {
        getPcs().addPropertyChangeListener(l);
    }

    /**
     * @return the name of the node as its string representation.
     */
    public String toString() {
        return this.name;
    }

    public int hashCode() {
        if (NodeEqualityMode.getEqualityType() == NodeEqualityMode.Type.OBJECT) {
            return super.hashCode();
        } else if (NodeEqualityMode.getEqualityType() == NodeEqualityMode.Type.NAME) {
            return this.getName().hashCode();
        }

        throw new IllegalArgumentException();
    }

    /**
     * Two continuous variables are equal if they have the same name and the same missing value marker.
     */
    public boolean equals(Object o) {
        if (NodeEqualityMode.getEqualityType() == NodeEqualityMode.Type.OBJECT) {
            return o == this;
        } else if (NodeEqualityMode.getEqualityType() == NodeEqualityMode.Type.NAME) {
            return o instanceof GraphNode && getName().equals(((Node) o).getName());
        }

        throw new IllegalStateException();
    }

    public Node like(String name) {
        GraphNode node = new GraphNode(name);
        node.setNodeType(getNodeType());
        return node;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.name == null) {
            throw new NullPointerException();
        }

        if (this.nodeType == null) {
            throw new NullPointerException();
        }
    }

    @Override
    public int compareTo(Node node) {
        String node1 = getName();
        String node2 = node.getName();

        boolean isAlpha1 = Node.ALPHA.matcher(node1).matches();
        boolean isAlpha2 = Node.ALPHA.matcher(node2).matches();
        boolean isAlphaNum1 = Node.ALPHA_NUM.matcher(node1).matches();
        boolean isAlphaNum2 = Node.ALPHA_NUM.matcher(node2).matches();
        boolean isLag1 = Node.LAG.matcher(node1).matches();
        boolean isLag2 = Node.LAG.matcher(node2).matches();

        if (isAlpha1) {
            if (isLag2) {
                return -1;
            }
        } else if (isAlphaNum1) {
            if (isAlphaNum2) {
                String s1 = node1.replaceAll("\\d+", "");
                String s2 = node2.replaceAll("\\d+", "");
                if (s1.equals(s2)) {
                    String n1 = node1.replaceAll("\\D+", "");
                    String n2 = node2.replaceAll("\\D+", "");

                    return Integer.valueOf(n1).compareTo(Integer.valueOf(n2));
                } else {
                    return s1.compareTo(s2);
                }
            } else if (isLag2) {
                return -1;
            }
        } else if (isLag1) {
            if (isAlpha2 || isAlphaNum2) {
                return 1;
            } else if (isLag2) {
                String l1 = node1.replaceAll(":", "");
                String l2 = node2.replaceAll(":", "");
                String s1 = l1.replaceAll("\\d+", "");
                String s2 = l2.replaceAll("\\d+", "");
                if (s1.equals(s2)) {
                    String n1 = l1.replaceAll("\\D+", "");
                    String n2 = l2.replaceAll("\\D+", "");

                    return Integer.valueOf(n1).compareTo(Integer.valueOf(n2));
                } else {
                    return s1.compareTo(s2);
                }
            }
        }

        return node1.compareTo(node2);
    }

    @Override
    public NodeVariableType getNodeVariableType() {
        return this.nodeVariableType;
    }

    @Override
    public void setNodeVariableType(NodeVariableType nodeVariableType) {
        this.nodeVariableType = nodeVariableType;
    }

    @Override
    public Map<String, Object> getAllAttributes() {
        return this.attributes;
    }

    @Override
    public Object getAttribute(String key) {
        return this.attributes.get(key);
    }

    @Override
    public void removeAttribute(String key) {
        this.attributes.remove(key);
    }

    @Override
    public void addAttribute(String key, Object value) {
        this.attributes.put(key, value);
    }

}
