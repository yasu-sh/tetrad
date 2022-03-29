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

package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.calculator.expression.*;
import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.workbench.LayoutUtils;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.*;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Editor to use for the calculator.
 *
 * @author Tyler Gibson
 */
public class CalculatorEditor extends JPanel implements FinalizingParameterEditor {


    /**
     * The calculator's params.
     */
    private Parameters params;


    /**
     * The dataset the calculator is working on.
     */
    private DataSet dataSet;


    /**
     * List of all editors.
     */
    private final List<ExpressionEditor> editors = new LinkedList<>();


    /**
     * The editor last in focus.
     */
    private ExpressionEditor focused;


    /**
     * The variable list.
     */
    private JList variableList;


    /**
     * Panel that contains the expressions.
     */
    private JPanel expressionsPanel;


    /**
     * States whether the dialog was canceled.
     */
    private boolean canceled;


    /**
     * Empty constructor required for Parameter Editors.
     */
    public CalculatorEditor() {
//        super(JOptionUtils.getCenteringFrame(), "Calculator", true);
//        this.addWindowListener(new WindowAdapter() {
//            public void windowClosing(WindowEvent e) {
//                canceled = true;
//            }
//        });
    }

    //================================= Public Methods ============================//


    /**
     * @return the data set that the editor is working with.
     */
    public DataSet getDataSet() {
        return this.dataSet;
    }


    /**
     * Sets the calculator's params.
     *
     * @param params
     */
    public void setParams(final Parameters params) {
        this.params = params;
    }


    /**
     * Grabs the data set that the calculator is working on.
     */
    public void setParentModels(final Object[] parentModels) {
        if (parentModels == null || parentModels.length == 0) {
            throw new IllegalArgumentException("There must be parent model");
        }
        DataWrapper data = null;
        for (final Object parent : parentModels) {
            if (parent instanceof DataWrapper) {
                data = (DataWrapper) parent;
            }
        }
        if (data == null) {
            throw new IllegalArgumentException("Should have have a data wrapper as a parent");
        }
        final DataModel model = data.getSelectedDataModel();
        if (!(model instanceof DataSet)) {
            throw new IllegalArgumentException("The data must be tabular");
        }
        this.dataSet = (DataSet) model;
    }

    /**
     * Builds the gui.
     */
    public void setup() {
//        getContentPane().setLayout(new BorderLayout());
        final JComponent calculator = buildCalculator();

//        JButton close = new JButton("Close");
//        close.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                canceled = true;
//                setVisible(false);
//                dispose();
//            }
//        });
//
//        JButton save = new JButton("Save");
//        save.addActionListener(new ActionListener() {
//            public void actionPerformed(ActionEvent e) {
//                List<Equation> eqs = parseEquations();
//                if (eqs != null) {
//                    setVisible(false);
//                    dispose();
//                }
//            }
//        });

        final Box box = Box.createVerticalBox();
        box.setBorder(new EmptyBorder(5, 5, 5, 5));

//        Box buttonBox = Box.createHorizontalBox();
//        buttonBox.add(Box.createHorizontalGlue());
//        buttonBox.add(save);
//        buttonBox.add(Box.createHorizontalStrut(5));
//        buttonBox.add(close);
//        buttonBox.add(Box.createHorizontalGlue());

        box.add(calculator);
//        box.add(Box.createVerticalStrut(10));
//        box.add(buttonBox);

        add(box);

//        getContentPane().add(box, BorderLayout.CENTER);
//        pack();
//        setLocation(this);
//        setVisible(true);
    }

    /**
     * @return true.
     */
    public boolean mustBeShown() {
        return false;
    }


    public boolean finalizeEdit() {
        if (this.canceled) {
            return false;
        }
        final List<Equation> equations = parseEquations();
        if (equations == null) {
            return false;
        }

        final StringBuilder buf = new StringBuilder();

        for (int i = 0; i < equations.size(); i++) {
            final Equation eq = equations.get(i);

            buf.append(eq.getUnparsedExpression());

            if (i < equations.size() - 1) {
                buf.append("///");
            }
        }

        this.params.set("calculator_equations", buf.toString());

        if (this.params != null) {
            final List<String> _equations = new ArrayList<>();

            for (final Equation eq : equations) {
                _equations.add(eq.getUnparsedExpression());
//                this.params.addEquation(eq.getUnparsedExpression());
            }

            this.params.set("equations", _equations);
        }

        return true;
    }

    //=============================== Private Methods ===================================//


