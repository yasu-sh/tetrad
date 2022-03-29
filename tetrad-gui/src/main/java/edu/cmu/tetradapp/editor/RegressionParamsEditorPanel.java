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
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.model.RegressionModel;
import edu.cmu.tetradapp.util.DoubleTextField;
import edu.cmu.tetradapp.workbench.LayoutUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.awt.dnd.*;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.io.IOException;
import java.util.List;
import java.util.*;

/**
 * Allows one to drop/drap variables from a source list to a response area and a
 * predictors list. Also lets one specify an alpha level.
 *
 * @author Tyler Gibson
 */
@SuppressWarnings({"unchecked"})
class RegressionParamsEditorPanel extends JPanel {

    private static final long serialVersionUID = -194301447990323529L;

    private final boolean logistic;
    private final RegressionModel regressionModel;
    /**
     * The params that are being edited.
     */
    private final Parameters params;

    /**
     * The list of predictors.
     */
    private static JList PREDICTORS_LIST;

    /**
     * The list of source variables.
     */
    private static JList SOURCE_LIST;

    /**
     * A list with a single item in it for the response variable.
     */
    private static JTextField RESPONSE_FIELD;

    /**
     * A mapping between variable names and what sort of variable they are: 1 -
     * binary, 2- discrete, 3 - continuous.
     */
    private static final Map<String, Integer> VAR_MAP = new HashMap<>();

    /**
     * The font to render fields in.
     */
    private static final Font FONT = new Font("Dialog", Font.PLAIN, 12);

    /**
     * Constructs the editor given the <code>Parameters</code> and the
     * <code>DataModel</code> that should be used.
     */
    public RegressionParamsEditorPanel(final RegressionModel regressionModel, final Parameters parameters,
                                       final DataModel model, final boolean logistic) {
        this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        if (parameters == null) {
            throw new NullPointerException("The given params must not be null");
        }
        this.params = parameters;
        this.logistic = logistic;
        final List<String> variableNames = regressionModel.getVariableNames();
        this.regressionModel = regressionModel;

        // create components
        RegressionParamsEditorPanel.PREDICTORS_LIST = RegressionParamsEditorPanel.createList();
        final VariableListModel predictorsModel = (VariableListModel) RegressionParamsEditorPanel.getPredictorsList().getModel();
        RegressionParamsEditorPanel.SOURCE_LIST = RegressionParamsEditorPanel.createList();
        if (logistic && model instanceof DataSet) {
            buildMap((DataSet) model);
            RegressionParamsEditorPanel.getSourceList().setCellRenderer(new LogisticRegRenderer());
        }
        final VariableListModel variableModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
        RegressionParamsEditorPanel.RESPONSE_FIELD = createResponse(RegressionParamsEditorPanel.getSourceList(), 100);

        // if regressors are already set use'em.
        final List<String> regressors = regressionModel.getRegressorNames();
        if (regressors != null) {
            predictorsModel.addAll(regressors);
            final List<String> initVars = new ArrayList<>(variableNames);
            initVars.removeAll(regressors);
            variableModel.addAll(initVars);
        } else {
            variableModel.addAll(variableNames);
        }
        // if target is set use it too
        final String target = regressionModel.getTargetName();
        if (target != null) {
            variableModel.remove(target);
            //     response.setText(target);
        }

        // deal with drag and drop
        new DropTarget(RegressionParamsEditorPanel.getSourceList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);
        new DropTarget(RegressionParamsEditorPanel.getResponseField(), DnDConstants.ACTION_MOVE, new TargetListener(), true);
        new DropTarget(RegressionParamsEditorPanel.getPredictorsList(), DnDConstants.ACTION_MOVE, new TargetListener(), true);

        DragSource dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(RegressionParamsEditorPanel.getResponseField(), DnDConstants.ACTION_MOVE, new SourceListener());
        dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(RegressionParamsEditorPanel.getSourceList(), DnDConstants.ACTION_MOVE, new SourceListener());
        dragSource = DragSource.getDefaultDragSource();
        dragSource.createDefaultDragGestureRecognizer(RegressionParamsEditorPanel.getPredictorsList(), DnDConstants.ACTION_MOVE, new SourceListener());
        // build the gui
        final Box box = Box.createHorizontalBox();
        box.add(Box.createHorizontalStrut(10));
        final Box label = RegressionParamsEditorPanel.createLabel("Variables:");
        final int height = label.getPreferredSize().height + RegressionParamsEditorPanel.getResponseField().getPreferredSize().height + 10;
        final Box vBox1 = Box.createVerticalBox();
        vBox1.add(label);
        final JScrollPane pane = RegressionParamsEditorPanel.createScrollPane(RegressionParamsEditorPanel.getSourceList(), new Dimension(100, 350 + height));
        vBox1.add(pane);
        vBox1.add(Box.createVerticalStrut(10));
        vBox1.add(buildAlphaArea(this.params.getDouble("alpha", 0.001)));
        vBox1.add(Box.createVerticalStrut(10));
        vBox1.add(buildSortButton());
        vBox1.add(Box.createVerticalGlue());
        box.add(vBox1);

        box.add(Box.createHorizontalStrut(4));
        box.add(buildSelectorArea(label.getPreferredSize().height));
        box.add(Box.createHorizontalStrut(4));

        final Box vBox = Box.createVerticalBox();
        vBox.add(RegressionParamsEditorPanel.createLabel("Response:"));

        vBox.add(RegressionParamsEditorPanel.getResponseField());
        vBox.add(Box.createVerticalStrut(10));
        vBox.add(RegressionParamsEditorPanel.createLabel("Predictor(s):"));
        vBox.add(RegressionParamsEditorPanel.createScrollPane(RegressionParamsEditorPanel.getPredictorsList(), new Dimension(100, 350)));
        vBox.add(Box.createVerticalGlue());

        box.add(vBox);
        box.add(Box.createHorizontalStrut(10));
        box.add(Box.createHorizontalGlue());

        this.add(Box.createVerticalStrut(20));
        this.add(box);
    }

