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

package edu.cmu.tetrad.bayes;

import edu.cmu.tetrad.data.DiscreteVariable;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.TetradSerializable;

import java.text.NumberFormat;
import java.util.*;

/**
 * <p>Creates a table of stored cell probabilities for the given list of
 * variables. Since for a moderate number of variables and for a moderate number of values per variables this could get
 * to be a very large table, it might not be a good idea to use this class except for unit testing.&gt; 0
 *
 * @author josephramsey
 */

//////////////////////////////////////////////////////////////////////
// used also for MlBayesImObs to store the JPD of observed variables
//////////////////////////////////////////////////////////////////////

public final class StoredCellProbsObs implements TetradSerializable, DiscreteProbs {
    static final long serialVersionUID = 23L;

    private final List<Node> variables;
    private final int[] parentDims;
    private final double[] probs;


    //============================CONSTRUCTORS============================//

    public StoredCellProbsObs(List<Node> variables) {
        if (variables == null) {
            throw new NullPointerException();
        }

        for (Object variable : variables) {
            if (variable == null) {
                throw new NullPointerException();
            }

            if (!(variable instanceof DiscreteVariable)) {
                throw new IllegalArgumentException(
                        "Not a discrete variable: " + variable.getClass());
            }
        }

        this.variables = Collections.unmodifiableList(variables);
        Set<Object> variableSet = new HashSet<>(this.variables);
        if (variableSet.size() < this.variables.size()) {
            throw new IllegalArgumentException("Duplicate variable.");
        }

        this.parentDims = new int[getVariables().size()];

        for (int i = 0; i < getVariables().size(); i++) {
            DiscreteVariable var = (DiscreteVariable) getVariables().get(i);
            this.parentDims[i] = var.getNumCategories();
        }

        int numCells = 1;

        for (int parentDim : this.parentDims) {
            if (numCells > 1000000 /* Integer.MAX_VALUE / dim*/) {
                throw new IllegalArgumentException(
                        "The number of rows in the " +
                                "probability table " +
                                " is greater than 1,000,000 and cannot be " +
                                "represented.");
            }
            numCells *= parentDim;
        }

        this.probs = new double[numCells];
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static StoredCellProbsObs serializableInstance() {
        return new StoredCellProbsObs(new ArrayList<>());
    }

    // clear the probability table
    public void clearCellTable() {
        Arrays.fill(this.probs, Double.NaN);
    }

    // get vaues by marginalizing probabilities from allowUnfaithfulness bayesIm
    public void createCellTable(MlBayesIm bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        BayesImProbs cellProbsOnTheFly = new BayesImProbs(bayesIm);

        for (int i = 0; i < this.probs.length; i++) {
            int[] variableValues = getVariableValues(i);

            Proposition targetProp = Proposition.tautology(bayesIm);
            for (int j = 0; j < variableValues.length; j++) {
                String nodeName = getVariables().get(j).getName();
                Node node = bayesIm.getNode(nodeName);
                targetProp.setCategory(bayesIm.getNodeIndex(node),
                        variableValues[j]);
            }

            this.probs[i] = cellProbsOnTheFly.getProb(targetProp);
        }
    }

    // copy from another MlBayesImObs
    public void createCellTable(MlBayesImObs bayesIm) {
        if (bayesIm == null) {
            throw new NullPointerException();
        }

        for (int i = 0; i < this.probs.length; i++) {
            this.probs[i] = bayesIm.getProbability(i);
        }
    }

    //=============================PUBLIC METHODS=========================//

    /**
     * @return the probability for the given cell, specified as a particular combination of variable values, for the
     * list of variables (in order) returned by get
     */
    public double getCellProb(int[] variableValues) {
        return this.probs[getOffset(variableValues)];
    }

    public double getProb(Proposition assertion) {

        // Initialize to 0's.
        int[] variableValues = new int[assertion.getNumVariables()];

        for (int i = 0; i < assertion.getNumVariables(); i++) {
            variableValues[i] = StoredCellProbsObs.nextValue(assertion, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double p = 0.0;

        loop:
        while (true) {
            for (int i = assertion.getNumVariables() - 1; i >= 0; i--) {
                if (StoredCellProbsObs.hasNextValue(assertion, i, variableValues[i])) {
                    variableValues[i] =
                            StoredCellProbsObs.nextValue(assertion, i, variableValues[i]);

                    for (int j = i + 1; j < assertion.getNumVariables(); j++) {
                        if (StoredCellProbsObs.hasNextValue(assertion, j, -1)) {
                            variableValues[j] = StoredCellProbsObs.nextValue(assertion, j, -1);
                        } else {
                            break loop;
                        }
                    }

                    double cellProb = getCellProb(variableValues);

                    if (Double.isNaN(cellProb)) {
                        continue;
                    }

                    p += cellProb;
                    continue loop;
                }
            }

            break;
        }

        return p;
    }

    private static boolean hasNextValue(Proposition proposition, int variable,
                                        int curIndex) {
        return StoredCellProbsObs.nextValue(proposition, variable, curIndex) != -1;
    }

    private static int nextValue(Proposition proposition, int variable,
                                 int curIndex) {
        for (int i = curIndex + 1;
             i < proposition.getNumCategories(variable); i++) {
            if (proposition.isAllowed(variable, i)) {
                return i;
            }
        }

        return -1;
    }

    public double getConditionalProb(Proposition assertion,
                                     Proposition condition) {
        if (assertion.getVariableSource() != condition.getVariableSource()) {
            throw new IllegalArgumentException(
                    "Assertion and condition must be " +
                            "for the same Bayes IM.");
        }

        // Initialize to 0's.
        int[] variableValues = new int[condition.getNumVariables()];

        for (int i = 0; i < condition.getNumVariables(); i++) {
            variableValues[i] = StoredCellProbsObs.nextValue(condition, i, -1);
        }

        variableValues[variableValues.length - 1] = -1;
        double conditionTrue = 0.0;
        double assertionTrue = 0.0;

        loop:
        while (true) {
            for (int i = condition.getNumVariables() - 1; i >= 0; i--) {
                if (StoredCellProbsObs.hasNextValue(condition, i, variableValues[i])) {
                    variableValues[i] =
                            StoredCellProbsObs.nextValue(condition, i, variableValues[i]);

                    for (int j = i + 1; j < condition.getNumVariables(); j++) {
                        if (StoredCellProbsObs.hasNextValue(condition, j, -1)) {
                            variableValues[j] = StoredCellProbsObs.nextValue(condition, j, -1);
                        } else {
                            break loop;
                        }
                    }

                    double cellProb = getCellProb(variableValues);
                    boolean assertionHolds = true;

                    for (int j = 0; j < assertion.getNumVariables(); j++) {
                        if (!assertion.isAllowed(j, variableValues[j])) {
                            assertionHolds = false;
                            break;
                        }
                    }

                    if (assertionHolds) {
                        assertionTrue += cellProb;
                    }

                    conditionTrue += cellProb;
                    continue loop;
                }
            }

            break;
        }

        return assertionTrue / conditionTrue;
    }

    public List<Node> getVariables() {
        return this.variables;
    }

    public int getNumRows() {
        return this.probs.length;
    }

    public String toString() {
        StringBuilder buf = new StringBuilder();
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        buf.append("\nCell Probabilities:");

        buf.append("\n");

        for (Node variable : this.variables) {
            buf.append(variable).append("\t");
        }

        double sum = 0.0;
        final int maxLines = 500;

        for (int i = 0; i < this.probs.length; i++) {
            if (i >= maxLines) {
                buf.append("\nCowardly refusing to print more than ")
                        .append(maxLines).append(" lines.");
                break;
            }

            buf.append("\n");

            int[] variableValues = getVariableValues(i);

            for (int variableValue : variableValues) {
                buf.append(variableValue).append("\t");
            }

            buf.append(nf.format(this.probs[i]));
            sum += this.probs[i];
        }

        buf.append("\n\nSum = ").append(nf.format(sum));

        return buf.toString();
    }

    public int[] getVariableValues(int rowIndex) {
        int[] dims = getParentDims();
        int[] values = new int[dims.length];

        for (int i = dims.length - 1; i >= 0; i--) {
            values[i] = rowIndex % dims[i];
            rowIndex /= dims[i];
        }

        return values;
    }

    ///////////////////////////////////////////////////////////
    // Sets the cell probability. 
    // No guarantee the probabilities will add to 1.0 if they're
    // set one at a time.
    //
    public void setCellProbability(int[] variableValues, double probability) {
        if (probability < 0.0 || probability > 1.0) {
            throw new IllegalArgumentException(
                    "Probability not in [0.0, 1.0]: " + probability);
        }

        this.probs[getOffset(variableValues)] = probability;
    }

    //============================PRIVATE METHODS==========================//

    /**
     * @return the row in the table for the given node and combination of parent values.
     */
    private int getOffset(int[] values) {
        int[] dim = getParentDims();
        int offset = 0;

        for (int i = 0; i < dim.length; i++) {
            if (values[i] < 0 || values[i] >= dim[i]) {
                throw new IllegalArgumentException();
            }

            offset *= dim[i];
            offset += values[i];
        }

        return offset;
    }

    /**
     * @return an array containing the number of values, in order, of each variable.
     */
    private int[] getParentDims() {
        return this.parentDims;
    }

}





