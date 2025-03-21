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
package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.sem.StandardizedSemIm;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.workbench.GraphWorkbench;
import org.apache.commons.math3.util.FastMath;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.text.NumberFormat;
import java.util.List;

/**
 * Edits the parameters of the SemIm using a graph workbench.
 */
final class StandardizedSemImGraphicalEditor extends JPanel {

    static final long serialVersionUID = 23L;

    /**
     * Font size for parameter values in the graph.
     */
    private static final Font SMALL_FONT = new Font("Dialog", Font.PLAIN, 10);

    /**
     * The SemIM being edited.
     */
    private final StandardizedSemIm semIm;

    /**
     * Workbench for the graphical editor.
     */
    private GraphWorkbench workbench;

    /**
     * Stores the last active edge so that it can be reset properly.
     */
    private Object lastEditedObject;

    /**
     * This delay needs to be restored when the component is hidden.
     */
    private int savedTooltipDelay;

    /**
     * True iff this graphical display is editable.
     */
    private boolean editable = true;

    /**
     * The minimum of the range being edited.
     */
    private double min = Double.NEGATIVE_INFINITY;

    /**
     * The maximum of the range being edited.
     */
    private double max = Double.POSITIVE_INFINITY;

    /**
     * The edge being edited.
     */
    private Edge editingEdge;

    /**
     * The label for the minimum range value.
     */
    private final JLabel minRangeLabel;

    /**
     * The label for the maximum range value.
     */
    private final JLabel maxRangeLabel;

    /**
     * The label that displayed the getModel edge being edited.
     */
    private final JLabel edgeLabel;

    /**
     * The textfield that displays the getModel value of the parameter being
     * edited.
     */
    private final DoubleTextField valueField;

    /**
     * The slider that lets the user select a value within range for the
     * getModel parameter being edited.
     */
    private final JSlider slider;

    private boolean enableEditing = true;

    /**
     * Constructs a SemIm graphical editor for the given SemIm.
     */
    public StandardizedSemImGraphicalEditor(StandardizedSemIm semIm) {
        this.semIm = semIm;

        setLayout(new BorderLayout());
        JScrollPane scroll = new JScrollPane(workbench());
        scroll.setPreferredSize(new Dimension(450, 400));

        add(scroll, BorderLayout.CENTER);

        Box b = Box.createVerticalBox();
        Box b1 = Box.createHorizontalBox();

        this.minRangeLabel = new JLabel();
        this.maxRangeLabel = new JLabel();
        this.edgeLabel = new JLabel();
        this.valueField = new DoubleTextField(0, 8, NumberFormatUtil.getInstance().getNumberFormat());
        this.slider = new JSlider();
        setEditorToEdge(semIm.getSemPm().getGraph().getEdges().iterator().next());

        this.valueField.setFilter((value, oldValue) -> {
            if (value == Double.POSITIVE_INFINITY || value == Double.NEGATIVE_INFINITY || (value < StandardizedSemImGraphicalEditor.this.max && value > StandardizedSemImGraphicalEditor.this.min)) {
                setSliderToValue(value, StandardizedSemImGraphicalEditor.this.min, StandardizedSemImGraphicalEditor.this.max);
                semIm().setParameterValue(StandardizedSemImGraphicalEditor.this.editingEdge, value);
                resetLabels();
                StandardizedSemImGraphicalEditor.this.firePropertyChange("modelChanged", null, null);
                return value;
            } else {
                return oldValue;
            }
        });

        this.valueField.addFocusListener(new FocusAdapter() {
            public void focusLost(FocusEvent e) {
                try {
                    double value = Double.parseDouble(StandardizedSemImGraphicalEditor.this.valueField.getText());
                    semIm.setParameterValue(StandardizedSemImGraphicalEditor.this.editingEdge, value);
                    resetLabels();
                } catch (NumberFormatException e1) {
                    // Do nothing.
                }
            }
        });

        this.slider.addChangeListener(new ChangeListener() {
            public synchronized void stateChanged(ChangeEvent e) {
                double value = getValueFromSlider(StandardizedSemImGraphicalEditor.this.min, StandardizedSemImGraphicalEditor.this.max);
                StandardizedSemImGraphicalEditor.this.valueField.setValue(value);
                resetLabels();
            }
        });

        b1.add(new JLabel("Adjustable range for edge "));
        b1.add(this.edgeLabel);
        b1.add(new JLabel(" = ("));
        b1.add(this.minRangeLabel);
        b1.add(new JLabel(", "));
        b1.add(this.maxRangeLabel);
        b1.add(new JLabel(")"));
        b1.add(Box.createHorizontalGlue());
        b.add(b1);
        b.add(Box.createVerticalStrut(5));

        Box b2 = Box.createHorizontalBox();

        b2.add(this.slider);
        b2.add(Box.createHorizontalStrut(5));
        b2.add(new JLabel("Value ="));
        b2.add(Box.createHorizontalStrut(5));
        this.valueField.setMaximumSize(this.valueField.getPreferredSize());
        b2.add(this.valueField);
        b.add(b2);

        b.setBorder(new EmptyBorder(5, 5, 5, 5));

        add(b, BorderLayout.SOUTH);

        setBorder(new TitledBorder("Click parameter values to edit. (Error variances are not parameters.)"));

        ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
        setSavedTooltipDelay(toolTipManager.getInitialDelay());

        // Laborious code that follows is intended to make sure tooltips come
        // almost immediately within the sem im editor but more slowly outside.
        // Ugh.
        workbench().addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                resetLabels();
                ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                toolTipManager.setInitialDelay(100);
            }