    /**
     * Tries to parse the equations in the editor, if there is a parse exception
     * then a message is displayed stating the error and null is returned. Otherwise
     * the parsed equations are returned.
     */
    private List<Equation> parseEquations() {
        final List<Equation> equations = new ArrayList<>();
        for (final ExpressionEditor editor : this.editors) {
            try {
                System.out.println(editor.getEquation());
            } catch (final ParseException e) {
                e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
            }

            try {
                equations.add(editor.getEquation());
            } catch (final ParseException e) {
                final String s = e.getMessage();

                if (!"".equals(s)) {
                    JOptionPane.showMessageDialog(this, s);
                } else {
                    JOptionPane.showMessageDialog(this, "Could not parse " +
                            "equations.");
                }

                e.printStackTrace();
                return null;
            }
        }
        return equations;
    }


    /**
     * Sets the location of the window to the middle of the screen
     *
     * @param window - component whos location is set.
     */
    private static void setLocation(final Window window) {
        final Dimension screenDim = Toolkit.getDefaultToolkit().getScreenSize();
        final Rectangle bounds = window.getBounds();
        window.setLocation((screenDim.width - bounds.width) / 2,
                (screenDim.height - bounds.height) / 2);
    }


    /**
     * Builds the GUI.
     */
    private JComponent buildCalculator() {
        final Box mainBox = Box.createHorizontalBox();

        // create variable box.
        final Box varBox = Box.createVerticalBox();
        varBox.add(CalculatorEditor.createLabel("Variables:"));
        varBox.add(createVariableList());
        varBox.add(Box.createVerticalGlue());

        // create expression box.
        final Box expressionBox = Box.createVerticalBox();
        final Box equationEditors = Box.createVerticalBox();
        equationEditors.setBorder(new TitledBorder("Edit Expressions"));

        final JPanel editors1 = initializeExpressionEditors();
        final JScrollPane pane = new JScrollPane(editors1);
        pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        LayoutUtils.setAllSizes(pane, new Dimension(editors1.getPreferredSize().width + 25, 100));

        equationEditors.add(pane);

        final Box b = Box.createHorizontalBox();
        final JLabel label = new JLabel("Example using wildcard: New$ = $ + 1");
        label.setFont(new Font("Dialog", Font.BOLD, 10));
        b.add(label);
        b.add(Box.createHorizontalGlue());
        equationEditors.add(b);

        equationEditors.add(Box.createVerticalStrut(10));
        equationEditors.add(createAddRemoveButtons());
        equationEditors.add(Box.createVerticalGlue());

        final Box box = Box.createHorizontalBox();
        box.add(createCalculatorNumberPad());
        box.add(Box.createHorizontalStrut(40));
        box.add(createFunctionList());
        box.add(Box.createHorizontalStrut(10));
        box.add(Box.createHorizontalGlue());

        expressionBox.add(equationEditors);
        expressionBox.add(Box.createVerticalStrut(15));
        expressionBox.add(box);
        expressionBox.add(Box.createVerticalGlue());


        mainBox.add(varBox);
        mainBox.add(Box.createHorizontalStrut(3));
        mainBox.add(createSelectVariableButton());
        mainBox.add(Box.createHorizontalStrut(3));
        mainBox.add(expressionBox);
        mainBox.add(Box.createHorizontalGlue());

        return mainBox;
    }