    //============================= Private Methods =================================//
    private static List<Comparable> getSelected(final JList list) {
        final List selected = list.getSelectedValuesList();
        final List<Comparable> selectedList = new ArrayList<>(selected == null ? 0 : selected.size());
        if (selected != null) {
            for (final Object o : selected) {
                selectedList.add((Comparable) o);
            }
        }
        return selectedList;
    }

    /**
     * Bulids the arrows that allow one to move variables around (can also use
     * drag and drop)
     */
    private Box buildSelectorArea(final int startHeight) {
        final Box box = Box.createVerticalBox();
        final JButton moveToResponse = new JButton(">");
        final JButton moveToPredictor = new JButton(">");
        final JButton moveToSource = new JButton("<");

        moveToResponse.addActionListener((e) -> {
            final VariableListModel sourceModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
            final String target = RegressionParamsEditorPanel.getResponseField().getText();
            final List<Comparable> selected = RegressionParamsEditorPanel.getSelected(RegressionParamsEditorPanel.getSourceList());
            if (selected.isEmpty()) {
                return;
            } else if (1 < selected.size()) {
                JOptionPane.showMessageDialog(this, "Cannot have more than one response variable");
                return;
            } else if (this.logistic && !isBinary((String) selected.get(0))) {
                JOptionPane.showMessageDialog(this,
                        "Response variable must be binary.");
                return;
            }
            sourceModel.removeAll(selected);
            RegressionParamsEditorPanel.getResponseField().setText((String) selected.get(0));
            RegressionParamsEditorPanel.getResponseField().setCaretPosition(0);
            this.regressionModel.setTargetName((String) selected.get(0));
            if (target != null && target.length() != 0) {
                sourceModel.add(target);
            }
        });

        moveToPredictor.addActionListener((e) -> {
            final VariableListModel predictorsModel = (VariableListModel) RegressionParamsEditorPanel.getPredictorsList().getModel();
            final VariableListModel sourceModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
            final List<Comparable> selected = RegressionParamsEditorPanel.getSelected(RegressionParamsEditorPanel.getSourceList());
            sourceModel.removeAll(selected);
            predictorsModel.addAll(selected);
            this.regressionModel.setRegressorName(getPredictors());
        });

        moveToSource.addActionListener((e) -> {
            final VariableListModel predictorsModel = (VariableListModel) RegressionParamsEditorPanel.getPredictorsList().getModel();
            final VariableListModel sourceModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
            final List<Comparable> selected = RegressionParamsEditorPanel.getSelected(RegressionParamsEditorPanel.getPredictorsList());
            // if not empty remove/add, otherwise try the response list.
            if (!selected.isEmpty()) {
                predictorsModel.removeAll(selected);
                sourceModel.addAll(selected);
                this.regressionModel.setRegressorName(getPredictors());
            } else if (RegressionParamsEditorPanel.getResponseField().getText() != null && RegressionParamsEditorPanel.getResponseField().getText().length() != 0) {
                final String text = RegressionParamsEditorPanel.getResponseField().getText();
                this.regressionModel.setTargetName(null);
                RegressionParamsEditorPanel.getResponseField().setText(null);
                sourceModel.addAll(Collections.singletonList(text));
            }
        });

        box.add(Box.createVerticalStrut(startHeight));
        box.add(moveToResponse);
        box.add(Box.createVerticalStrut(150));
        box.add(moveToPredictor);
        box.add(Box.createVerticalStrut(10));
        box.add(moveToSource);
        box.add(Box.createVerticalGlue());

        return box;
    }

