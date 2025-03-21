package edu.cmu.tetradapp.editor;

import edu.cmu.tetrad.graph.Edge;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetradapp.workbench.DisplayNode;
import edu.cmu.tetradapp.workbench.GraphWorkbench;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.Transferable;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.Set;

/**
 * Jul 23, 2018 4:05:07 PM
 *
 * @author Chirayu Kong Wongchokprasitti, PhD (chw20@pitt.edu)
 */
public class HideShowNoConnectionNodesAction extends AbstractAction implements ClipboardOwner {

    private static final long serialVersionUID = 1843073951524699538L;

    /**
     * The desktop containing the target session editor.
     */
    private final GraphWorkbench workbench;

    public HideShowNoConnectionNodesAction(GraphWorkbench workbench) {
        super("Hide/Show No Connections Node");

        if (workbench == null) {
            throw new NullPointerException("Desktop must not be null.");
        }

        this.workbench = workbench;
    }


    @Override
    public void actionPerformed(ActionEvent e) {
        Graph graph = this.workbench.getGraph();
        for (Component comp : this.workbench.getComponents()) {
            if (comp instanceof DisplayNode) {
                Node node = ((DisplayNode) comp).getModelNode();
                List<Edge> edges = graph.getEdges(node);
                if (edges == null || edges.isEmpty()) {
                    comp.setVisible(!comp.isVisible());
                }
            }
        }
    }

    @Override
    public void lostOwnership(Clipboard clipboard, Transferable contents) {

    }

}
