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

import edu.cmu.tetrad.data.DataModel;
import edu.cmu.tetrad.data.Knowledge;
import edu.cmu.tetrad.graph.EdgeListGraph;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.LayoutUtil;
import edu.cmu.tetrad.util.JOptionUtils;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializable;
import edu.cmu.tetradapp.model.AlgorithmRunner;
import edu.cmu.tetradapp.util.GraphHistory;
import edu.cmu.tetradapp.util.IndTestType;
import edu.cmu.tetradapp.util.WatchedProcess;
import edu.cmu.tetradapp.workbench.DisplayEdge;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.CharArrayWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.rmi.MarshalledObject;
import java.util.ArrayList;

/**
 * Abstract base class for a number of search editors. The advantage of extending this class, in case you were
 * wondering, is that it will handle threading for you, provide a stop button for algorithm, and do logging. The execute
 * button used must be getExecuteButton(), or else logging won't work.
 *
 * @author josephramsey
 */
public abstract class AbstractSearchEditor extends JPanel implements GraphEditable, IndTestTypeSetter {

    /**
     * The algorithm wrapper being viewed.
     */
    private final AlgorithmRunner algorithmRunner;
    /**
     * The button one clicks to executeButton the algorithm.
     */
    private final JButton executeButton = new JButton();
    /**
     * The label for the result graph workbench.
     */
    private final String resultLabel;
    /**
     * History of graph edits.
     */
    private final GraphHistory graphHistory = new GraphHistory();
    /**
     * True if the warning message that previously defined knowledge is being used has already been shown and doesn't
     * need to be shown again.
     */
    boolean knowledgeMessageShown;
    /**
     * The workbench displaying the result workbench.
     */
    private GraphWorkbench workbench;
    /**
     * The scrollpange for the result workbench.
     */
    private JScrollPane workbenchScroll;

    //============================CONSTRUCTOR===========================//

    AbstractSearchEditor(AlgorithmRunner algorithmRunner, String resultLabel) {
        if (algorithmRunner == null) {
            throw new NullPointerException();
        }

        if (resultLabel == null) {
            throw new NullPointerException();
        }

        this.algorithmRunner = algorithmRunner;
        this.resultLabel = resultLabel;

        setup(resultLabel);
    }

    /**
     * Sets the name of this editor.
     */
    public final void setName(String name) {
        String oldName = getName();
        super.setName(name);
        firePropertyChange("name", oldName, getName());
    }

    //========================== Public Methods required by GraphEditable ======//


    /**
     * @return the work bench of null if there isn't one.
     */
    public GraphWorkbench getWorkbench() {
        return this.workbench;
    }

    void setWorkbench(GraphWorkbench graphWorkbench) {
        this.workbench = graphWorkbench;
    }

    /**
     * @return the graph.
     */
    public Graph getGraph() {
        if (this.workbench != null) {
            return this.workbench.getGraph();
        }
        return new EdgeListGraph();
    }

    /**
     * Not supported.
     */
    public void setGraph(Graph g) {
        this.workbench.setGraph(g);
        throw new UnsupportedOperationException("Cannot set the graph on a search editor.");
    }

    /**
     * Returns a list of all the SessionNodeWrappers (TetradNodes) and SessionNodeEdges that are model components for
     * the respective SessionNodes and SessionEdges selected in the workbench. Note that the workbench, not the
     * SessionEditorNodes themselves, keeps track of the selection.
     *
     * @return the set of selected model nodes.
     */
    public java.util.List getSelectedModelComponents() {
        java.util.List<Component> selectedComponents =
                this.workbench.getSelectedComponents();
        java.util.List<TetradSerializable> selectedModelComponents =
                new ArrayList<>();

        for (Component comp : selectedComponents) {
            if (comp instanceof DisplayNode) {
                selectedModelComponents.add(
                        ((DisplayNode) comp).getModelNode());
            } else if (comp instanceof DisplayEdge) {
                selectedModelComponents.add(
                        ((DisplayEdge) comp).getModelEdge());
            }
        }

        return selectedModelComponents;
    }

    //===========================PROTECTED METHODS==========================//