            public void componentHidden(ComponentEvent e) {
                ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                toolTipManager.setInitialDelay(getSavedTooltipDelay());
            }
        });

        workbench().addMouseListener(new MouseAdapter() {
            public void mouseEntered(MouseEvent e) {
                if (workbench().contains(e.getPoint())) {

                    // Commenting out the resetLabels, since it seems to make
                    // people confused when they can't move the mouse away
                    // from the text field they are editing without the
                    // textfield disappearing. jdramsey 3/16/2005.
//                    resetLabels();
                    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(100);
                }
            }

            public void mouseExited(MouseEvent e) {
                if (!workbench().contains(e.getPoint())) {
                    ToolTipManager toolTipManager = ToolTipManager.sharedInstance();
                    toolTipManager.setInitialDelay(getSavedTooltipDelay());
                }
            }
        });

        // Make sure the graphical editor reflects changes made to parameters
        // in other editors.
        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent e) {
                resetLabels();
            }
        });
    }

    //=================================PUBLIC METHODS=======================================//
    public GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    public void setEditable(boolean editable) {
        workbench().setAllowEdgeReorientations(editable);
        workbench().setAllowDoubleClickActions(editable);
        workbench().setAllowNodeEdgeSelection(editable);
        this.editable = editable;
    }

    //========================PRIVATE METHODS===========================//
    private void beginEdgeEdit(Edge edge) {
        finishEdit();

        if (!isEditable()) {
            return;
        }

        setEditorToEdge(edge);

        setLastEditedObject(edge);
        workbench().repaint();
    }

    private void setEditorToEdge(Edge edge) {
        if (edge.getNode1().getNodeType() == NodeType.ERROR || edge.getNode2().getNodeType() == NodeType.ERROR) {
            return;
        }

        if (this.editingEdge != null) {
            try {
                double value = Double.parseDouble(this.valueField.getText());
                this.semIm.setParameterValue(this.editingEdge, value);
                resetLabels();
            } catch (NumberFormatException e) {
                // Do nothing.
            }
        }

        double d = semIm().getParameterValue(edge);

        StandardizedSemIm.ParameterRange range = semIm().getParameterRange(edge);
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        this.editingEdge = edge;
        this.edgeLabel.setText(edge.toString());
        this.min = range.getLow();
        this.max = range.getHigh();
        this.minRangeLabel.setText(nf.format(range.getLow()));
        this.maxRangeLabel.setText(nf.format(range.getHigh()));

        setSliderToValue(d, this.min, this.max);
        this.valueField.setValue(d);
        this.valueField.grabFocus();
        this.valueField.setCaretPosition(0);
        this.valueField.moveCaretPosition(this.valueField.getText().length());
    }

    private void finishEdit() {
        if (lastEditedObject() != null) {
            resetLabels();
        }
    }

    private StandardizedSemIm semIm() {
        return this.semIm;
    }

    private Graph graph() {
        return this.semIm().getSemPm().getGraph();
    }

    private GraphWorkbench workbench() {
        if (getWorkbench() == null) {
            this.workbench = new GraphWorkbench(graph());
            getWorkbench().setAllowDoubleClickActions(false);
            getWorkbench().setAllowEdgeReorientations(false);
            getWorkbench().addPropertyChangeListener(
                    evt -> {
                        if ("BackgroundClicked".equals(
                                evt.getPropertyName())) {
                            finishEdit();
                        }
                    });
            resetLabels();
        }
        return getWorkbench();
    }

    private void setLastEditedObject(Object o) {
        this.lastEditedObject = o;
    }

    private Object lastEditedObject() {
        return this.lastEditedObject;
    }

    public void resetLabels() {
        for (Edge o : graph().getEdges()) {
            resetEdgeLabel(o);
        }

        List<Node> nodes = graph().getNodes();

        for (Node node : nodes) {
            resetNodeLabel(node);
        }

        workbench().repaint();
    }

    private double getValueFromSlider(double c1, double c2) {

        int n = this.slider.getMaximum() - this.slider.getMinimum();
        int slider = this.slider.getValue();

        if (c1 == Double.NEGATIVE_INFINITY && slider == 0) {
            return Double.NEGATIVE_INFINITY;
        }

        if (c2 == Double.POSITIVE_INFINITY && slider == 100) {
            return Double.POSITIVE_INFINITY;
        }

        return sliderToValue(slider, c1, c2, n);
    }

    private void setSliderToValue(double value, double c1, double c2) {
        if (value == Double.NEGATIVE_INFINITY) {
            value = Double.MIN_VALUE;
        } else if (value == Double.POSITIVE_INFINITY) {
            value = Double.MAX_VALUE;
        }

        int n = this.slider.getMaximum() - this.slider.getMinimum();

        int slider = valueToSlider(value, c1, c2, n);
        this.slider.setValue(slider);
    }

    private double sliderToValue(int slider, double min, double max, int n) {
        double f;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            f = min + ((double) slider / n) * (max - min);
        } else if (min != Double.NEGATIVE_INFINITY) {
            f = min + FastMath.tan(((double) slider / n) * (FastMath.PI / 2));
        } else if (max != Double.POSITIVE_INFINITY) {
            f = max + FastMath.tan(-(((double) n - slider) / n) * (FastMath.PI / 2));
//            System.out.println("slider = " + slider + " min = " + min + " max = " + max + "  f = " + f);
        } else {
            f = FastMath.tan(-FastMath.PI / 2 + ((double) slider / n) * FastMath.PI);
        }
        return f;
    }

    private int valueToSlider(double value, double min, double max, int n) {
        double x;
        if (min != Double.NEGATIVE_INFINITY && max != Double.POSITIVE_INFINITY) {
            x = n * (value - min) / (max - min);
        } else if (min != Double.NEGATIVE_INFINITY) {
            x = (2. * n) / FastMath.PI * FastMath.atan(value - min);
        } else if (max != Double.POSITIVE_INFINITY) {
            x = n + (2. * n) / FastMath.PI * FastMath.atan(value - max);
//            System.out.println("value = " + value + " x = " + x);
        } else {
            x = (n / FastMath.PI) * (FastMath.atan(value) + FastMath.PI / 2);
        }

        int slider = (int) FastMath.round(x);
        if (slider > 100) {
            slider = 100;
        }
        if (slider < 0) {
            slider = 0;
        }
        return slider;
    }

    private void resetEdgeLabel(Edge edge) {
        if (semIm().containsParameter(edge)) {
            double val = semIm().getParameterValue(edge);

            JLabel label = new JLabel();

            if (Edges.isBidirectedEdge(edge)) {
                label.setForeground(Color.GREEN.darker().darker());
            }

            label.setBackground(Color.white);
            label.setOpaque(true);
            label.setFont(StandardizedSemImGraphicalEditor.SMALL_FONT);
            label.setText(" " + asString(val) + " ");
            label.addMouseListener(new EdgeMouseListener(edge, this));

            workbench().setEdgeLabel(edge, label);
        } else {
            workbench().setEdgeLabel(edge, null);
        }
    }

    private void resetNodeLabel(Node node) {
        JLabel label = new JLabel();
        label.setBackground(Color.WHITE);
        label.addMouseListener(new NodeMouseListener());
        label.setFont(StandardizedSemImGraphicalEditor.SMALL_FONT);

        NodeType nodeType = node.getNodeType();

        if (nodeType != NodeType.ERROR) {
            return;
        }

        double error = this.semIm.getErrorVariance(node);
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        label.setText(nf.format(error));

        // Offset the nodes slightly differently depending on whether
        // they're error nodes or not.
        label.setOpaque(false);
        workbench().setNodeLabel(node, label, -10, -10);
    }

    private int getSavedTooltipDelay() {
        return this.savedTooltipDelay;
    }

    private void setSavedTooltipDelay(int savedTooltipDelay) {
        if (this.savedTooltipDelay == 0) {
            this.savedTooltipDelay = savedTooltipDelay;
        }
    }

    private String asString(double value) {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        if (Double.isNaN(value)) {
            return " * ";
        } else {
            return nf.format(value);
        }
    }

    private boolean isEditable() {
        return this.editable;
    }

    public boolean isEnableEditing() {
        return this.enableEditing;
    }

    public void enableEditing(boolean enableEditing) {
        this.enableEditing = enableEditing;
        if (this.workbench != null) {
            this.workbench.enableEditing(enableEditing);
        }
    }

    static final class EdgeMouseListener extends MouseAdapter {

        private final Edge edge;
        private final StandardizedSemImGraphicalEditor editor;

        public EdgeMouseListener(Edge edge, StandardizedSemImGraphicalEditor editor) {
            this.edge = edge;
            this.editor = editor;
        }

        private Edge getEdge() {
            return this.edge;
        }

        private StandardizedSemImGraphicalEditor getEditor() {
            return this.editor;
        }

        public void mousePressed(MouseEvent e) {
            getEditor().beginEdgeEdit(getEdge());
        }
    }

    static final class NodeMouseListener extends MouseAdapter {

        public NodeMouseListener() {
        }

        public void mouseClicked(MouseEvent e) {
        }
    }

}
