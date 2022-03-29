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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.DagWrapper;
import edu.cmu.tetradapp.model.DataWrapper;
import edu.cmu.tetradapp.model.GraphWrapper;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.util.StringTextField;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * Edits parameters for Markov blanket search algorithm.
 *
 * @author Ricardo Silva (GES version)
 * @author Frank Wimberly adapted for PCX.
 * @author Frank Wimberly further adapted for Regression
 */
final class RegressionParamsPanel extends JPanel implements ActionListener {

    /**
     * The parameter object being edited.
     */
    private final Parameters params;

    /**
     * The name of the target variable or node in the regression.
     */
    private String targetName;

    /**
     * The variable names from the object being searched over (usually data).
     */
    private List<String> varNames;

    private List<String> regressorNames;

    //private JComboBox box;
    private final JTextField responseVar;
    private JList availableVarsList;
    private JList predictorVarListbox;
    private final ArrowButton responseButton;
    //private Vector varsList;
    //private Vector predictorVarsList;

    private static final String INCLUDE_RESPONSE = "includeResponse";
    private static final String INCLUDE_PREDICTOR = "includePredictor";
    private static final String EXCLUDE_PREDICTOR = "excludePredictor";

    /**
     * Opens up an editor to let the user view the given RegressionRunner.
     */
    public RegressionParamsPanel(Parameters params,
                                 Object[] parentModels) {

        if (params == null) {
            throw new NullPointerException(
                    "Parameters must not be null.");
        }

        this.params = params;

        if (params.get("varNames", null) != null) {
            this.varNames = (List<String>) params.get("varNames", null);
        }

        if (this.varNames == null) {
            this.varNames = getVarsFromData(parentModels);

            if (this.varNames == null) {
                this.varNames = getVarsFromGraph(parentModels);
            }

            params.set("varNames", this.varNames);

            if (this.varNames == null) {
                throw new IllegalStateException(
                        "Variables are not accessible.");
            }

            params().set("varNames", this.varNames);
        }

        //predictorVarsList = new Vector();

        JLabel instructions =
                new JLabel("Select response and predictor variables:");
        JLabel varsLabel = new JLabel("Variables");
        JLabel responseLabel = new JLabel("Response");
        JLabel predictorLabel = new JLabel("Predictor(s)");
        JScrollPane varListbox = (JScrollPane) createVarListbox();
        JScrollPane predictorVarListbox =
                (JScrollPane) createPredictorVarListbox();


        this.responseButton = new ArrowButton(this, RegressionParamsPanel.INCLUDE_RESPONSE);
        ArrowButton predictorInButton =
                new ArrowButton(this, RegressionParamsPanel.INCLUDE_PREDICTOR);
        ArrowButton predictorOutButton =
                new ArrowButton(this, RegressionParamsPanel.EXCLUDE_PREDICTOR, false);

        this.responseVar = new StringTextField("", 10);
        this.responseVar.setEditable(false);
        this.responseVar.setBackground(Color.white);
        this.responseVar.setPreferredSize(new Dimension(150, 30));
        this.responseVar.setFont(new Font("SanSerif", Font.BOLD, 12));

        //TEST
        this.responseVar.setText(params.getString("targetName", null));
        if (!this.responseVar.getText().equals("") &&
                this.responseButton.getText().equals(">")) {
            this.responseButton.toggleInclude();
        }
        //this.predictorVarListbox.setListData(params.getRegressorNames());

        DefaultListModel predsModel =
                (DefaultListModel) this.predictorVarListbox.getModel();
        String[] paramNames = (String[]) params.get("regressorNames", null);
        for (String paramName : paramNames) {
            predsModel.addElement(paramName);
        }

        //Construct availableVarsList of variable names not response nor in predictors.
        //List varListNames = params.getRegressorNames();
        List<Object> varListNames = new ArrayList<Object>(this.varNames);
        String targetName = params.getString("targetName", null);
        varListNames.remove(targetName);

        List<String> regNames = (List<String>) params.get("regressorNames", null);

        for (String regName : regNames) {
            varListNames.remove(regName);
        }

        DefaultListModel varsModel =
                (DefaultListModel) this.availableVarsList.getModel();
        varsModel.clear();
        for (Object varListName : varListNames) {
            varsModel.addElement(varListName);
        }

        //availableVarsList.setListData(varNames.toArray());
        //END OF TEST

        //setPreferredSize(new Dimension(300, 600));

        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        Box b = Box.createVerticalBox();

        Box b0 = Box.createHorizontalBox();
        b0.add(instructions);
        //add(instructions);

        Box b1 = Box.createHorizontalBox();

        Box b2 = Box.createVerticalBox();
        Box b3 = Box.createVerticalBox();
        Box b4 = Box.createVerticalBox();
        Box b5 = Box.createVerticalBox();

        DoubleTextField alphaField = new DoubleTextField(params.getDouble("alpha", 0.001), 4,
                NumberFormatUtil.getInstance().getNumberFormat());
        alphaField.setFilter(new DoubleTextField.Filter() {
            public double filter(double value, double oldValue) {
                try {
                    params().set("alpha", 0.001);
                    Preferences.userRoot().putDouble("alpha",
                            params().getDouble("alpha", 0.001));
                    return value;
                } catch (Exception e) {
                    return oldValue;
                }
            }
        });

        JLabel alphaLabel = new JLabel("Alpha:");

        b2.add(varsLabel);
        b2.add(varListbox);
        b2.add(alphaLabel);
        b2.add(alphaField);

        b3.add(this.responseButton);
        Component strut3 = Box.createVerticalStrut(90);
        b3.add(strut3);
        b3.add(predictorInButton);
        b3.add(predictorOutButton);

        //Component strut41 = Box.createVerticalStrut(10);
        //b4.add(strut41);
        responseLabel.setPreferredSize(new Dimension(80, 30));
        b4.add(responseLabel);
        Component strut42 = Box.createVerticalStrut(120);
        b4.add(strut42);
        b4.add(predictorLabel);

        b5.add(this.responseVar);
        Component strut5 = Box.createVerticalStrut(10);
        b5.add(strut5);
        b5.add(predictorVarListbox);

        b1.add(b2);
        b1.add(b3);
        b1.add(b4);
        b1.add(b5);

        b.add(b0);
        b.add(b1);

        add(b);

    }