    /**
     * Not supported.
     */
    public void pasteSubsession(java.util.List<Object> sessionElements, Point upperLeft) {
        throw new UnsupportedOperationException("Cannot paste into Search editor.");
    }

    /**
     * Constructs the toolbar panel. For the execute button, must use getExecuteButton() in order for logging to work.
     */
    protected abstract JPanel getToolbar();

    /**
     * Adds any special menus needed for a particular search editor. These will be added to the right of the normal
     * ones.
     */
    protected abstract void addSpecialMenus(JMenuBar menuBar);

    /**
     * Executes the algorithm. The execution takes place inside a thread, so one cannot count on a result graph having
     * been found when the method
     */
    void execute() {
        class MyWatchedProcess extends WatchedProcess {
            public void watch() {
                try {
                    getExecuteButton().setEnabled(false);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }

//                setErrorMessage(null);

                if (!AbstractSearchEditor.this.knowledgeMessageShown) {
                    Parameters searchParams = getAlgorithmRunner().getParams();

                    if (searchParams != null) {
                        Knowledge knowledge = (Knowledge) searchParams.get("knowledge", new Knowledge());
                        if (!knowledge.isEmpty()) {
                            JOptionPane.showMessageDialog(
                                    JOptionUtils.centeringComp(),
                                    "Using previously set knowledge. (To edit, use " +
                                            "the Knowledge menu.)");
                            AbstractSearchEditor.this.knowledgeMessageShown = true;
                        }
                    }
                }


                try {
                    storeLatestWorkbenchGraph();
                    getAlgorithmRunner().execute();
                } catch (Exception e) {
                    CharArrayWriter writer1 = new CharArrayWriter();
                    PrintWriter writer2 = new PrintWriter(writer1);
                    e.printStackTrace(writer2);
                    String message = writer1.toString();
                    writer2.close();

                    e.printStackTrace(System.out);

                    TetradLogger.getInstance().error(message);

                    String messageString = e.getMessage();

                    if (e.getCause() != null) {
                        messageString = e.getCause().getMessage();
                    }

                    if (messageString == null) {
                        messageString = message;
                    }

//                    setErrorMessage(messageString);

                    TetradLogger.getInstance().error("************Algorithm stopped!");

                    getExecuteButton().setEnabled(true);
                    throw new RuntimeException(e);
                }

                Graph resultGraph = resultGraph();

                doDefaultArrangement(resultGraph);
                getWorkbench().setBackground(Color.WHITE);
                getWorkbench().setGraph(resultGraph);
                getGraphHistory().clear();
                getGraphHistory().add(resultGraph);
                getWorkbench().repaint();

                // For Mimbuild, e.g., that need to do a second stage.
                firePropertyChange("algorithmFinished", null, null);
                getExecuteButton().setEnabled(true);
                firePropertyChange("modelChanged", null, null);
            }
        }
        ;

        SwingUtilities.invokeLater(MyWatchedProcess::new);

//        Thread watcher = new Thread(() -> {
//            while (true) {
//                try {
//                    Thread.sleep(300);
//
//                    if (!process.isAlive()) {
//                        getExecuteButton().setEnabled(true);
//                        return;
//                    }
//                } catch (InterruptedException e) {
//                    getExecuteButton().setEnabled(true);
//                    return;
//                }
//            }
//        });

//        watcher.start();
    }

    void doPostExecutionSteps() {
        // Do nothing for abstract search editor.
    }

    protected abstract void doDefaultArrangement(Graph resultGraph);

    JButton getExecuteButton() {
        return this.executeButton;
    }

    //===========================PRIVATE METHODS==========================//

    AlgorithmRunner getAlgorithmRunner() {
        return this.algorithmRunner;
    }

    private Graph resultGraph() {
        Graph resultGraph = this.algorithmRunner.getGraph();

        if (resultGraph == null) {
            resultGraph = new EdgeListGraph();
        }

        return resultGraph;
    }

    /**
     * Sets up the editor, does the layout, and so on.
     */
    void setup(String resultLabel) {
        setLayout(new BorderLayout());
        add(getToolbar(), BorderLayout.WEST);
        add(workbenchScroll(), BorderLayout.CENTER);
        add(menuBar(), BorderLayout.NORTH);
    }

