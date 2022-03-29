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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.graph.GraphUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.NodeType;

import java.util.*;

import static java.lang.Math.abs;

/**
 * Utility for reidentifying variables for multiple indicator structure searches.
 */
public class ReidentifyVariables {
    // This reidentifies a variable if all of its members belong to one of the clusters
    // in the original graph.
    public static List<String> reidentifyVariables1(final List<List<Node>> partition, final Graph trueGraph) {
        final List<String> names = new ArrayList<>();
        Node latent;

        for (final List<Node> _partition : partition) {
            boolean added = false;

            for (final Node _latent : trueGraph.getNodes()) {
                final List<Node> trueChildren = trueGraph.getChildren(_latent);

                for (final Node node2 : new ArrayList<>(trueChildren)) {
                    if (node2.getNodeType() == NodeType.LATENT) {
                        trueChildren.remove(node2);
                    }
                }

                boolean containsAll = true;
                latent = _latent;

                for (final Node child : _partition) {
                    boolean contains = false;

                    for (final Node _child : trueChildren) {
                        if (child.getName().equals(_child.getName())) {
                            contains = true;
                            break;
                        }
                    }

                    if (!contains) {
                        containsAll = false;
                        break;
                    }
                }

                if (containsAll) {
                    String name = latent.getName();

                    while (names.contains(name)) {
                        name += "*";
                    }

                    names.add(name);

                    for (final Node child : _partition) {
                        if (!_partition.contains(child)) {
                            _partition.add(child);
                        }
                    }

                    added = true;
                    break;
                }
            }

            if (!added) {
                String name = "M*";

                while (names.contains(name)) {
                    name += "*";
                }

                names.add(name);

                for (final Node child : _partition) {
                    if (!_partition.contains(child)) {
                        _partition.add(child);
                    }
                }
            }
        }

        return names;
    }

    // This reidentifies a variable in the output with a variable in the input if the sum of the
    // factor loadings for the output clusters on the input's loadings is greater than for
    // any other input latent.
    public static List<String> reidentifyVariables2(final List<List<Node>> clusters, Graph trueGraph, final DataSet data) {
        trueGraph = GraphUtils.replaceNodes(trueGraph, data.getVariables());
        final Map<Node, SemIm> ims = new HashMap<>();
        final List<String> latentNames = new ArrayList<>();

        for (final Node node : trueGraph.getNodes()) {
            if (node.getNodeType() != NodeType.LATENT) continue;

            final List<Node> children = trueGraph.getChildren(node);
            children.removeAll(ReidentifyVariables.getLatents(trueGraph));

            final List<Node> all = new ArrayList<>();
            all.add(node);
            all.addAll(children);

            final Graph subgraph = trueGraph.subgraph(all);

            final SemPm pm = new SemPm(subgraph);
            pm.fixOneLoadingPerLatent();

            final SemOptimizer semOptimizer = new SemOptimizerPowell();
            final SemEstimator est = new SemEstimator(data, pm, semOptimizer);
            est.setScoreType(ScoreType.Fgls);
            final SemIm im = est.estimate();

            ims.put(node, im);
        }

        final Map<List<Node>, String> clustersToNames = new HashMap<>();


//        Graph reidentifiedGraph = new EdgeListGraph();

        for (final List<Node> cluster : clusters) {
            double maxSum = Double.NEGATIVE_INFINITY;
            Node maxLatent = null;

            for (final Node _latent : trueGraph.getNodes()) {
                if (_latent.getNodeType() != NodeType.LATENT) {
                    continue;
                }

                final double sum = ReidentifyVariables.sumOfAbsLoadings(cluster, _latent, trueGraph, ims);

                if (sum > maxSum) {
                    maxSum = sum;
                    maxLatent = _latent;
                }
            }

            if (maxLatent == null) {
                throw new NullPointerException("No such latent");
            }

            final String name = maxLatent.getName();
            latentNames.add(name);
            clustersToNames.put(cluster, name);
        }


        final Set<String> values = new HashSet<>(clustersToNames.values());

        for (final String key : values) {
            final double maxSum = Double.NEGATIVE_INFINITY;
            List<Node> maxCluster = null;

            for (final List<Node> _cluster : clustersToNames.keySet()) {
                if (clustersToNames.get(_cluster).equals(key)) {
                    final double sum = ReidentifyVariables.sumOfAbsLoadings(_cluster, trueGraph.getNode(key), trueGraph, ims);
                    if (sum > maxSum) {
                        maxCluster = _cluster;
                    }
                }
            }

            for (final List<Node> _cluster : clustersToNames.keySet()) {
                if (clustersToNames.get(_cluster).equals(key)) {
                    if (!_cluster.equals(maxCluster)) {
                        String name = key;

                        while (latentNames.contains(name)) {
                            name = name + "*";
                        }

                        clustersToNames.put(_cluster, name);
                        latentNames.set(clusters.indexOf(_cluster), name);
                    }
                }
            }
        }

        return latentNames;
    }

    private static double sumOfAbsLoadings(final List<Node> searchChildren, final Node latent, final Graph mim, final Map<Node, SemIm> ims) {
        double sum = 0.0;

//        System.out.println(latent + " " + searchChildren + " " + mim.getChildren(latent));

        for (final Node child : searchChildren) {
            if (mim.isParentOf(latent, child)) {
                final SemIm im = ims.get(latent);
                final double coef = im.getEdgeCoef(latent, child);
                sum += abs(coef);
            }
        }

        return sum;
    }

    public static List<Node> getLatents(final Graph graph) {
        final List<Node> latents = new ArrayList<>();
        for (final Node node : graph.getNodes()) if (node.getNodeType() == NodeType.LATENT) latents.add(node);
        return latents;
    }
}