    private JComponent createVarListbox() {
        //varsList = new Vector(varNames);
        //availableVarsList = new JList(varsList);    ORIG
        this.availableVarsList = new JList(new DefaultListModel());
        DefaultListModel varsModel =
                (DefaultListModel) this.availableVarsList.getModel();

        for (Object varName : this.varNames) {
            varsModel.addElement(varName);
        }

        //availableVarsList.setModel(new DefaultListModel());
        this.availableVarsList.setVisibleRowCount(5);
        this.availableVarsList.setFixedCellWidth(100);
        this.availableVarsList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        this.availableVarsList.setSelectedIndex(0);

        //JLabel listboxTitle = new JLabel(" Variables");
        //scrollPane.setColumnHeaderView(listboxTitle);
        //listboxTitle.setBackground(Color.DARK_GRAY);

        return new JScrollPane(this.availableVarsList);
    }

    private JComponent createPredictorVarListbox() {
        this.predictorVarListbox = new JList(new DefaultListModel());
        this.predictorVarListbox.setVisibleRowCount(4);
        this.predictorVarListbox.setFixedCellWidth(100);
        this.predictorVarListbox.setSelectionMode(
                ListSelectionModel.SINGLE_SELECTION);
        this.predictorVarListbox.setSelectedIndex(0);

        //JLabel listboxTitle = new JLabel(" Variables");
        //scrollPane.setColumnHeaderView(listboxTitle);
        //listboxTitle.setBackground(Color.DARK_GRAY);

        return new JScrollPane(this.predictorVarListbox);
    }


    private List<String> getVarsFromData(Object[] parentModels) {
        DataModel dataModel = null;

        for (Object parentModel : parentModels) {
            if (parentModel instanceof DataWrapper) {
                DataWrapper dataWrapper = (DataWrapper) parentModel;
                dataModel = dataWrapper.getSelectedDataModel();
            }
        }

        if (dataModel == null) {
            return null;
        } else {
            return new ArrayList<>(dataModel.getVariableNames());
        }
    }

    private List<String> getVarsFromGraph(Object[] parentModels) {
        Object graphWrapper = null;

        for (Object parentModel : parentModels) {
            if (parentModel instanceof GraphWrapper) {
                graphWrapper = parentModel;
            } else if (parentModel instanceof DagWrapper) {
                graphWrapper = parentModel;
            }
        }

        if (graphWrapper == null) {
            return null;
        } else {
            Graph graph = null;

            if (graphWrapper instanceof GraphWrapper) {
                graph = ((GraphWrapper) graphWrapper).getGraph();
            } else if (graphWrapper instanceof DagWrapper) {
                graph = ((DagWrapper) graphWrapper).getDag();
            }

            List<Node> nodes = graph.getNodes();
            List<String> nodeNames = new LinkedList<>();

            for (Node node : nodes) {
                nodeNames.add(node.getName());
            }

            return nodeNames;
        }
    }

    private void setRegressorValues() {
    }

    private void setRegressorNames(List<String> names) {
        this.regressorNames = names;
    }

    private Parameters params() {
        return this.params;
    }

    private String targetName() {
        return this.targetName;
    }

    private void setTargetName(String targetName) {
        this.targetName = targetName;
    }