    JScrollPane workbenchScroll() {
        Graph resultGraph = resultGraph();

        Graph sourceGraph = this.algorithmRunner.getSourceGraph();
        Parameters searchParams = this.algorithmRunner.getParams();
        Graph latestWorkbenchGraph = null;

        if (searchParams != null) {
            latestWorkbenchGraph = (Graph) searchParams.get("sourceGraph", null);
        }

        if (latestWorkbenchGraph == null) {
            LayoutUtil.arrangeBySourceGraph(resultGraph, sourceGraph);
        } else {
            LayoutUtil.arrangeBySourceGraph(resultGraph, latestWorkbenchGraph);
        }

        this.workbench = new GraphWorkbench(resultGraph);

        this.graphHistory.clear();
        this.graphHistory.add(resultGraph);

        this.workbench.setAllowDoubleClickActions(false);
        this.workbench.setAllowNodeEdgeSelection(true);
        this.workbenchScroll = new JScrollPane(this.workbench);

        this.workbench.addMouseListener(new MouseAdapter() {
            public void mouseExited(MouseEvent e) {
                storeLatestWorkbenchGraph();
            }
        });

        return this.workbenchScroll;
    }

    /**
     * Creates the menubar for the search editor.
     */
    JMenuBar menuBar() {
        JMenuBar menuBar = new JMenuBar();
        JMenu file = new JMenu("File");
        menuBar.add(file);
        JMenu fileMenu = new GraphFileMenu(this, getWorkbench(), false);
        file.add(fileMenu);
        file.add(new SaveComponentImage(this.workbench, "Save Graph Image..."));

        JMenu edit = new JMenu("Edit");
        JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
        copy.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
        edit.add(copy);

        menuBar.add(edit);

        addSpecialMenus(menuBar);

        return menuBar;
    }

//     JMenuItem copy = new JMenuItem(new CopySubgraphAction(this));
//        JMenuItem paste = new JMenuItem(new PasteSubgraphAction(this));
//
//        copy.setAccelerator(
//                KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK));
//        paste.setAccelerator(
//                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK));
//
//        edit.add(copy);
//        edit.add(paste);

    String getResultLabel() {
        return this.resultLabel;
    }

    JScrollPane getWorkbenchScroll() {
        return this.workbenchScroll;
    }

    void setWorkbenchScroll(JScrollPane workbenchScroll) {
        this.workbenchScroll = workbenchScroll;
    }

    Graph getLatestWorkbenchGraph() {
        if (this.algorithmRunner.getParams() == null) {
            return null;
        }

        Graph graph = (Graph) this.algorithmRunner.getParams().get("sourceGraph", null);

        if (graph == null) {
            return this.algorithmRunner.getSourceGraph();
        }

        return graph;
    }

    void storeLatestWorkbenchGraph() {
        Graph latestWorkbenchGraph = this.workbench.getGraph();

        if (latestWorkbenchGraph.getNumNodes() == 0) {
            return;
        }

        Parameters searchParams = this.algorithmRunner.getParams();

        try {
            Graph graph = new MarshalledObject<>(latestWorkbenchGraph).get();

            if (graph == null) {
                throw new NullPointerException("Null graph");
            }

            if (searchParams != null) {
                searchParams.set("sourceGraph", graph);
            }
        } catch (IOException e) {
            e.printStackTrace();

            if (searchParams != null) {
                searchParams.set("sourceGraph", (Graph) null);
            }
        } catch (ClassNotFoundException e) {
            if (searchParams != null) {
                searchParams.set("sourceGraph", (Graph) null);
            }

            e.printStackTrace();
        }
    }

    GraphHistory getGraphHistory() {
        return this.graphHistory;
    }

    public IndTestType getTestType() {
        return (IndTestType) getAlgorithmRunner().getParams().get("indTestType", IndTestType.FISHER_Z);
    }

    public void setTestType(IndTestType testType) {
        getAlgorithmRunner().getParams().set("indTestType", testType);
    }

    public DataModel getDataModel() {
        return getAlgorithmRunner().getDataModel();
    }

    public Object getSourceGraph() {
        return getAlgorithmRunner().getParams().get("sourceGraph", null);
    }
}





