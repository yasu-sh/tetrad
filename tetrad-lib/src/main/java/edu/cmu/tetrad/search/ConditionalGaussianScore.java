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

package edu.cmu.tetrad.search;

import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Implements a conditional Gaussian BIC score for FGS.
 *
 * @author Joseph Ramsey
 */
public class ConditionalGaussianScore implements Score {

    private final DataSet dataSet;

    // The variables of the continuousData set.
    private final List<Node> variables;
    private final Map<Node, Integer> nodesHash;

    // Likelihood function
    private final ConditionalGaussianLikelihood likelihood;

    private double penaltyDiscount;
    private int numCategoriesToDiscretize = 3;
    private final double structurePrior;

    /**
     * Constructs the score using a covariance matrix.
     */
    public ConditionalGaussianScore(final DataSet dataSet, final double penaltyDiscount, final double structurePrior, final boolean discretize) {
        if (dataSet == null) {
            throw new NullPointerException();
        }

        this.dataSet = dataSet;
        this.variables = dataSet.getVariables();
        this.penaltyDiscount = penaltyDiscount;
        this.structurePrior = structurePrior;

        final Map<Node, Integer> nodesHash = new HashMap<>();

        for (int j = 0; j < this.variables.size(); j++) {
            nodesHash.put(this.variables.get(j), j);
        }

        this.nodesHash = nodesHash;

        this.likelihood = new ConditionalGaussianLikelihood(dataSet);

        this.likelihood.setNumCategoriesToDiscretize(this.numCategoriesToDiscretize);
        this.likelihood.setPenaltyDiscount(penaltyDiscount);
        this.likelihood.setDiscretize(discretize);
    }

    /**
     * Calculates the sample likelihood and BIC score for i given its parents in a simple SEM model
     */
    public double localScore(final int i, final int... parents) {
        final List<Integer> rows = getRows(i, parents);
        this.likelihood.setRows(rows);

        final ConditionalGaussianLikelihood.Ret ret = this.likelihood.getLikelihood(i, parents);

        final int N = this.dataSet.getNumRows();
        final double lik = ret.getLik();
        final int k = ret.getDof();

        return 2.0 * (lik + getStructurePrior(parents)) - getPenaltyDiscount() * k * Math.log(rows.size());
    }

    private List<Integer> getRows(final int i, final int[] parents) {
        final List<Integer> rows = new ArrayList<>();

        K:
        for (int k = 0; k < this.dataSet.getNumRows(); k++) {
            if (this.variables.get(i) instanceof DiscreteVariable) {
                if (this.dataSet.getInt(k, i) == -99) continue;
            } else if (this.variables.get(i) instanceof ContinuousVariable) {
                if (Double.isNaN(this.dataSet.getInt(k, i))) continue;
            }

            for (final int p : parents) {
                if (this.variables.get(i) instanceof DiscreteVariable) {
                    if (this.dataSet.getInt(k, p) == -99) continue K;
                } else if (this.variables.get(i) instanceof ContinuousVariable) {
                    if (Double.isNaN(this.dataSet.getInt(k, p))) continue K;
                }
            }

            rows.add(k);
        }

        return rows;
    }

    private double getStructurePrior(final int[] parents) {
        if (this.structurePrior <= 0) {
            return 0;
        } else {
            final int k = parents.length;
            final double n = this.dataSet.getNumColumns() - 1;
            final double p = this.structurePrior / n;
            return k * Math.log(p) + (n - k) * Math.log(1.0 - p);
        }
    }

    public double localScoreDiff(final int x, final int y, final int[] z) {
        return localScore(y, append(z, x)) - localScore(y, z);
    }

    @Override
    public double localScoreDiff(final int x, final int y) {
        return localScore(y, x) - localScore(y);
    }

    private int[] append(final int[] parents, final int extra) {
        final int[] all = new int[parents.length + 1];
        System.arraycopy(parents, 0, all, 0, parents.length);
        all[parents.length] = extra;
        return all;
    }

    /**
     * Specialized scoring method for a single parent. Used to speed up the effect edges search.
     */
    public double localScore(final int i, final int parent) {
        return localScore(i, new int[]{parent});
    }

    /**
     * Specialized scoring method for no parents. Used to speed up the effect edges search.
     */
    public double localScore(final int i) {
        return localScore(i, new int[0]);
    }

    public int getSampleSize() {
        return this.dataSet.getNumRows();
    }

    @Override
    public boolean isEffectEdge(final double bump) {
        return bump > 0;
    }

    @Override
    public List<Node> getVariables() {
        return this.variables;
    }

    @Override
    public Node getVariable(final String targetName) {
        for (final Node node : this.variables) {
            if (node.getName().equals(targetName)) {
                return node;
            }
        }

        return null;
    }

    @Override
    public int getMaxDegree() {
        return (int) Math.ceil(Math.log(this.dataSet.getNumRows()));
    }

    @Override
    public boolean determines(final List<Node> z, final Node y) {
        return false;
    }

    public double getPenaltyDiscount() {
        return this.penaltyDiscount;
    }

    public void setPenaltyDiscount(final double penaltyDiscount) {
        this.penaltyDiscount = penaltyDiscount;
    }

    public void setNumCategoriesToDiscretize(final int numCategoriesToDiscretize) {
        this.numCategoriesToDiscretize = numCategoriesToDiscretize;
    }

    @Override
    public String toString() {
        final NumberFormat nf = new DecimalFormat("0.00");
        return "Conditional Gaussian Score Penalty " + nf.format(this.penaltyDiscount);
    }
}