    private Box buildSortButton() {
        final JButton sort = new JButton("Sort Variables");
        sort.setFont(sort.getFont().deriveFont(11f));
        sort.setMargin(new Insets(3, 3, 3, 3));
        sort.addActionListener((e) -> {
            final VariableListModel predictorsModel = (VariableListModel) RegressionParamsEditorPanel.getPredictorsList().getModel();
            final VariableListModel sourceModel = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
            predictorsModel.sort();
            sourceModel.sort();
        });
        final Box box = Box.createHorizontalBox();
        box.add(sort);
        box.add(Box.createHorizontalGlue());

        return box;
    }

    private Box buildAlphaArea(final double alpha) {
        final DoubleTextField field = new DoubleTextField(alpha, 4, NumberFormatUtil.getInstance().getNumberFormat());
        field.setFilter((value, oldValue) -> {
            if (0.0 <= value && value <= 1.0) {
                this.params.set("alpha", value);
                this
                        .firePropertyChange("significanceChanged", oldValue, value);
                return value;
            }
            return oldValue;
        });

        final Box box = Box.createHorizontalBox();
        box.add(new JLabel("Alpha: "));
        box.add(field);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    private void buildMap(final DataSet model) {
        for (final Node node : model.getVariables()) {
            if (DataUtils.isBinary(model, model.getColumn(node))) {
                RegressionParamsEditorPanel.getVarMap().put(node.getName(), 1);
            } else if (node instanceof DiscreteVariable) {
                RegressionParamsEditorPanel.getVarMap().put(node.getName(), 2);
            } else {
                RegressionParamsEditorPanel.getVarMap().put(node.getName(), 3);
            }
        }
    }

    private static JScrollPane createScrollPane(final JList comp, final Dimension dim) {
        final JScrollPane pane = new JScrollPane(comp);
        LayoutUtils.setAllSizes(pane, dim);
        return pane;
    }

    private static Box createLabel(final String text) {
        final JLabel label = new JLabel(text);
        label.setAlignmentX(JLabel.LEFT_ALIGNMENT);
        final Box box = Box.createHorizontalBox();
        box.add(label);
        box.add(Box.createHorizontalGlue());
        return box;
    }

    private JTextField createResponse(final JList list, final int width) {
        final JTextField pane = new JTextField();
        pane.setFont(RegressionParamsEditorPanel.getFONT());
        pane.setFocusable(true);
        pane.setEditable(false);
        pane.setBackground(list.getBackground());

        final String target = this.regressionModel.getTargetName();
        if (target != null) {
            pane.setText(target);
        } else {
            pane.setText("Hello");
        }
        pane.setCaretPosition(0);
        LayoutUtils.setAllSizes(pane, new Dimension(width, pane.getPreferredSize().height));
        if (target == null) {
            pane.setText(null);
        }
        pane.addFocusListener(new FocusAdapter() {
            @Override
            public void focusGained(final FocusEvent e) {
                RegressionParamsEditorPanel.getPredictorsList().clearSelection();
            }
        });

        return pane;
    }

    private static JList createList() {
        final JList list = new JList(new VariableListModel());
        list.setFont(RegressionParamsEditorPanel.getFONT());
        list.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        list.setVisibleRowCount(10);
        return list;
    }

    private static DataFlavor getListDataFlavor() {
        try {
            return new DataFlavor(DataFlavor.javaJVMLocalObjectMimeType + "; class=java.lang.Object",
                    "Local Variable List");
        } catch (final Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private List<String> getPredictors() {
        final ListModel model = RegressionParamsEditorPanel.getPredictorsList().getModel();
        final List<String> predictors = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            predictors.add((String) model.getElementAt(i));
        }
        return predictors;
    }

    private void addToSource(final String var) {
        final VariableListModel model = (VariableListModel) RegressionParamsEditorPanel.getSourceList().getModel();
        model.add(var);
    }

    private boolean isBinary(final String node) {
        final int i = RegressionParamsEditorPanel.getVarMap().get(node);
        return i == 1;
    }

    private static Map<String, Integer> getVarMap() {
        return RegressionParamsEditorPanel.VAR_MAP;
    }

    private static JList getPredictorsList() {
        return RegressionParamsEditorPanel.PREDICTORS_LIST;
    }

    private static JList getSourceList() {
        return RegressionParamsEditorPanel.SOURCE_LIST;
    }

    private static JTextField getResponseField() {
        return RegressionParamsEditorPanel.RESPONSE_FIELD;
    }

    private static Font getFONT() {
        return RegressionParamsEditorPanel.FONT;
    }

    //========================== Inner classes (a lot of'em) =========================================//

    /**
     * A renderer that adds info about whether a variable is binary or not.
     */
    private static class LogisticRegRenderer extends DefaultListCellRenderer {

        public Component getListCellRendererComponent(final JList list, final Object value, final int index, final boolean isSelected, final boolean cellHasFocus) {
            String var = (String) value;
            if (var == null) {
                setText(" ");
                return this;
            }
            final int binary = RegressionParamsEditorPanel.getVarMap().get(var);
            if (binary == 1) {
                var += " (Binary)";
            } else if (binary == 2) {
                var += " (Discrete)";
            } else if (binary == 3) {
                var += " (Continuous)";
            }
            setText(var);

            if (isSelected) {
                setBackground(list.getSelectionBackground());
                setForeground(list.getSelectionForeground());
            } else {
                setBackground(list.getBackground());
                setForeground(list.getForeground());
            }

            return this;
        }
    }

    private class TargetListener extends DropTargetAdapter {

        public void drop(final DropTargetDropEvent dtde) {
            final Transferable t = dtde.getTransferable();
            final Component comp = dtde.getDropTargetContext().getComponent();
            if (comp instanceof JList || comp instanceof JTextField) {
                try {
                    // if response, remove everything first
                    if (comp == RegressionParamsEditorPanel.getResponseField()) {
                        final String var = RegressionParamsEditorPanel.getResponseField().getText();
                        if (var != null && var.length() != 0) {
                            addToSource(var);
                        }
                        final List<Comparable> vars = (List<Comparable>) t.getTransferData(ListTransferable.FLAVOR);
                        if (vars.isEmpty()) {
                            dtde.rejectDrop();
                            return;
                        } else if (1 < vars.size()) {
                            JOptionPane.showMessageDialog(RegressionParamsEditorPanel.this,
                                    "There can only be one response variable.");
                            dtde.rejectDrop();
                            return;
                        } else if (RegressionParamsEditorPanel.this.logistic && !isBinary((String) vars.get(0))) {
                            JOptionPane.showMessageDialog(RegressionParamsEditorPanel.this,
                                    "The response variable must be binary");
                            dtde.rejectDrop();
                            return;
                        }
                        RegressionParamsEditorPanel.getResponseField().setText((String) vars.get(0));
                        RegressionParamsEditorPanel.getResponseField().setCaretPosition(0);
                    } else {
                        final JList list = (JList) comp;
                        final VariableListModel model = (VariableListModel) list.getModel();
                        final List<Comparable> vars = (List<Comparable>) t.getTransferData(ListTransferable.FLAVOR);
                        model.addAll(vars);
                    }

                    RegressionParamsEditorPanel.this.regressionModel.setTargetName(RegressionParamsEditorPanel.getResponseField().getText());
                    RegressionParamsEditorPanel.this.regressionModel.setRegressorName(getPredictors());
                    dtde.getDropTargetContext().dropComplete(true);
                } catch (final Exception ex) {
                    dtde.rejectDrop();
                    ex.printStackTrace();
                }
            } else {
                dtde.rejectDrop();
            }
        }
    }

    /**
     * A source/gesture listener for the JLists
     */
    private class SourceListener extends DragSourceAdapter implements DragGestureListener {

        public void dragDropEnd(final DragSourceDropEvent evt) {
            if (evt.getDropSuccess()) {
                final Component comp = evt.getDragSourceContext().getComponent();
                final Transferable t = evt.getDragSourceContext().getTransferable();
                if (t instanceof ListTransferable) {
                    try {
                        //noinspection unchecked
                        final List<Comparable> o = (List<Comparable>) t.getTransferData(ListTransferable.FLAVOR);
                        if (comp instanceof JList) {
                            final JList list = (JList) comp;
                            final VariableListModel model = (VariableListModel) list.getModel();
                            for (final Comparable c : o) {
                                model.removeFirst(c);
                            }
                        } else {
                            final JTextField pane = (JTextField) comp;
                            pane.setText(null);
                        }

                        RegressionParamsEditorPanel.this.regressionModel.setTargetName(RegressionParamsEditorPanel.getResponseField().getText());
                        RegressionParamsEditorPanel.this.regressionModel.setRegressorName(getPredictors());
                    } catch (final Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        }

        public void dragGestureRecognized(final DragGestureEvent dge) {
            final Component comp = dge.getComponent();
            List selected = null;
            if (comp instanceof JList) {
                final JList list = (JList) comp;
                selected = list.getSelectedValuesList();
            } else {
                final JTextField pane = (JTextField) comp;
                final String text = pane.getText();
                if (text != null && text.length() != 0) {
                    selected = Collections.singletonList(text);
                }
            }
            if (selected != null) {
                final ListTransferable t = new ListTransferable(Arrays.asList(selected));
                dge.startDrag(DragSource.DefaultMoveDrop, t, this);
            }
        }
    }

    /**
     * A basic model for the list (needed an addAll feature, which the detault
     * model didn't have)
     */
    private static class VariableListModel extends AbstractListModel {

        private final Vector<Comparable> delegate = new Vector<>();

        public int getSize() {
            return this.delegate.size();
        }

        public Object getElementAt(final int index) {
            return this.delegate.get(index);
        }

        public void remove(final Comparable element) {
            final int index = this.delegate.indexOf(element);
            if (0 <= index) {
                this.delegate.remove(index);
                this.fireIntervalRemoved(this, index, index);
            }
        }

        public void add(final Comparable element) {
            this.delegate.add(element);
            this.fireIntervalAdded(this, this.delegate.size(), this.delegate.size());
        }

        public void removeFirst(final Comparable element) {
            this.delegate.removeElement(element);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

        public void removeAll(final List<? extends Comparable> elements) {
            this.delegate.removeAll(elements);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

        public void addAll(final List<? extends Comparable> elements) {
            this.delegate.addAll(elements);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

        public void removeAll() {
            this.delegate.clear();
            this.fireContentsChanged(this, 0, 0);
        }

        public void sort() {
            Collections.sort(this.delegate);
            this.fireContentsChanged(this, 0, this.delegate.size());
        }

    }

    /**
     * A basic transferable.
     */
    private static class ListTransferable implements Transferable {

        private final static DataFlavor FLAVOR = RegressionParamsEditorPanel.getListDataFlavor();

        private final List object;

        public ListTransferable(final List object) {
            if (object == null) {
                throw new NullPointerException();
            }
            this.object = object;
        }

        public DataFlavor[] getTransferDataFlavors() {
            return new DataFlavor[]{ListTransferable.FLAVOR};
        }

        public boolean isDataFlavorSupported(final DataFlavor flavor) {
            return flavor == ListTransferable.FLAVOR;
        }

        public Object getTransferData(final DataFlavor flavor) throws UnsupportedFlavorException, IOException {
            if (ListTransferable.FLAVOR != flavor) {
                throw new UnsupportedFlavorException(flavor);
            }
            return this.object;
        }
    }

    public Parameters getParams() {
        return this.params;
    }

}