    public void actionPerformed(ActionEvent e) {
        String varName;
        String predictorName;
        int varSelectionIndex, predictorVarSelectionIndex;

        //DefaultListModel varsModel = (DefaultListModel) availableVarsList.getModel();   //HERE

        //The list models were used because of apparent undocumented behavior of the lists.
        DefaultListModel varsModel =
                (DefaultListModel) this.availableVarsList.getModel();
        DefaultListModel predsModel =
                (DefaultListModel) this.predictorVarListbox.getModel();

        //if (varsList.size()!=0)
        if (varsModel.size() != 0) {
            varName = (String) this.availableVarsList.getSelectedValue();
        } else {
            varName = "";
        }

        if (predsModel.size() != 0) {
            predictorName = (String) this.predictorVarListbox.getSelectedValue();
        } else {
            predictorName = "";
        }

        /* include/exclude response variable */
        if (e.getActionCommand().equals(RegressionParamsPanel.INCLUDE_RESPONSE)) {
            if ((this.availableVarsList.isSelectionEmpty()) &&
                    (this.responseButton.getIsIncluded())) {
                return;
            }

            if (this.responseButton.getIsIncluded()) {
                this.responseVar.setText(varName);
                //varsList.remove(varName);  ORIG

                varsModel.removeElement(varName);
            } else {
                //varsList.add(responseVar.getText());   ORIG
                varsModel.addElement(this.responseVar.getText());
                this.responseVar.setText("");
            }
            this.responseButton.toggleInclude();

            //Object targetValue = box.getSelectedItem();
            String newTargetName = this.responseVar.getText();
            setTargetName(newTargetName);
            params().set("targetName", targetName());
        }
        /* include predictor variable */
        else if (e.getActionCommand().equals(RegressionParamsPanel.INCLUDE_PREDICTOR)) {
            if (this.availableVarsList.isSelectionEmpty()) {
                return;
            }
            //varsList.remove(varName);    ORIG

            varsModel.removeElement(varName);
            predsModel.addElement(varName);

            /* exclude predictor variable */
        } else if (e.getActionCommand().equals(RegressionParamsPanel.EXCLUDE_PREDICTOR)) {
            if (this.predictorVarListbox.isSelectionEmpty()) {
                return;
            }
            predsModel.removeElement(predictorName);
            //varsList.add(predictorName);   ORIG
            varsModel.addElement(predictorName);
        } else {
            return;
        }

        /* updates expt variables and predictor listbox */
        varSelectionIndex = this.availableVarsList.getSelectedIndex();
        predictorVarSelectionIndex = this.predictorVarListbox.getSelectedIndex();

        if (varSelectionIndex > 0) {
            varSelectionIndex--;
        }
        //availableVarsList.setListData(varsList);
        if (varSelectionIndex != -1) {
            this.availableVarsList.setSelectedIndex(varSelectionIndex);
        }

        if (predictorVarSelectionIndex > 0) {
            predictorVarSelectionIndex--;
        }
        //predictorVarListbox.setListData(predictorVarsList);
        if (predictorVarSelectionIndex != -1) {
            this.predictorVarListbox.setSelectedIndex(predictorVarSelectionIndex);
        }

        //setRegressorNames((String[]) predictorVarsList.toArray());

        int numPredictors = predsModel.size();
        Object[] predictors = new Object[numPredictors];

        List<String> regNames = new ArrayList<>();
        for (int i = 0; i < numPredictors; i++) {
            predictors[i] = predsModel.getElementAt(i);
            regNames.add((String) predsModel.getElementAt(i));
        }

        /*
        if(predictorVarsList.contains(responseVar.getText()))
            throw new RuntimeException("Response ar may not be a predictor");

        if(predictorVarsList.size() == 0 || responseVar.getText() == null)
            throw new RuntimeException("Response and predictors must be selected.");
        */

        setRegressorValues();   //TEST
        setRegressorNames(regNames);

        params().set("regressorNames", this.regressorNames);
    }


    /**
     * Private Inner Class to manipulate the arrow buttons for
     * including/excluding variables for x/y-axis
     */
    public class ArrowButton extends JButton {
        private boolean isInclude;

        public ArrowButton(RegressionParamsPanel listener, String command) {
            this(listener, command, true);
        }

        public ArrowButton(RegressionParamsPanel listener, String command,
                           boolean isInclude) {
            this.isInclude = isInclude;
            addActionListener(listener);
            setActionCommand(command);
            if (isInclude) {
                setText(">");
            } else {
                setText("<");
            }
        }

        public void toggleInclude() {
            if (this.isInclude) {
                setText("<");
                this.isInclude = false;
            } else {
                setText(">");
                this.isInclude = true;
            }
        }

        public boolean getIsIncluded() {
            return this.isInclude;
        }
    }

}