    /**
     * Creates the select variable button.
     */
    private JComponent createSelectVariableButton() {
        final Box box = Box.createVerticalBox();
        final JButton selectVariable = new JButton(">");
        selectVariable.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final Node node = (Node) CalculatorEditor.this.variableList.getSelectedValue();
                if (node != null) {
                    insertSymbol(node.getName());
                }
            }
        });


        box.add(Box.createVerticalStrut(50));
        box.add(selectVariable);
        box.add(Box.createVerticalGlue());

        return box;
    }


    private JComponent createAddRemoveButtons() {
        final Box box = Box.createHorizontalBox();
        final JButton remove = new JButton("Remove Selected Expressions");
        remove.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                removeSelected();
            }
        });

        final JButton add = new JButton("Add Expression");
        add.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                CalculatorEditor.this.editors.add(new ExpressionEditor(CalculatorEditor.this.dataSet, "", ""));
                createExpressionEditors();
                CalculatorEditor.this.expressionsPanel.revalidate();
                CalculatorEditor.this.expressionsPanel.repaint();

            }
        });

        box.add(add);
        box.add(Box.createHorizontalStrut(5));
        box.add(remove);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    /**
     * Builds the expression editors.
     */
    private JPanel initializeExpressionEditors() {
        if (this.expressionsPanel == null) {
            this.expressionsPanel = new JPanel();
            this.expressionsPanel.setLayout(new BoxLayout(this.expressionsPanel, BoxLayout.Y_AXIS));
        }
        this.expressionsPanel.removeAll();
        final String[] displayEquations;

        if (this.params != null) {
            final List<String> equations = (List<String>) this.params.get("equations", new ArrayList<String>());
            final int size = equations.size();
            displayEquations = equations.toArray(new String[size]);
        } else {
            final String _displayEquations = (String) this.params.get("calculator_equations", "");
            displayEquations = _displayEquations.split("///");
        }

        for (final String expression : displayEquations) {
            if (!(expression.contains("="))) {
                continue;
            }

            final String[] sides = expression.split("=");
            this.editors.add(new ExpressionEditor(this.dataSet, sides[0], sides[1]));
        }

        if (this.editors.isEmpty()) {
            this.editors.add(new ExpressionEditor(this.dataSet, "", ""));
        }

        final FocusListener listener = new FocusAdapter() {
            public void focusGained(final FocusEvent evt) {
                CalculatorEditor.this.focused = (ExpressionEditor) evt.getSource();
            }
        };

        for (final ExpressionEditor editor : this.editors) {
            editor.addFieldFocusListener(listener);
            this.expressionsPanel.add(editor);

        }
        this.expressionsPanel.add(Box.createVerticalGlue());

        return this.expressionsPanel;
    }

    private JPanel createExpressionEditors() {
        if (this.expressionsPanel == null) {
            this.expressionsPanel = new JPanel();
            this.expressionsPanel.setLayout(new BoxLayout(this.expressionsPanel, BoxLayout.Y_AXIS));
        }
        this.expressionsPanel.removeAll();

        if (this.editors.isEmpty()) {
            this.editors.add(new ExpressionEditor(this.dataSet, "", ""));
        }
        final FocusListener listener = new FocusAdapter() {
            public void focusGained(final FocusEvent evt) {
                CalculatorEditor.this.focused = (ExpressionEditor) evt.getSource();
            }
        };

        for (final ExpressionEditor editor : this.editors) {
            editor.addFieldFocusListener(listener);
            this.expressionsPanel.add(editor);

        }
        this.expressionsPanel.add(Box.createVerticalGlue());

        return this.expressionsPanel;
    }


    /**
     * Creates the variable selection list.
     */
    private JComponent createVariableList() {
        final List<Node> nodes = this.dataSet.getVariables();
        this.variableList = new JList(new DefaultListModel());
        this.variableList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        final DefaultListModel model = (DefaultListModel) this.variableList.getModel();
        for (final Node node : nodes) {
            model.addElement(node);
        }
        this.variableList.addMouseListener(new MouseAdapter() {
            public void mouseClicked(final MouseEvent evt) {
                if (evt.getButton() == MouseEvent.BUTTON1 && evt.getClickCount() % 2 == 0) {
                    final int index = CalculatorEditor.this.variableList.locationToIndex(evt.getPoint());
                    if (0 <= index) {
                        final ListModel model = CalculatorEditor.this.variableList.getModel();
                        final Node node = (Node) model.getElementAt(index);
                        if (node != null) {
                            insertSymbol(node.getName());
                        }
                    }
                }
            }
        });
        final JScrollPane pane = new JScrollPane(this.variableList);
        pane.setPreferredSize(new Dimension(100, 300));

        return pane;
    }


    /**
     * Creates a left-aligned label.
     */
    private static Box createLabel(final String text) {
        final Box box = Box.createHorizontalBox();
        box.add(new JLabel(text));
        box.add(Box.createHorizontalGlue());
        return box;
    }


    /**
     * Removes the selected editors.
     */
    private void removeSelected() {
        boolean allSelected = true;
        for (final ExpressionEditor e : this.editors) {
            if (!e.removeSelected()) {
                allSelected = false;
            }
        }
        if (allSelected) {
            JOptionPane.showMessageDialog(this, "Cannot delete all expression editors.");
        } else {
            for (int i = this.editors.size() - 1; 0 <= i; i--) {
                final ExpressionEditor e = this.editors.get(i);
                if (e.removeSelected()) {
                    this.editors.remove(i);
                }
            }
            createExpressionEditors();
            this.expressionsPanel.revalidate();
            this.expressionsPanel.repaint();
        }
    }


    /**
     * Inserts the given symbol in the last focused field in the expressions editor or
     * if there isn't such a field then the top expression field.
     */
    private void insertSymbol(final String symbol) {
        final ExpressionEditor editor = this.focused;
        if (editor != null) {
            editor.insertLastFocused(symbol, true);
        } else {
            this.editors.get(0).insertLastFocused(symbol, true);
        }
    }


    /**
     * Inserts the given expression fragment in the last focused expression field.
     */
    private void insertExpression(final ExpressionSignature sig) {
        final ExpressionEditor editor = this.focused;
        if (editor != null) {
            editor.addExpressionSignature(sig);
        } else {
            this.editors.get(0).addExpressionSignature(sig);
        }
    }


    /**
     * Creates the calculator's "number pad".
     */
    private JComponent createCalculatorNumberPad() {
        final JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(4, 4));

        panel.add(createCalculatorButton("7"));
        panel.add(createCalculatorButton("8"));
        panel.add(createCalculatorButton("9"));
        panel.add(createCalculatorButton("+"));

        panel.add(createCalculatorButton("4"));
        panel.add(createCalculatorButton("5"));
        panel.add(createCalculatorButton("6"));
        panel.add(createCalculatorButton("-"));

        panel.add(createCalculatorButton("1"));
        panel.add(createCalculatorButton("2"));
        panel.add(createCalculatorButton("3"));
        panel.add(createCalculatorButton("*"));

        panel.add(createCalculatorButton("0"));
        panel.add(createCalculatorButton("."));
        panel.add(createCalculatorButton(ConstantExpression.E.getName()));
        panel.add(createCalculatorButton(ConstantExpression.PI.getName()));

        panel.setPreferredSize(new Dimension(150, 150));

        return panel;
    }


    /**
     * Creates a button for the given symbol.
     */
    private JButton createCalculatorButton(final String symbol) {
        final JButton button = new JButton(symbol);
        final Font font = button.getFont();
        button.setFont(new Font("Dialog", font.getStyle(), font.getSize()));
        button.setMargin(new Insets(4, 4, 4, 4));
        button.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                insertSymbol(symbol);
            }
        });
        return button;
    }


    /**
     * Creates the list of functions.
     */
    private JComponent createFunctionList() {
        final List<ExpressionDescriptor> des = ExpressionManager.getInstance().getDescriptors();
        final Object[] descriptors = des.toArray(new Object[des.size()]);
        final JList list = new JList(descriptors);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(new Renderer());
        list.addMouseListener(new MouseAdapter() {
            public void mouseClicked(final MouseEvent evt) {
                if (evt.getButton() == MouseEvent.BUTTON1 && evt.getClickCount() % 2 == 0) {
                    final int index = list.locationToIndex(evt.getPoint());
                    if (0 <= index) {
                        final ListModel model = list.getModel();
                        final ExpressionDescriptor des = (ExpressionDescriptor) model.getElementAt(index);
                        insertExpression(des.getSignature());
                    }
                }
            }
        });

        final JScrollPane pane = new JScrollPane(list);
        LayoutUtils.setAllSizes(pane, new Dimension(150, 130));
        pane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);


        final JButton select = new JButton("Select");
        select.addActionListener(new ActionListener() {
            public void actionPerformed(final ActionEvent e) {
                final ExpressionDescriptor des = (ExpressionDescriptor) list.getSelectedValue();
                if (des != null) {
                    insertExpression(des.getSignature());
                }
            }
        });
        final Box selectBox = Box.createHorizontalBox();
        selectBox.add(Box.createHorizontalGlue());
        selectBox.add(select);
        selectBox.add(Box.createHorizontalGlue());


        final JPanel panel = new JPanel(new BorderLayout());
        panel.add(new JLabel("Functions:"), BorderLayout.NORTH);
        panel.add(pane, BorderLayout.CENTER);
        panel.add(selectBox, BorderLayout.SOUTH);

        return panel;
    }

    //=================================== Inner Classes =================================//


    /**
     * Renderer for the function list.
     */
    private static class Renderer extends DefaultListCellRenderer {

        public Component getListCellRendererComponent(
                final JList list,
                final Object value,
                final int index,
                final boolean isSelected,
                final boolean cellHasFocus) {
            super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

            if (value == null) {
                setText("");
            } else {
                final ExpressionDescriptor des = (ExpressionDescriptor) value;
                setText(des.getToken());
            }

            return this;
        }
    }


}






