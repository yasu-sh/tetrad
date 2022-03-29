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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.*;
import edu.cmu.tetrad.regression.Regression;
import edu.cmu.tetrad.regression.RegressionCovariance;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.util.Vector;
import edu.cmu.tetrad.util.*;
import edu.cmu.tetrad.util.dist.Distribution;
import edu.cmu.tetrad.util.dist.Split;
import org.apache.commons.math3.distribution.ChiSquaredDistribution;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.rmi.MarshalledObject;
import java.util.*;

import static java.lang.Math.sqrt;

/**
 * Stores an instantiated structural equation model (SEM), with error covariance
 * terms, possibly cyclic, suitable for estimation and simulation. For
 * estimation, the maximum likelihood fitting function and the negative log
 * likelihood function (Bollen 1989, p. 109) are calculated; these can be
 * maximized by an estimator to estimate optimal parameter values. The values of
 * freeParameters are set as indicated in their corresponding Parameter objects
 * as initial values for estimation. Provides multiple ways to get and set the
 * values of free freeParameters. For simulation, cyclic and acyclic methods are
 * provided; the cyclic method is used by default, although the acyclic method
 * is considerably faster for large data sets.
 * <p>
 * Let V be the set of variables in the model. The freeParameters of the model
 * are as follows: (a) the list of linear coefficients for all edges u-->v in
 * the model, where u, v are in V, (b) the list of variances for all variables
 * in V, (c) the list of all error covariances d<-->e, where d an e are
 * exogenous terms in the model (either exogenous variables or error terms for
 * endogenous variables), and (d) the list of means for all variables in V.
 * <p>
 * It is important to note that the likelihood functions this class calculates
 * do not depend on variable means. They depend only on edge coefficients and
 * error covariances. Hence, variable means are treated differently from edge
 * coefficients and error covariances in the model.
 * <p>
 * Reference: Bollen, K. A. (1989). Structural Equations with Latent Variables.
 * New York: John Wiley & Sons.
 *
 * @author Frank Wimberly
 * @author Ricardo Silva
 * @author Joseph Ramsey
 */
public final class SemIm implements IM, ISemIm, TetradSerializable {

    static final long serialVersionUID = 23L;
    private static Collection<? extends String> parameterNames;
    /**
     * The Sem PM containing the graph and the freeParameters to be estimated.
     * For now a defensive copy of this is not being constructed, since it is
     * not used anywhere in the code except in the the constructor and in its
     * accessor method. It somebody changes it, it's their own fault, but it
     * won't affect this class.
     *
     * @serial Cannot be null.
     */
    private final SemPm semPm;
    /**
     * The list of measured and latent variableNodes for the semPm.
     * (Unmodifiable.)
     *
     * @serial Cannot be null.
     */
    private final List<Node> variableNodes;
    /**
     * The list of measured variableNodes from the semPm. (Unmodifiable.)
     *
     * @serial Cannot be null.
     */
    private final List<Node> measuredNodes;
    /**
     * Matrix of edge coefficients. edgeCoefC[i][j] is the coefficient of the
     * edge from getVariableNodes().get(i) to getVariableNodes().get(j), or 0.0
     * if this edge is not in the graph. The values of these may be changed, but
     * the array itself may not.
     *
     * @serial Cannot be null.
     */
    private final Matrix edgeCoef;
    /**
     * Standard Deviations of means. Needed to calculate standard errors.
     *
     * @serial Cannot be null.
     */
    private final double[] variableMeansStdDev;
    /**
     * The list of free freeParameters (Unmodifiable). This must be in the same
     * order as this.freeMappings.
     *
     * @serial Cannot be null.
     */
    private List<Parameter> freeParameters;
    /**
     * The list of fixed freeParameters (Unmodifiable). This must be in the same
     * order as this.fixedMappings.
     *
     * @serial Cannot be null.
     */
    private List<Parameter> fixedParameters;
    /**
     * The list of mean freeParameters (Unmodifiable). This must be in the same
     * order as variableMeans.
     */
    private List<Parameter> meanParameters;
    /**
     * Matrix of error covariances. errCovar[i][j] is the covariance of the
     * error term of getExoNodes().get(i) and getExoNodes().get(j), with the
     * special case (duh!) that errCovar[i][i] is the variance of
     * getExoNodes.get(i). The values of these may be changed, but the array
     * itself may not.
     *
     * @serial Cannot be null.
     */
    private Matrix errCovar;
    /**
     * Means of variables. These will not be counted for purposes of calculating
     * degrees of freedom, since the increase in dof is exactly balanced by a
     * decrease in dof.
     *
     * @serial Cannot be null.
     */
    private double[] variableMeans;
    /**
     * Replaced by sampleCovar. Please do not delete. Required for serialization
     * backward compatibility.
     *
     * @serial
     */
    private Matrix sampleCovarC;
    /**
     * The variable of the sample covariance.
     */
    private List<Node> sampleCovarVariables;
    /**
     * The sample size.
     *
     * @serial Range >= 0.
     */
    private int sampleSize;
    /**
     * Replaced by implCovarC. Please do not delete. Required for serialization
     * backward compatibility.
     *
     * @serial
     */
    private Matrix implCovar;
    /**
     * The list of freeMappings. This is an unmodifiable list. It is fixed (up
     * to order) by the SemPm. This must be in the same order as
     * this.freeParameters.
     *
     * @serial Cannot be null.
     */
    private List<Mapping> freeMappings;
    /**
     * The list of fixed freeParameters (Unmodifiable). This must be in the same
     * order as this.fixedParameters.
     *
     * @serial Cannot be null.
     */
    private List<Mapping> fixedMappings;
    /**
     * Stores the standard errors for the freeParameters. May be null.
     *
     * @serial Can be null.
     */
    private double[] standardErrors;
    /**
     * True iff setting freeParameters to out-of-bound values throws exceptions.
     *
     * @serial Any value.
     */
    private boolean parameterBoundsEnforced = true;

//    /**
//     * Used for some linear algebra calculations.
//     */
//    private transient TetradAlgebra algebra;
    /**
     * True iff this SemIm is estimated.
     *
     * @serial Any value.
     */
    private boolean estimated = false;
    /**
     * True just in case the graph for the SEM is cyclic.
     */
    private boolean cyclic;

//    /**
//     * Caches the log determinant of the sample covariance matrix.
//     */
//    private transient double logDetSample;
    /**
     * True just in case cyclicity has already been checked.
     */
    private boolean cyclicChecked = false;

//    /**
//     * True if positive definiteness should be checked when optimizing the
//     * FML function. (It's checked other places.)
//     */
//    private boolean checkPositiveDefinite;
    /**
     * Parameters to help guide how values are chosen for freeParameters.
     */
    private Parameters params = new Parameters();
    /**
     * Stores a distribution for each variable. Initialized to N(0, 1) for each.
     */
    private Map<Node, Distribution> distributions;
    /**
     * Stores the connection functions of specified nodes.
     */
    private Map<Node, ConnectionFunction> functions;
    /**
     * True iff only positive data should be simulated.
     */
    private boolean simulatedPositiveDataOnly = false;
    private Map<Node, Integer> variablesHash;
    private Matrix sampleCovInv;
    // Types of scores that yield a chi square value when minimized.
//    public enum ScoreType {
//        Fml, Fgls
//    }
    private ScoreType scoreType = ScoreType.Fml;
    private int errorType = 1; // 1 = Normal, 2 = Uniform, 3 = Exponential, 4 = Gumbel
    private double errorParam1 = 0.0;
    private double errorParam2 = 1.0;

    /**
     * Constructs a new SEM IM from a SEM PM.
     */
    public SemIm(final SemPm semPm) {
        this(semPm, null, new Parameters());
    }

    //=============================CONSTRUCTORS============================//

    /**
     * Constructs a new SEM IM from the given SEM PM, using the given params
     * object to guide the choice of parameter values.
     */
    public SemIm(final SemPm semPm, final Parameters params) {
        this(semPm, null, params);
    }

    /**
     * Constructs a new SEM IM from the given SEM PM, using the old SEM IM and
     * params object to guide the choice of parameter values. If old values are
     * retained, they are gotten from the old SEM IM.
     */
    public SemIm(final SemPm semPm, final SemIm oldSemIm, final Parameters parameters) {
        if (semPm == null) {
            throw new NullPointerException("Sem PM must not be null.");
        }

        if (this.params == null) {
            throw new NullPointerException();
        }

        this.params = parameters;

        this.sampleSize = parameters.getInt(Params.SAMPLE_SIZE);

        this.semPm = new SemPm(semPm);

        this.variableNodes
                = Collections.unmodifiableList(semPm.getVariableNodes());
        this.measuredNodes
                = Collections.unmodifiableList(semPm.getMeasuredNodes());

        final int numVars = this.variableNodes.size();

        this.edgeCoef = new Matrix(numVars, numVars);
        this.errCovar = new Matrix(numVars, numVars);
        this.variableMeans = new double[numVars];
        this.variableMeansStdDev = new double[numVars];

        this.freeParameters = initFreeParameters();
        this.freeMappings = createMappings(getFreeParameters());
        this.fixedParameters = initFixedParameters();
        this.fixedMappings = createMappings(getFixedParameters());
        this.meanParameters = initMeanParameters();

        // Set variable means to 0.0 pending the program creating the SemIm
        // setting them. I.e. by default they are set to 0.0.
        for (int i = 0; i < numVars; i++) {
            this.variableMeans[i] = 0;
            this.variableMeansStdDev[i] = Double.NaN;
        }

        initializeValues();

        // Note that we want to use the default params object here unless a bona fide
        // subistute has been provided.
        if (oldSemIm != null && this.getParams().getBoolean("retainPreviousValues", false)) {
            retainPreviousValues(oldSemIm);
        }

        this.distributions = new HashMap<>();

        // Careful with this! These must not be left in! They override the
        // normal behavior!
//        for (Node node : variableNodes) {
//            this.distributions.put(node, new Uniform(-1, 1));
//        }


        this.errorType = parameters.getInt(Params.SIMULATION_ERROR_TYPE);
        this.errorParam1 = parameters.getDouble(Params.SIMULATION_PARAM1);
        this.errorParam2 = parameters.getDouble(Params.SIMULATION_PARAM2);

        this.functions = new HashMap<>();
    }

    /**
     * Constructs a SEM model using the given SEM PM and sample covariance
     * matrix.
     */
    public SemIm(final SemPm semPm, final ICovarianceMatrix covMatrix) {
        this(semPm);
        setCovMatrix(covMatrix);
    }

    /**
     * Special hidden constructor to generate updated models.
     */
    private SemIm(final SemIm semIm, final Matrix covariances,
                  final Vector means) {
        this(semIm);

        if (covariances.rows() != covariances.columns()) {
            throw new IllegalArgumentException(
                    "Expecting covariances to be square.");
        }

        if (!MatrixUtils.isPositiveDefinite(covariances)) {
            throw new IllegalArgumentException("Covariances must be symmetric "
                    + "positive definite.");
        }

        if (means.size() != this.semPm.getVariableNodes().size()) {
            throw new IllegalArgumentException(
                    "Number of means does not equal " + "number of variables.");
        }

        if (covariances.rows() != this.semPm.getVariableNodes().size()) {
            throw new IllegalArgumentException(
                    "Dimension of covariance matrix "
                            + "does not equal number of variables.");
        }

        this.errCovar = covariances;
        this.variableMeans = means.toArray();

        this.freeParameters = initFreeParameters();
        this.freeMappings = createMappings(getFreeParameters());
        this.fixedParameters = initFixedParameters();
        this.fixedMappings = createMappings(getFixedParameters());
    }

    /**
     * Copy constructor.
     *
     * @throws RuntimeException if the given SemIm cannot be serialized and
     *                          deserialized correctly.
     */
    public SemIm(final SemIm semIm) {
        try {

            // We make a deep copy of semIm and then copy all of its fields
            // into this SEM IM. Otherwise, it's just too HARD to make a deep copy!
            // (Complain, complain.) jdramsey 4/20/2005
            final SemIm _semIm = new MarshalledObject<>(semIm).get();

            this.semPm = _semIm.semPm;
            this.variableNodes = _semIm.variableNodes;
            this.measuredNodes = _semIm.measuredNodes;
            this.freeParameters = _semIm.freeParameters;
            this.fixedParameters = _semIm.fixedParameters;
            this.meanParameters = _semIm.meanParameters;
            this.edgeCoef = _semIm.edgeCoef;
            this.errCovar = _semIm.errCovar;
            this.variableMeans = _semIm.variableMeans;
            this.variableMeansStdDev = _semIm.variableMeansStdDev;
            this.sampleCovarC = _semIm.sampleCovarC;
            this.sampleSize = _semIm.sampleSize;
            this.implCovar = _semIm.implCovar;
            this.freeMappings = _semIm.freeMappings;
            this.fixedMappings = _semIm.fixedMappings;
            this.standardErrors = _semIm.standardErrors;
            this.parameterBoundsEnforced = _semIm.parameterBoundsEnforced;
            this.estimated = _semIm.estimated;
            this.cyclic = _semIm.cyclic;
            this.distributions = new HashMap<>(_semIm.distributions);
            this.scoreType = _semIm.scoreType;
        } catch (final IOException | ClassNotFoundException e) {
            throw new RuntimeException("SemIm could not be deep cloned.", e);
        }
    }

    public static List<String> getParameterNames() {
        final List<String> parameters = new ArrayList<>();
        parameters.add("coefLow");
        parameters.add("coefHigh");
        parameters.add("covLow");
        parameters.add("covHigh");
        parameters.add("varLow");
        parameters.add("varHigh");
        parameters.add("coefSymmetric");
        parameters.add("covSymmetric");
        return parameters;
    }

    /**
     * Constructs a new SEM IM with the given graph, retaining parameter values
     * from <code>semIm</code> for nodes of the same name and edges connecting
     * nodes of the same names.
     *
     * @param semIm The old SEM IM.
     * @param graph The graph for the new SEM IM.
     * @return The new SEM IM, retaining values from <code>semIm</code>.
     */
    public static SemIm retainValues(final SemIm semIm, final SemGraph graph) {
        final SemPm newSemPm = new SemPm(graph);
        final SemIm newSemIm = new SemIm(newSemPm);

        for (final Parameter p1 : newSemIm.getSemPm().getParameters()) {
            final Node nodeA = semIm.getSemPm().getGraph().getNode(p1.getNodeA().getName());
            final Node nodeB = semIm.getSemPm().getGraph().getNode(p1.getNodeB().getName());

            for (final Parameter p2 : semIm.getSemPm().getParameters()) {
                if (p2.getNodeA() == nodeA && p2.getNodeB() == nodeB && p2.getType() == p1.getType()) {
                    newSemIm.setParamValue(p1, semIm.getParamValue(p2));
                }
            }
        }

        newSemIm.sampleSize = semIm.sampleSize;
        return newSemIm;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static SemIm serializableInstance() {
        return new SemIm(SemPm.serializableInstance());
    }

    /**
     * @return a variant of the getModel model with the given covariance matrix
     * and means. Used for updating.
     */
    public SemIm updatedIm(final Matrix covariances, final Vector means) {
        return new SemIm(this, covariances, means);
    }

    //==============================PUBLIC METHODS=========================//

    /**
     * Sets the sample covariance matrix for this Sem as a submatrix of the
     * given matrix. The variable names used in the SemPm for this model must
     * all appear in this CovarianceMatrix.
     */
    public void setCovMatrix(final ICovarianceMatrix covMatrix) {
        if (covMatrix == null) {
            throw new NullPointerException(
                    "Covariance matrix must not be null.");
        }

        final ICovarianceMatrix covMatrix2 = fixVarOrder(covMatrix);
        this.sampleCovarC = covMatrix2.getMatrix().copy();
        this.sampleCovarVariables = covMatrix2.getVariables();
        this.sampleSize = covMatrix2.getSampleSize();
        this.sampleCovInv = null;

        if (this.sampleSize < 0) {
            throw new IllegalArgumentException(
                    "Sample size out of range: " + this.sampleSize);
        }
    }

    /**
     * Calculates the covariance matrix of the given DataSet and sets the sample
     * covariance matrix for this model to a subset of it. The measured variable
     * names used in the SemPm for this model must all appear in this data set.
     */
    public void setDataSet(final DataSet dataSet) {
        setCovMatrix(new CovarianceMatrix(dataSet));
    }

    /**
     * @return the Digraph which describes the causal structure of the Sem.
     */
    public SemPm getSemPm() {
        return this.semPm;
    }

    /**
     * @return an array containing the getModel values for the free
     * freeParameters, in the order in which the freeParameters appear in
     * getFreeParameters(). That is, getFreeParamValues()[i] is the value for
     * getFreeParameters()[i].
     */
    public double[] getFreeParamValues() {
        final double[] paramValues = new double[freeMappings().size()];

        for (int i = 0; i < freeMappings().size(); i++) {
            final Mapping mapping = freeMappings().get(i);
            paramValues[i] = mapping.getValue();
        }

        return paramValues;
    }

    /**
     * Sets the values of the free freeParameters (in the order in which they
     * appear in getFreeParameters()) to the values contained in the given
     * array. That is, params[i] is the value for getFreeParameters()[i].
     */
    public void setFreeParamValues(final double[] params) {
        if (params.length != getNumFreeParams()) {
            throw new IllegalArgumentException("The array provided must be "
                    + "of the same length as the number of free parameters.");
        }

        for (int i = 0; i < freeMappings().size(); i++) {
            final Mapping mapping = freeMappings().get(i);
            mapping.setValue(params[i]);
        }
    }

    /**
     * Gets the value of a single free parameter, or Double.NaN if the parameter
     * is not in this
     *
     * @throws IllegalArgumentException if the given parameter is not a free
     *                                  parameter in this model.
     */
    public double getParamValue(final Parameter parameter) {
        if (parameter == null) {
            throw new NullPointerException();
        }

        // This is evil. jdramsey 12/3/2015
//        Node nodeA = parameter.getNodeA();
//        Node nodeB = parameter.getNodeB();
//        Node parent;
//        Node child;
//
//        Graph graph = getSemPm().getGraph();
//
//        if (graph.isParentOf(nodeA, nodeB)) {
//            parent = nodeA;
//            child = nodeB;
//        } else {
//            parent = nodeB;
//            child = nodeA;
//        }
//
//        List<Node> parents = graph.getParents(child);
//
//        if (parameter.getNodeA() != parameter.getNodeB() && sampleSize > 1
//                && measuredNodes.contains(child) && measuredNodes.containsAll(parents)) {
//            TetradMatrix sampleCovar = getSampleCovar();
//            CovarianceMatrix cov = new CovarianceMatrix(measuredNodes, sampleCovar, sampleSize);
//            Regression regression = new RegressionCovariance(cov);
//            RegressionResult result = regression.regress(child, parents);
////            double[] se = result.getSe();
//            double[] coef = result.getCoef();
//            return coef[parents.indexOf(parent) + 1];
//        }
        if (getFreeParameters().contains(parameter)) {
            final int index = getFreeParameters().indexOf(parameter);
            final Mapping mapping = this.freeMappings.get(index);
            return mapping.getValue();
        } else if (getFixedParameters().contains(parameter)) {
            final int index = getFixedParameters().indexOf(parameter);
            final Mapping mapping = this.fixedMappings.get(index);
            return mapping.getValue();
        } else if (getMeanParameters().contains(parameter)) {
            final int index = getMeanParameters().indexOf(parameter);
            return this.variableMeans[index];
        }

//        return Double.NaN;
        throw new IllegalArgumentException(
                "Not a parameter in this model: " + parameter);
    }

    /**
     * Sets the value of a single free parameter to the given value.
     *
     * @throws IllegalArgumentException if the given parameter is not a free
     *                                  parameter in this model.
     */
    public void setParamValue(final Parameter parameter, final double value) {
        if (getFreeParameters().contains(parameter)) {
            // Note this assumes the freeMappings are in the same order as the
            // free freeParameters.                                        get
            final int index = getFreeParameters().indexOf(parameter);
            final Mapping mapping = this.freeMappings.get(index);
            mapping.setValue(value);
        } else if (getMeanParameters().contains(parameter)) {
            final int index = getMeanParameters().indexOf(parameter);
            this.variableMeans[index] = value;
        } else {
            throw new IllegalArgumentException("That parameter cannot be set in "
                    + "this model: " + parameter);
        }
    }

    /**
     * Sets the value of a single free parameter to the given value.
     *
     * @throws IllegalArgumentException if the given parameter is not a free
     *                                  parameter in this model.
     */
    public void setFixedParamValue(final Parameter parameter, final double value) {
        if (!getFixedParameters().contains(parameter)) {
            throw new IllegalArgumentException(
                    "Not a fixed parameter in " + "this model: " + parameter);
        }

        // Note this assumes the fixedMappings are in the same order as the
        // fixed freeParameters.
        final int index = getFixedParameters().indexOf(parameter);
        final Mapping mapping = this.fixedMappings.get(index);
        mapping.setValue(value);
    }

    public double getErrVar(final Node x) {
        final Parameter param = this.semPm.getVarianceParameter(x);
        return getParamValue(param);
    }

    public double getErrCovar(final Node x, final Node y) {
        final Parameter param = this.semPm.getCovarianceParameter(x, y);
        return getParamValue(param);
    }

    public double getEdgeCoef(final Node x, final Node y) {
        final Parameter param = this.semPm.getCoefficientParameter(x, y);
        return getParamValue(param);
    }

    public double getEdgeCoef(final Edge edge) {
        if (!Edges.isDirectedEdge(edge)) {
            throw new IllegalArgumentException("Only directed edges have 'edge coefficients'");
        }
        return getEdgeCoef(edge.getNode1(), edge.getNode2());
    }

    public void setErrVar(final Node x, final double value) {
        final Parameter param = this.semPm.getVarianceParameter(x);
        setParamValue(param, value);
    }

    public void setEdgeCoef(final Node x, final Node y, final double value) {
        final Parameter param = this.semPm.getCoefficientParameter(x, y);
        setParamValue(param, value);
    }

    public boolean existsEdgeCoef(final Node x, final Node y) {
        return x != y && this.semPm.getCoefficientParameter(x, y) != null;
    }

    public void setErrCovar(final Node x, final double value) {
        final SemGraph graph = getSemPm().getGraph();
        final Node exogenousX = graph.getExogenous(x);
        setParamValue(exogenousX, exogenousX, value);
    }

    public void setErrCovar(final Node x, final Node y, final double value) {
        final Parameter param = this.semPm.getCovarianceParameter(x, y);
        setParamValue(param, value);
    }

    /**
     * Sets the mean associated with the given node.
     */
    public void setMean(final Node node, final double mean) {
        final int index = this.variableNodes.indexOf(node);
        this.variableMeans[index] = mean;
    }

    /**
     * Sets the mean associated with the given node.
     */
    public void setMeanStandardDeviation(final Node node, final double mean) {
        final int index = this.variableNodes.indexOf(node);
        this.variableMeansStdDev[index] = mean;
    }

    /**
     * Sets the intercept. For acyclic SEMs only.
     *
     * @throws UnsupportedOperationException if called on a cyclic SEM.
     */
    public void setIntercept(final Node node, final double intercept) {
        if (isCyclic()) {
            throw new UnsupportedOperationException("Setting and getting of "
                    + "intercepts is supported for acyclic SEMs only. The internal "
                    + "parameterizations uses variable means; the relationship "
                    + "between variable means and intercepts has not been fully "
                    + "worked out for the cyclic case.");
        }

        final SemGraph semGraph = getSemPm().getGraph();
        final List<Node> tierOrdering = semGraph.getCausalOrdering();

        final double[] intercepts = new double[tierOrdering.size()];

        for (int i = 0; i < tierOrdering.size(); i++) {
            final Node _node = tierOrdering.get(i);
            intercepts[i] = getIntercept(_node);
        }

        intercepts[tierOrdering.indexOf(node)] = intercept;

        for (int i = 0; i < tierOrdering.size(); i++) {
            final Node _node = tierOrdering.get(i);

            final List<Node> parents = semGraph.getParents(_node);

            double weightedSumOfParentMeans = 0.0;

            for (final Node parent : parents) {
                if (parent.getNodeType() == NodeType.ERROR) {
                    continue;
                }

                final double coef = getEdgeCoef(parent, _node);
                final double mean = getMean(parent);
                weightedSumOfParentMeans += coef * mean;
            }

            final double mean = weightedSumOfParentMeans + intercepts[i];
            setMean(_node, mean);
        }
    }

    /**
     * @return the intercept, for acyclic models, or Double.NaN otherwise.
     * @throws UnsupportedOperationException if called on a cyclic SEM.
     */
    public double getIntercept(Node node) {
        node = this.semPm.getGraph().getNode(node.getName());

        if (isCyclic()) {
            return Double.NaN;
//            throw new UnsupportedOperationException("Setting and getting of " +
//                    "intercepts is supported for acyclic SEMs only. The internal " +
//                    "parameterizations uses variable means; the relationship " +
//                    "between variable means and intercepts has not been fully " +
//                    "worked out for the cyclic case.");
        }

        final SemGraph semGraph = getSemPm().getGraph();
        final List<Node> parents = semGraph.getParents(node);

        double weightedSumOfParentMeans = 0.0;

        for (final Node parent : parents) {
            if (parent.getNodeType() == NodeType.ERROR) {
                continue;
            }

            final double coef = getEdgeCoef(parent, node);
            final double mean = getMean(parent);
            weightedSumOfParentMeans += coef * mean;
        }

        final double mean = getMean(node);
        final double intercept = mean - weightedSumOfParentMeans;
        return intercept;
    }

    /**
     * @return the value of the mean assoc iated with the given node.
     */
    public double getMean(final Node node) {
        final int index = this.variableNodes.indexOf(node);

        if (index == -1) {
            System.out.println("Expecting this node: " + node);
            System.out.println("Node list = " + this.variableNodes);
        }

        return this.variableMeans[index];
    }

    /**
     * @return the means for variables in order.
     */
    public double[] getMeans() {
        final double[] means = new double[this.variableMeans.length];
        System.arraycopy(this.variableMeans, 0, means, 0, this.variableMeans.length);
        return means;
    }

    /**
     * @return the value of the mean associated with the given node.
     */
    public double getMeanStdDev(final Node node) {
        final int index = this.variableNodes.indexOf(node);
        return this.variableMeansStdDev[index];
    }

    /**
     * @return the value of the variance associated with the given node.
     */
    public double getVariance(final Node node, final Matrix implCovar) {
        if (getSemPm().getGraph().isExogenous(node)) {
//            if (node.getNodeType() == NodeType.ERROR) {
            final Parameter parameter = getSemPm().getVarianceParameter(node);

            // This seems to be required to get the show/hide error terms
            // feature to work in the SemImEditor.
            if (parameter == null) {
                return Double.NaN;
            }

            return getParamValue(parameter);
        } else {
            final int index = this.variableNodes.indexOf(node);
//            TetradMatrix implCovar = getImplCovar();
            return implCovar.get(index, index);
        }
    }

    /**
     * @return the value of the standard deviation associated with the given
     * node.
     */
    public double getStdDev(final Node node, final Matrix implCovar) {
        return sqrt(getVariance(node, implCovar));
    }

    /**
     * Gets the value of a single free parameter to the given value, where the
     * free parameter is specified by the endpoint nodes of its edge in the w
     * graph. Note that coefficient freeParameters connect elements of
     * getVariableNodes(), whereas variance and covariance freeParameters
     * connect elements of getExogenousNodes(). (For variance freeParameters,
     * nodeA and nodeB are the same.)
     *
     * @throws IllegalArgumentException if the given parameter is not a free
     *                                  parameter in this model or if there is no parameter connecting nodeA with
     *                                  nodeB in this model.
     */
    public double getParamValue(final Node nodeA, final Node nodeB) {
        Parameter parameter = null;

        if (nodeA == nodeB) {
            parameter = getSemPm().getVarianceParameter(nodeA);
        }

        if (parameter == null) {
            parameter = getSemPm().getCovarianceParameter(nodeA, nodeB);
        }

        if (parameter == null) {
            parameter = getSemPm().getCoefficientParameter(nodeA, nodeB);
        }

        if (parameter == null) {
            return Double.NaN;
        }

        if (!getFreeParameters().contains(parameter)) {
            return Double.NaN;
        }

        return getParamValue(parameter);
    }

    /**
     * Sets the value of a single free parameter to the given value, where the
     * free parameter is specified by the endpoint nodes of its edge in the
     * graph. Note that coefficient freeParameters connect elements of
     * getVariableNodes(), whereas variance and covariance freeParameters
     * connect elements of getExogenousNodes(). (For variance freeParameters,
     * nodeA and nodeB are the same.)
     *
     * @throws IllegalArgumentException if the given parameter is not a free
     *                                  parameter in this model or if there is no parameter connecting nodeA with
     *                                  nodeB in this model, or if value is Double.NaN.
     */
    public void setParamValue(final Node nodeA, final Node nodeB, final double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Please remove or impute missing data.");
        }

        if (nodeA == null || nodeB == null) {
            throw new NullPointerException("Nodes must not be null: nodeA = " + nodeA + ", nodeB = " + nodeB);
        }

        Parameter parameter = null;

        if (nodeA == nodeB) {
            parameter = getSemPm().getVarianceParameter(nodeA);
        }

        if (parameter == null) {
            parameter = getSemPm().getCoefficientParameter(nodeA, nodeB);
        }

        if (parameter == null) {
            parameter = getSemPm().getCovarianceParameter(nodeA, nodeB);
        }

        if (parameter == null) {
            throw new IllegalArgumentException("There is no parameter in "
                    + "model for an edge from " + nodeA + " to " + nodeB + ".");
        }

        if (!this.getFreeParameters().contains(parameter)) {
            throw new IllegalArgumentException(
                    "Not a free parameter in " + "this model: " + parameter);
        }

        setParamValue(parameter, value);
    }

    /**
     * @return the (unmodifiable) list of free freeParameters in the model.
     */
    public List<Parameter> getFreeParameters() {
        return this.freeParameters;
    }

    /**
     * @return the number of free freeParameters.
     */
    public int getNumFreeParams() {
        return getFreeParameters().size();
    }

    /**
     * @return the (unmodifiable) list of fixed freeParameters in the model.
     */
    public List<Parameter> getFixedParameters() {
        return this.fixedParameters;
    }

    private List<Parameter> getMeanParameters() {
        return this.meanParameters;
    }

    /**
     * @return the number of free freeParameters.
     */
    public int getNumFixedParams() {
        return getFixedParameters().size();
    }

    /**
     * The list of measured and latent nodes for the semPm. (Unmodifiable.)
     */
    public List<Node> getVariableNodes() {
        return this.variableNodes;
    }

    /**
     * The list of measured nodes for the semPm. (Unmodifiable.)
     */
    public List<Node> getMeasuredNodes() {
        return this.measuredNodes;
    }

    /**
     * @return the sample size (that is, the sample size of the CovarianceMatrix
     * provided at construction time).
     */
    public int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * @return a copy of the matrix of edge coefficients. Note that
     * edgeCoefC[i][j] is the coefficient of the edge from
     * getVariableNodes().get(i) to getVariableNodes().get(j), or 0.0 if this
     * edge is not in the graph. The values of these may be changed, but the
     * array itself may not.
     */
    public Matrix getEdgeCoef() {
        return this.edgeCoef.copy();
    }

    /**
     * @return a copy of the matrix of error covariances. Note that
     * errCovar[i][j] is the covariance of the error term of
     * getExoNodes().get(i) and getExoNodes().get(j), with the special case
     * (duh!) that errCovar[i][i] is the variance of getExoNodes.get(i). The
     * values of these may be changed, but the array itself may not.
     */
    public Matrix getErrCovar() {
        return errCovar().copy();
    }

    /**
     * @return a copy of the implied covariance matrix over all the variables.
     */
    public Matrix getImplCovar(final boolean recalculate) {
        if (!recalculate && this.implCovar != null) {
            return this.implCovar;
        } else {
            return implCovar();
        }
    }

    /**
     * @return a copy of the implied covariance matrix over the measured
     * variables only.
     */
    public Matrix getImplCovarMeas() {
        return implCovarMeas().copy();
    }

    /**
     * @return a copy of the sample covariance matrix, or null if no sample
     * covar has been set.
     */
    public Matrix getSampleCovar() {
        return this.sampleCovarC;
    }

    /**
     * Sets the distribution for the given node.
     */
    public void setDistribution(final Node node, final Distribution distribution) {
        if (node == null) {
            throw new NullPointerException();
        }

        if (!getVariableNodes().contains(node)) {
            throw new IllegalArgumentException("Not a node in this SEM.");
        }

        if (distribution == null) {
            throw new NullPointerException("Distribution must be specified.");
        }

        this.distributions.put(node, distribution);
    }

    /**
     * The value of the maximum likelihood function for the getModel the model
     * (Bollen 107). To optimize, this should be minimized.
     */
    public double getScore() {
        if (this.scoreType == ScoreType.Fml) {
            return getFml2();
        } else if (this.scoreType == ScoreType.Fgls) {
            return getFgls();
        } else {
            throw new IllegalStateException("Unrecognized score type; " + this.scoreType);
        }
    }

    //    private double getFml1() {
//        TetradMatrix sigma; // Do this once.
//
//        try {
//            sigma = implCovarMeas();
//        } catch (Exception e) {
//            return Double.NaN;
//        }
//
//        TetradMatrix s = this.sampleCovarC;
//
//        double logDetSigma = logDet(sigma);
//        double traceSSigmaInv = traceABInv(s, sigma);
//        double logDetSample = logDetSample();
//        int pPlusQ = getMeasuredNodes().size();
//
//        double h1 = logDetSigma + traceSSigmaInv;
//        double h0 = logDetSample + pPlusQ;
//
//        return h1 - h0;
//    }
    private double getFml2() {
        final Matrix sigma;

        try {
            sigma = implCovarMeas();
        } catch (final Exception e) {
            return Double.NaN;
        }

        final Matrix s = this.sampleCovarC;

        final double fml;

        try {
            fml = Math.log(sigma.det()) + (s.times(sigma.inverse())).trace() - Math.log(s.det()) - getMeasuredNodes().size();
        } catch (final Exception e) {
            return Double.NaN;
        }

        return fml;
    }

    private double getFgls() {
        final Matrix implCovarMeas;

        try {
            implCovarMeas = implCovarMeas();
        } catch (final Exception e) {
            return Double.NaN;
        }

        if (this.sampleCovInv == null) {
            final Matrix sampleCovar = this.sampleCovarC;
            this.sampleCovInv = sampleCovar.inverse();
        }

        final Matrix I = Matrix.identity(implCovarMeas.rows());
        final Matrix diff = I.minus((implCovarMeas.times(this.sampleCovInv)));

        return 0.5 * (diff.times(diff)).trace();
    }

//    private double getLogLikelihood() {
//        TetradMatrix SigmaTheta; // Do this once.
//
//        try {
//            SigmaTheta = implCovarMeas();
//        } catch (Exception e) {
////            e.printStackTrace();
//            return Double.NaN;
//        }
//
//        TetradMatrix sStar = this.sampleCovarC;
//
//        double logDetSigmaTheta = logDet(SigmaTheta);
//        double traceSStarSigmaInv = traceABInv(sStar, SigmaTheta);
//        int pPlusQ = getMeasuredNodes().size();
//
//        return -(sampleSize / 2.) * pPlusQ * Math.log(2 * Math.PI)
//                - (sampleSize / 2.) * logDetSigmaTheta
//                - (sampleSize / 2.) * traceSStarSigmaInv;
//    }

    /**
     * The negative of the log likelihood function for the getModel model, with
     * the constant chopped off. (Bollen 134). This is an alternative, more
     * efficient, optimization function to Fml which produces the same result
     * when minimized.
     */
    public double getTruncLL() {
        // Formula Bollen p. 263.

        final Matrix Sigma = implCovarMeas();

        // Using (n - 1) / n * s as in Bollen p. 134 causes sinkholes to open
        // up immediately. Not sure why.
        final Matrix S = this.sampleCovarC;
        final int n = getSampleSize();
        return -(n - 1) / 2. * (logDet(Sigma) + traceAInvB(Sigma, S));
//        return -(n / 2.0) * (logDet(Sigma) + traceABInv(S, Sigma));
//        return -(logDet(Sigma) + traceABInv(S, Sigma));
//        return -(n - 1) / 2 * (logDet(Sigma) + traceABInv(S, Sigma));
    }

    /**
     * @return BIC score, calculated as chisq - dof. This is equal to
     * getFullBicScore() up to a constant.
     */
    public double getBicScore() {
        final int dof = getSemPm().getDof();
//        return getChiSquare() - dof * Math.log(sampleSize);

        return getChiSquare() - dof * Math.log(this.sampleSize);

//        CovarianceMatrix covarianceMatrix = new CovarianceMatrix(getVariableNodes(), getImplCovar(), getSampleSize());
//        Ges ges = new Ges(covarianceMatrix);
//        return -ges.getScore(getEstIm().getGraph());
    }

    @Override
    public double getRmsea() {
//        double e = 1; // The sum of squares of the model? What's that?
//        return e / (getSemPm().getDof() * getSampleSize());

        final double v = getChiSquare() - this.semPm.getDof();
        final double v1 = this.semPm.getDof() * (getSampleSize() - 1);
        return sqrt(v) / sqrt(v1);
    }

    @Override
    public double getCfi() {
        if (getSampleCovar() == null) {
            return Double.NaN;
        }

        final SemIm nullIm = independenceModel();
        final double nullChiSq = nullIm.getChiSquare();
        final double dNull = nullChiSq - nullIm.getSemPm().getDof();
        final double dProposed = getChiSquare() - getSemPm().getDof();
        return (dNull - dProposed) / dNull;
    }

    private SemIm independenceModel() {
        final Graph nullModel = new SemGraph(getSemPm().getGraph());
        nullModel.removeEdges(nullModel.getEdges());
        final SemPm nullPm = new SemPm(nullModel);
        final CovarianceMatrix sampleCovar = new CovarianceMatrix(getMeasuredNodes(), getSampleCovar(), getSampleSize());

        return new SemEstimator(sampleCovar, nullPm).estimate();
    }

//    public double getAicScore() {
//        int dof = getSemPm().getDof();
//        return getChiSquare() - 2 * dof;
////
////        return getChiSquare() + dof * Math.log(sampleSize);
//
////        CovarianceMatrix covarianceMatrix = new CovarianceMatrix(getVariableNodes(), getImplCovar(), getSampleSize());
////        Ges ges = new Ges(covarianceMatrix);
////        return -ges.getScore(getEstIm().getGraph());
//    }
//    /**
//     * @return the BIC score, without subtracting constant terms.
//     */
//    public double getFullBicScore() {
////        int dof = getEstIm().getDof();
//        int sampleSize = getSampleSize();
//        double penalty = getNumFreeParams() * Math.log(sampleSize);
////        double penalty = getEstIm().getDof() * Math.log(sampleSize);           i
//        double L = getLogLikelihood();
//        return -2 * L + penalty;
//    }
//    public double getKicScore() {
//        double fml = getScore();
//        int edgeCount = getSemPm().getGraph().getNumEdges();
//        int sampleSize = getSampleSize();
//
//        return -fml - (edgeCount * Math.log(sampleSize));
//    }

    /**
     * @return the chi square value for the model.
     */
    public double getChiSquare() {
        return (getSampleSize() - 1) * getScore();
    }

    /**
     * @return the p-value for the model.
     */
    public double getPValue() {
        final double chiSquare = getChiSquare();
        final int dof = this.semPm.getDof();
        if (dof <= 0) {
            return Double.NaN;
        } else if (chiSquare < 0) {
            return Double.NaN;
        } else {
            return 1.0 - new ChiSquaredDistribution(dof).cumulativeProbability(chiSquare);
        }
    }

    /**
     * This simulate method uses the implied covariance metrix directly to
     * simulate data, instead of going tier by tier. It should work for cyclic
     * graphs as well as acyclic graphs.
     */
    @Override
    public DataSet simulateData(final int sampleSize, final boolean latentDataSaved) {
        return simulateData(sampleSize, null, latentDataSaved);
    }

    private DataSet simulateData(final int sampleSize, final DataSet initialValues, final boolean latentDataSaved) {
        if (this.semPm.getGraph().isTimeLagModel()) {
            return simulateTimeSeries(sampleSize, latentDataSaved);
        }

        if (initialValues != null) {
            return simulateDataRecursive(sampleSize, latentDataSaved);
        } else {
            //        return simulateDataCholesky(sampleSize, latentDataSaved);
            return simulateDataReducedForm(sampleSize, latentDataSaved);
//            return simulateDataRecursive2(sampleSize, latentDataSaved);
        }
    }

    public void setScoreType(ScoreType scoreType) {
        if (scoreType == null) {
            scoreType = ScoreType.Fgls;
        }
        this.scoreType = scoreType;
    }

    private DataSet simulateTimeSeries(final int sampleSize, final boolean latentDataSaved) {
        final SemGraph semGraph = new SemGraph(this.semPm.getGraph());
        semGraph.setShowErrorTerms(true);
        final TimeLagGraph timeSeriesGraph = this.semPm.getGraph().getTimeLagGraph();

        final List<Node> variables = new ArrayList<>();

        final List<Node> lag0Nodes = timeSeriesGraph.getLag0Nodes();

        for (final Node node : lag0Nodes) {
            final ContinuousVariable _node = new ContinuousVariable(timeSeriesGraph.getNodeId(node).getName());
            _node.setNodeType(node.getNodeType());
            variables.add(_node);
        }

        final DataSet fullData = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);

        final Map<Node, Integer> nodeIndices = new HashMap<>();

        for (int i = 0; i < lag0Nodes.size(); i++) {
            nodeIndices.put(lag0Nodes.get(i), i);
        }

        final Graph contemporaneousDag = timeSeriesGraph.subgraph(timeSeriesGraph.getLag0Nodes());

        final List<Node> tierOrdering = contemporaneousDag.getCausalOrdering();

        for (int currentStep = 0; currentStep < sampleSize; currentStep++) {
            for (final Node to : tierOrdering) {
                final List<Node> parents = semGraph.getNodesInTo(to, Endpoint.ARROW);

                double sum = 0.0;

                for (final Node parent : parents) {
                    if (parent.getNodeType() == NodeType.ERROR) {
                        final Node child = semGraph.getChildren(parent).get(0);
                        final double paramValue = getParamValue(child, child);
                        sum += RandomUtil.getInstance().nextNormal(0.0, paramValue);
                    } else {
                        final TimeLagGraph.NodeId id = timeSeriesGraph.getNodeId(parent);
                        final int fromIndex = nodeIndices.get(timeSeriesGraph.getNode(id.getName(), 0));
                        final int lag = id.getLag();
                        if (currentStep > lag) {
                            final double coef = getParamValue(parent, to);
                            final double fromValue = fullData.getDouble(currentStep - lag, fromIndex);
                            sum += coef * fromValue;
                        } else {
                            sum += RandomUtil.getInstance().nextNormal(0.0, 0.5);
                        }
                    }
                }

                if (to.getNodeType() != NodeType.ERROR) {
                    final int toIndex = nodeIndices.get(to);
                    fullData.setDouble(currentStep, toIndex, sum);
                }
            }
        }

        return latentDataSaved ? fullData : DataUtils.restrictToMeasured(fullData);
    }

    /**
     * This simulate method uses the implied covariance metrix directly to
     * simulate data, instead of going tier by tier. It should work for cyclic
     * graphs as well as acyclic graphs.
     *
     * @param sampleSize how many data points in sample
     * @param seed       a seed for random number generation
     */
    @Override
    public DataSet simulateData(final int sampleSize, final long seed, final boolean latentDataSaved) {
        final RandomUtil random = RandomUtil.getInstance();
        final long _seed = random.getSeed();
        random.setSeed(seed);
        final DataSet dataSet = simulateData(sampleSize, latentDataSaved);
        random.revertSeed(_seed);
        return dataSet;
    }

    /**
     * Simulates data from this Sem using a Cholesky decomposition of the
     * implied covariance matrix. This method works even when the underlying
     * graph is cyclic.
     *
     * @param sampleSize      the number of rows of data to simulate.
     * @param latentDataSaved True iff data for latents should be saved.
     */
    public DataSet simulateDataCholesky(final int sampleSize, final boolean latentDataSaved) {
        final List<Node> variables = new LinkedList<>();

        if (latentDataSaved) {
            for (final Node node : getVariableNodes()) {
                variables.add(node);
            }
        } else {
            for (final Node node : getMeasuredNodes()) {
                variables.add(node);
            }
        }

        final List<Node> newVariables = new ArrayList<>();

        for (final Node node : variables) {
            final ContinuousVariable continuousVariable = new ContinuousVariable(node.getName());
            continuousVariable.setNodeType(node.getNodeType());
            newVariables.add(continuousVariable);
        }

        final Matrix impliedCovar = implCovar();

        final DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, newVariables.size()), newVariables);
        final Matrix cholesky = MatrixUtils.cholesky(impliedCovar);

        // Simulate the data by repeatedly calling the Cholesky.exogenousData
        // method. Store only the data for the measured variables.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            final double[] exoData = new double[cholesky.rows()];

            for (int i = 0; i < exoData.length; i++) {
                exoData[i] = RandomUtil.getInstance().nextNormal(0, 1);
                //            exoData[i] = randomUtil.nextUniform(-1, 1);
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            final double[] point = new double[exoData.length];

            for (int i = 0; i < exoData.length; i++) {
                double sum = 0.0;

                for (int j = 0; j <= i; j++) {
                    sum += cholesky.get(i, j) * exoData[j];
                }

                point[i] = sum;
            }

            for (int col = 0; col < variables.size(); col++) {
                final int index = getVariableNodes().indexOf(variables.get(col));
                final double value = point[index] + this.variableMeans[col];

                if (Double.isNaN(value) || Double.isInfinite(value)) {
                    throw new IllegalArgumentException("Value out of range: " + value);
                }

                if (isSimulatedPositiveDataOnly() && value < 0) {
                    row--;
                    continue ROW;
                }

                fullDataSet.setDouble(row, col, value);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }

    public DataSet simulateDataRecursive(final int sampleSize,
                                         final boolean latentDataSaved) {
        return simulateDataRecursive(sampleSize, null, latentDataSaved);
    }

    public DataSet simulateDataRecursive(final DataSet initialValues,
                                         final boolean latentDataSaved) {
        return simulateDataRecursive(initialValues.getNumRows(), initialValues, latentDataSaved);
    }

    /**
     * This simulates data by picking random values for the exogenous terms and
     * percolating this information down through the SEM, assuming it is
     * acyclic. Fast for large simulations but hangs for cyclic models.
     *
     * @param sampleSize > 0.
     * @return the simulated data set.
     */
    private DataSet simulateDataRecursive(final int sampleSize, final DataSet initialValues,
                                          final boolean latentDataSaved) {
        final int errorType = this.params.getInt(Params.SIMULATION_ERROR_TYPE);

        final List<Node> variables = new LinkedList<>();
        final List<Node> variableNodes = getVariableNodes();

        for (final Node node : variableNodes) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            variables.add(var);
        }

        final DataSet fullDataSet = new BoxDataSet(new VerticalDoubleDataBox(sampleSize, variables.size()), variables);

        // Create some index arrays to hopefully speed up the simulation.
        final Graph graph = new EdgeListGraph(getSemPm().getGraph());
        final List<Node> tierOrdering = graph.getCausalOrdering();

        final int[] tierIndices = new int[variableNodes.size()];

        for (int i = 0; i < tierIndices.length; i++) {
            tierIndices[i] = variableNodes.indexOf(tierOrdering.get(i));
        }

        final int[][] _parents = new int[variableNodes.size()][];

        for (int i = 0; i < variableNodes.size(); i++) {
            final Node node = variableNodes.get(i);
            final List<Node> parents = graph.getParents(node);

            parents.removeIf(_node -> _node.getNodeType() == NodeType.ERROR);

            _parents[i] = new int[parents.size()];

            for (int j = 0; j < parents.size(); j++) {
                final Node _parent = parents.get(j);
                _parents[i][j] = variableNodes.indexOf(_parent);
            }
        }

        final Matrix cholesky = MatrixUtils.cholesky(errCovar());

        // Do the simulation.
        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            final double[] exoData = new double[cholesky.rows()];

            for (int i = 0; i < exoData.length; i++) {
                if (errorType == 1) {
                    exoData[i] = RandomUtil.getInstance().nextNormal(0,
                            sqrt(this.errCovar.get(i, i)));
                } else if (errorType == 2) {
                    exoData[i] = RandomUtil.getInstance().nextUniform(this.errorParam1, this.errorParam2);
                } else if (errorType == 3) {
                    exoData[i] = RandomUtil.getInstance().nextExponential(this.errorParam1);
                } else if (errorType == 4) {
                    exoData[i] = RandomUtil.getInstance().nextGumbel(this.errorParam1,
                            this.errorParam2);
                }
            }

            // Step 2. Multiply by cholesky to get correct covariance.
            final double[] point = new double[exoData.length];

            for (int i = 0; i < exoData.length; i++) {
                double sum = 0.0;

                for (int j1 = 0; j1 < exoData.length; j1++) {
                    sum += cholesky.get(i, j1) * exoData[j1];
                }

                point[i] = sum;
            }

            final Vector e = new Vector(point);

            for (int tier = 0; tier < tierOrdering.size(); tier++) {
                final Node node = tierOrdering.get(tier);
                final ConnectionFunction function = this.functions.get(node);
                final int col = tierIndices[tier];

                final Distribution distribution = this.distributions.get(node);
                double value;

                // If it's an exogenous node and initial data has been specified, use that data instead.
                final Node node1 = tierOrdering.get(tier);

                Node initNode = null;
                int initCol = -1;

                if (initialValues != null) {
                    initNode = initialValues.getVariable(node1.getName());
                    initCol = initialValues.getColumn(initNode);
                }

                if (_parents[col].length == 0 && initialValues != null
                        && initCol != -1) {
                    final int column = initialValues.getColumn(initNode);
                    value = initialValues.getDouble(row, column);
//                    System.out.println(value);

                } else {
                    if (distribution == null) {
//                    value = RandomUtil.getInstance().nextNormal(0,
//                            Math.sqrt(errCovar().get(col, col)));
                        value = e.get(col);
                    } else {
                        value = distribution.nextRandom();
                    }
                }

                if (function != null) {
                    final Node[] parents = function.getInputNodes();
                    final double[] parentValues = new double[parents.length];

                    for (int j = 0; j < parents.length; j++) {
                        final Node parent = parents[j];
                        final int index = variableNodes.indexOf(parent);
                        parentValues[j] = fullDataSet.getDouble(row, index);
                    }

                    value += function.valueAt(parentValues);

                    if (initialValues == null && isSimulatedPositiveDataOnly() && value < 0) {
                        row--;
                        continue ROW;
                    }

                    fullDataSet.setDouble(row, col, value);
                } else {
                    for (int j = 0; j < _parents[col].length; j++) {
                        final int parent = _parents[col][j];
                        final double parentValue = fullDataSet.getDouble(row, parent);
                        final double parentCoef = this.edgeCoef.get(parent, col);
                        value += parentValue * parentCoef;
                    }

                    if (isSimulatedPositiveDataOnly() && value < 0) {
                        row--;
                        continue ROW;
                    }

                    fullDataSet.setDouble(row, col, value);
                }
            }
        }

        for (int i = 0; i < fullDataSet.getNumRows(); i++) {
            for (int j = 0; j < fullDataSet.getNumColumns(); j++) {
                fullDataSet.setDouble(i, j, fullDataSet.getDouble(i, j) + this.variableMeans[j]);
            }
        }

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }

    public DataSet simulateDataReducedForm(final int sampleSize, final boolean latentDataSaved) {
        final int errorType = this.params.getInt(Params.SIMULATION_ERROR_TYPE);

        final int numVars = getVariableNodes().size();

        // Calculate inv(I - edgeCoefC)
        final Matrix B = edgeCoef().transpose();
        final Matrix iMinusBInv = TetradAlgebra.identity(B.rows()).minus(B).inverse();

        // Pick error values e, for each calculate inv * e.
        final Matrix sim = new Matrix(sampleSize, numVars);

        ROW:
        for (int row = 0; row < sampleSize; row++) {

            // Step 1. Generate normal samples.
            final Vector e = new Vector(this.edgeCoef.columns());

            for (int i = 0; i < e.size(); i++) {
//                e.set(i, RandomUtil.getInstance().nextNormal(0, sqrt(errCovar.get(i, i))));

                if (errorType == 1) {
                    e.set(i, RandomUtil.getInstance().nextNormal(0, sqrt(this.errCovar.get(i, i))));
//                    e.set(i, RandomUtil.getInstance().nextNormal(0, sqrt(errorParam2)));
                } else if (errorType == 2) {
                    e.set(i, RandomUtil.getInstance().nextUniform(this.errorParam1, this.errorParam2));
                } else if (errorType == 3) {
                    e.set(i, RandomUtil.getInstance().nextExponential(this.errorParam1));
                } else if (errorType == 4) {
                    e.set(i, RandomUtil.getInstance().nextGumbel(this.errorParam1, this.errorParam2));
                }
            }

            // Step 3. Calculate the new rows in the data.
            final Vector sample = iMinusBInv.times(e);
            sim.assignRow(row, sample);

            for (int col = 0; col < sample.size(); col++) {
                final double value = sim.get(row, col) + this.variableMeans[col];

                if (isSimulatedPositiveDataOnly() && value < 0) {
                    row--;
                    continue ROW;
                }

                sim.set(row, col, value);
            }
        }

        final List<Node> continuousVars = new ArrayList<>();

        for (final Node node : getVariableNodes()) {
            final ContinuousVariable var = new ContinuousVariable(node.getName());
            var.setNodeType(node.getNodeType());
            continuousVars.add(var);
        }

        final DataSet fullDataSet = new BoxDataSet(new DoubleDataBox(sim.toArray()), continuousVars);

        if (latentDataSaved) {
            return fullDataSet;
        } else {
            return DataUtils.restrictToMeasured(fullDataSet);
        }
    }

    // For testing.
    public Vector simulateOneRecord(final Vector e) {
        // Calculate inv(I - edgeCoefC)
        final Matrix edgeCoef = edgeCoef().copy().transpose();

//        TetradMatrix iMinusB = TetradAlgebra.identity(edgeCoefC.rows()); //TetradMatrix.identity(edgeCoefC.rows());
//        iMinusB.assign(edgeCoefC, Functions.minus);
        final Matrix iMinusB = TetradAlgebra.identity(edgeCoef.rows()).minus(edgeCoef);

        final Matrix inv = iMinusB.inverse();

        return inv.times(e);
    }

    /**
     * Iterates through all freeParameters, picking values for them from the
     * distributions that have been set for them.
     */
    public void initializeValues() {
        for (final Mapping fixedMapping : this.fixedMappings) {
            final Parameter parameter = fixedMapping.getParameter();
            fixedMapping.setValue(initialValue(parameter));
        }

        for (final Mapping freeMapping : this.freeMappings) {
            final Parameter parameter = freeMapping.getParameter();
            freeMapping.setValue(initialValue(parameter));
        }
    }

    public double getStandardError(final Parameter parameter, final int maxFreeParams) {
        final Matrix sampleCovar = getSampleCovar();

        if (sampleCovar == null) {
            return Double.NaN;
        }

        if (getFreeParameters().contains(parameter)) {
            if (getNumFreeParams() <= maxFreeParams) {
                if (parameter.getNodeA() != parameter.getNodeB()) {
                    final Node nodeA = parameter.getNodeA();
                    final Node nodeB = parameter.getNodeB();
                    final Node parent;
                    final Node child;

                    final Graph graph = getSemPm().getGraph();

                    if (graph.isParentOf(nodeA, nodeB)) {
                        parent = nodeA;
                        child = nodeB;
                    } else {
                        parent = nodeB;
                        child = nodeA;
                    }

                    if (child.getName().startsWith("E_")) {
                        return Double.NaN;
                    }

                    final CovarianceMatrix cov = new CovarianceMatrix(this.measuredNodes, sampleCovar, this.sampleSize);
                    final Regression regression = new RegressionCovariance(cov);
                    final List<Node> parents = graph.getParents(child);

                    for (final Node node : new ArrayList<>(parents)) {
                        if (node.getName().startsWith("E_")) {
                            parents.remove(node);
                        }
                    }

                    if (!(child.getNodeType() == NodeType.LATENT) && !containsLatent(parents)) {
                        final RegressionResult result = regression.regress(child, parents);
                        final double[] se = result.getSe();
                        return se[parents.indexOf(parent) + 1];
                    }
                }

                if (this.sampleCovarC == null) {
                    this.standardErrors = null;
                    return Double.NaN;
                }

                final int index = getFreeParameters().indexOf(parameter);
                final double[] doubles = standardErrors();

                if (doubles == null) {
                    return Double.NaN;
                }

                return doubles[index];
            } else {
                return Double.NaN;
            }
        } else if (getFixedParameters().contains(parameter)) {
            return 0.0;
        }

        throw new IllegalArgumentException(
                "That is not a parameter of this model: " + parameter);
    }

    private boolean containsLatent(final List<Node> parents) {
        for (final Node node : parents) {
            if (node.getNodeType() == NodeType.LATENT) {
                return true;
            }
        }

        return false;
    }

    public List<Node> listUnmeasuredLatents() {
        return unmeasuredLatents(getSemPm());
    }

    public double getTValue(final Parameter parameter, final int maxFreeParams) {
        return getParamValue(parameter)
                / getStandardError(parameter, maxFreeParams);
    }

    public double getPValue(final Parameter parameter, final int maxFreeParams) {
        final double tValue = getTValue(parameter, maxFreeParams);
        final int df = getSampleSize() - 1;
        return 2.0 * (1.0 - ProbUtils.tCdf(Math.abs(tValue), df));
    }

    public boolean isParameterBoundsEnforced() {
        return this.parameterBoundsEnforced;
    }

    public void setParameterBoundsEnforced(
            final boolean parameterBoundsEnforced) {
        this.parameterBoundsEnforced = parameterBoundsEnforced;
    }

    public boolean isEstimated() {
        return this.estimated;
    }

    public void setEstimated(final boolean estimated) {
        this.estimated = estimated;
    }

    public boolean isCyclic() {
        if (!this.cyclicChecked) {
            this.cyclic = this.semPm.getGraph().existsDirectedCycle();
            this.cyclicChecked = true;
        }

        return this.cyclic;
    }

//    public boolean isCheckPositiveDefinite() {
//        return checkPositiveDefinite;
//    }
//
//    public void setCheckPositiveDefinite(boolean checkPositiveDefinite) {
//        this.checkPositiveDefinite = checkPositiveDefinite;
//    }

    /**
     * @return the variable by the given name, or null if none exists.
     * @throws NullPointerException if name is null.
     */
    public Node getVariableNode(final String name) {
        if (name == null) {
            throw new NullPointerException();
        }

        final List<Node> variables = getVariableNodes();

        for (final Node variable : variables) {
            if (name.equals(variable.getName())) {
                return variable;
            }
        }

        return null;
    }

    /**
     * @return a string representation of the Sem (pretty detailed).
     */
    public String toString() {
        final List<String> varNames = new ArrayList<>();

        for (final Node node : this.variableNodes) {
            varNames.add(node.getName());
        }

        final StringBuilder buf = new StringBuilder();

        buf.append("\nVariable nodes:\n\n");
        buf.append(getVariableNodes());

        buf.append("\n\nMeasured nodes:\n\n");
        buf.append(getMeasuredNodes());

        buf.append("\n\nEdge coefficient matrix:\n");
        buf.append(MatrixUtils.toStringSquare(edgeCoef().toArray(), varNames));

        buf.append("\n\nError covariance matrix:\n");
        buf.append(MatrixUtils.toStringSquare(getErrCovar().toArray(), varNames));

        buf.append("\n\nVariable means:\n");

        for (int i = 0; i < getVariableNodes().size(); i++) {
            buf.append("\nMean(");
            buf.append(getVariableNodes().get(i));
            buf.append(") = ");
            buf.append(this.variableMeans[i]);
        }

        buf.append("\n\nSample size = ");
        buf.append(this.sampleSize);

        if (this.sampleCovarC == null) {
            buf.append("\n\nSample covaraince matrix not specified**");
        } else {
            buf.append("\n\nsample cov:\n");
            buf.append(MatrixUtils.toString(this.sampleCovarC.toArray()));
        }

        buf.append("\n\nimplCovar:\n");

        try {
            buf.append(MatrixUtils.toString(implCovar().toArray()));
        } catch (final IllegalFormatException e) {
            e.printStackTrace();
        }

        buf.append("\n\nimplCovarMeas:\n");
        buf.append(MatrixUtils.toString(implCovarMeas().toArray()));

        if (this.sampleCovarC != null) {
            buf.append("\n\nmodel chi square = ");
            buf.append(getChiSquare());

            buf.append("\nmodel dof = ");
            buf.append(this.semPm.getDof());

            buf.append("\nmodel p-value = ");
            buf.append(getPValue());
        }

        buf.append("\n\nfree mappings:\n");
        for (int i = 0; i < this.freeMappings.size(); i++) {
            final Mapping iMapping = this.freeMappings.get(i);
            buf.append("\n");
            buf.append(i);
            buf.append(". ");
            buf.append(iMapping);
        }

        buf.append("\n\nfixed mappings:\n");
        for (int i = 0; i < this.fixedMappings.size(); i++) {
            final Mapping iMapping = this.fixedMappings.get(i);
            buf.append("\n");
            buf.append(i);
            buf.append(". ");
            buf.append(iMapping);
        }

        return buf.toString();
    }

    //==============================PRIVATE METHODS====================//
    private void retainPreviousValues(final SemIm oldSemIm) {
        if (oldSemIm == null) {
            System.out.println("old sem im null");
            return;
        }

        final List<Node> nodes = this.semPm.getGraph().getNodes();
        final Graph oldGraph = oldSemIm.getSemPm().getGraph();

        for (final Node nodeA : nodes) {
            for (final Node nodeB : nodes) {
                final Node _nodeA = oldGraph.getNode(nodeA.getName());
                final Node _nodeB = oldGraph.getNode(nodeB.getName());

                if (_nodeA == null || _nodeB == null) {
                    continue;
                }

                final double _value = oldSemIm.getParamValue(_nodeA, _nodeB);

                if (!Double.isNaN(_value)) {
                    try {
                        final Parameter _parameter = oldSemIm.getSemPm().getParameter(_nodeA, _nodeB);
                        final Parameter parameter = getSemPm().getParameter(nodeA, nodeB);

                        if (parameter == null || _parameter == null) {
                            continue;
                        }

                        if (parameter.getType() != _parameter.getType()) {
                            continue;
                        }

                        if (parameter.isFixed()) {
                            continue;
                        }

                        setParamValue(nodeA, nodeB, _value);
                    } catch (final IllegalArgumentException e) {
                        System.out.println("Couldn't set " + nodeA + ", " + nodeB);
                    }
                }
            }
        }
    }

    //    private double logDetSample() {
//        if (logDetSample == 0.0 && getSampleCovar() != null) {
//            double det = getSampleCovar().det();
//            logDetSample = Math.log(det);
//        }
//
//        return logDetSample;
//    }
    private List<Node> unmeasuredLatents(final SemPm semPm) {
        final SemGraph graph = semPm.getGraph();

        final List<Node> unmeasuredLatents = new LinkedList<>();

        NODES:
        for (final Node node : graph.getNodes()) {
            if (node.getNodeType() == NodeType.LATENT) {
                for (final Node child : graph.getChildren(node)) {
                    if (child.getNodeType() == NodeType.MEASURED) {
                        continue NODES;
                    }
                }

                unmeasuredLatents.add(node);
            }
        }

        return unmeasuredLatents;
    }

    private Matrix errCovar() {
        return this.errCovar;
    }

    private Matrix implCovar() {
        computeImpliedCovar();
        return this.implCovar;
    }

    private Matrix implCovarMeas() {
        computeImpliedCovar();
        // Submatrix of implied covar for measured vars only.
        final int size = getMeasuredNodes().size();
        /*
      The covariance matrix of the measured variables only. May be null if
      implCovar has not been calculated yet. This is the submatrix of
      implCovar, restricted to just the measured variables. It is recalculated
      each time the F_ML function is recalculated.

         */
        final Matrix implCovarMeas = new Matrix(size, size);

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                final Node iNode = getMeasuredNodes().get(i);
                final Node jNode = getMeasuredNodes().get(j);

//                int _i = getVariableNodes().indexOf(iNode);
//                int _j = getVariableNodes().indexOf(jNode);
                if (this.variablesHash == null) {
                    this.variablesHash = new HashMap<>();

                    for (int k = 0; k < this.variableNodes.size(); k++) {
                        this.variablesHash.put(this.variableNodes.get(k), k);
                    }
                }

                final int _i = this.variablesHash.get(iNode);
                final int _j = this.variablesHash.get(jNode);

                implCovarMeas.set(i, j, this.implCovar.get(_i, _j));
            }
        }
        return implCovarMeas;
    }

    private List<Parameter> initFreeParameters() {
        return Collections.unmodifiableList(this.semPm.getFreeParameters());
    }

    /**
     * @return a random value from the appropriate distribution for the given
     * parameter.
     */
    private double initialValue(final Parameter parameter) {
        if (!getSemPm().getParameters().contains(parameter)) {
            throw new IllegalArgumentException("Not a parameter for this SEM: " + parameter);
        }

        if (parameter.isInitializedRandomly()) {
            if (parameter.getType() == ParamType.COEF) {
                final double coefLow = getParams().getDouble("coefLow", .5);
                final double coefHigh = getParams().getDouble("coefHigh", 1.5);
                final double value = new Split(coefLow, coefHigh).nextRandom();
                if (getParams().getBoolean("coefSymmetric", true)) {
                    return value;
                } else {
                    return Math.abs(value);
                }
            } else if (parameter.getType() == ParamType.COVAR) {
                final double covLow = getParams().getDouble("covLow", 0.1);
                final double covHigh = getParams().getDouble("covHigh", 0.2);
                final double value = new Split(covLow, covHigh).nextRandom();
                if (getParams().getBoolean("covSymmetric", true)) {
                    return value;
                } else {
                    return Math.abs(value);
                }
            } else { //if (parameter.getType() == ParamType.VAR) {
                return RandomUtil.getInstance().nextUniform(getParams().getDouble("varLow", 1), getParams().getDouble("varHigh", 3));
            }
        } else {
            return parameter.getStartingValue();
        }
    }

    /**
     * @return the (unmodifiable) list of freeParameters (type Param).
     */
    private List<Mapping> freeMappings() {
        return this.freeMappings;
    }

    /**
     * @return A submatrix of <code>covMatrix</code> with the order of its
     * variables the same as in <code>semPm</code>.
     * @throws IllegalArgumentException if not all of the variables of
     *                                  <code>semPm</code> are in <code>covMatrix</code>.
     */
    private ICovarianceMatrix fixVarOrder(final ICovarianceMatrix covMatrix) {
        final List<String> varNamesList = new ArrayList<>();

        for (int i = 0; i < getMeasuredNodes().size(); i++) {
            final Node node = getMeasuredNodes().get(i);
            varNamesList.add(node.getName());
        }

        //System.out.println("CovarianceMatrix ar order: " + varNamesList);
        final String[] measuredVarNames = varNamesList.toArray(new String[varNamesList.size()]);
        return covMatrix.getSubmatrix(measuredVarNames);
    }

    /**
     * Creates an unmodifiable list of freeMappings in the same order as the
     * given list of freeParameters.
     */
    private List<Mapping> createMappings(final List<Parameter> parameters) {
        final List<Mapping> mappings = new ArrayList<>();
        final SemGraph graph = getSemPm().getGraph();

        for (final Parameter parameter : parameters) {
            final Node nodeA = graph.getVarNode(parameter.getNodeA());
            final Node nodeB = graph.getVarNode(parameter.getNodeB());

            if (nodeA == null || nodeB == null) {
                throw new IllegalArgumentException("Missing variable--either " + nodeA + " or " + nodeB + " parameter = " + parameter + ".");
            }

            final int i = getVariableNodes().indexOf(nodeA);
            final int j = getVariableNodes().indexOf(nodeB);

            if (parameter.getType() == ParamType.COEF) {
                final Mapping mapping = new Mapping(this, parameter, edgeCoef(), i, j);
                mappings.add(mapping);
            } else if (parameter.getType() == ParamType.VAR) {
                final Mapping mapping = new Mapping(this, parameter, errCovar(), i, i);
                mappings.add(mapping);
            } else if (parameter.getType() == ParamType.COVAR) {
                final Mapping mapping = new Mapping(this, parameter, errCovar(), i, j);
                mappings.add(mapping);
            }
        }

        return Collections.unmodifiableList(mappings);
    }

    private List<Parameter> initFixedParameters() {
        final List<Parameter> fixedParameters = new ArrayList<>();

        for (final Parameter _parameter : getSemPm().getParameters()) {
            final ParamType type = _parameter.getType();

            if (type == ParamType.VAR || type == ParamType.COVAR || type == ParamType.COEF) {
                if (_parameter.isFixed()) {
                    fixedParameters.add(_parameter);
                }
            }
        }

        return Collections.unmodifiableList(fixedParameters);
    }

    private List<Parameter> initMeanParameters() {
        final List<Parameter> meanParameters = new ArrayList<>();

        for (final Parameter param : getSemPm().getParameters()) {
            if (param.getType() == ParamType.MEAN) {
                meanParameters.add(param);
            }
        }

        return Collections.unmodifiableList(meanParameters);
    }

    /**
     * Computes the implied covariance matrices of the Sem. There are two:
     * <code>implCovar </code> contains the covariances of all the variables and
     * <code>implCovarMeas</code> contains covariance for the measured variables
     * only.
     */
    private void computeImpliedCovar() {
        final Matrix edgeCoefT = edgeCoef().transpose();// getAlgebra().transpose(edgeCoefC());

        // Note. Since the sizes of the temp matrices in this calculation
        // never change, we ought to be able to reuse them.
        this.implCovar = MatrixUtils.impliedCovar(edgeCoefT, errCovar());

    }

    private double logDet(final Matrix matrix2D) {
        final double det = matrix2D.det();
        return Math.log(Math.abs(det));
//        return det > 0 ? Math.log(det) : 0;
    }

    private double traceAInvB(final Matrix A, final Matrix B) {

        // Note that at this point the sem and the sample covar MUST have the
        // same variables in the same order.
        final Matrix inverse = A.inverse();
        final Matrix product = inverse.times(B);

        final double trace = product.trace();

//        double trace = MatrixUtils.trace(product);
        if (trace < -1e-8) {
            return 0;
//            throw new IllegalArgumentException("Trace was negative: " + trace);
        }

        return trace;
    }

    //    private double traceABInv(TetradMatrix A, TetradMatrix B) {
//
//        // Note that at this point the sem and the sample covar MUST have the
//        // same variables in the same order.
//        TetradMatrix inverse;
//        try {
//            inverse = B.inverse();
//        } catch (Exception e) {
//            return Double.NaN;
//        }
//
//        TetradMatrix product = A.times(inverse);
//
//        double trace = product.trace();
//
//        if (trace < -1e-8) {
//            return 0;
//        }
//
//        return trace;
//    }
    private Matrix edgeCoef() {
        return this.edgeCoef;
    }

    private double[] standardErrors() {
        if (this.standardErrors == null) {
            final SemStdErrorEstimator estimator = new SemStdErrorEstimator();
            try {
                estimator.computeStdErrors(this);
            } catch (final Exception e) {
                return null;
            }
            this.standardErrors = estimator.getStdErrors();
        }
        return this.standardErrors;
    }

    /**
     * Adds semantic checks to the default deserialization method. This method
     * must have the standard signature for a readObject method, and the body of
     * the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from
     * version to version. A readObject method of this form may be added to any
     * class, even if Tetrad sessions were previously saved out using a version
     * of the class that didn't include it. (That's what the
     * "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for help.
     *
     * @throws java.io.IOException
     * @throws ClassNotFoundException
     */
    private void readObject(final ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.semPm == null) {
            throw new NullPointerException();
        }

        if (this.variableNodes == null) {
            throw new NullPointerException();
        }

        if (this.measuredNodes == null) {
            throw new NullPointerException();
        }

        if (this.variableMeans == null) {
            throw new NullPointerException();
        }

        if (this.freeParameters == null) {
            throw new NullPointerException();
        }

        if (this.freeMappings == null) {
            throw new NullPointerException();
        }

        if (this.fixedParameters == null) {
            throw new NullPointerException();
        }

        if (this.fixedMappings == null) {
            throw new NullPointerException();
        }

        if (this.meanParameters == null) {
            this.meanParameters = initMeanParameters();
        }

        if (this.sampleSize < 0) {
            throw new IllegalArgumentException(
                    "Sample size out of range: " + this.sampleSize);
        }

        if (getParams() == null) {
            setParams(new Parameters());
        }

        if (this.distributions == null) {
            this.distributions = new HashMap<>();
        }
    }

    //    private TetradAlgebra getAlgebra() {
//        if (algebra == null) {
//            algebra = new TetradAlgebra();
//        }
//
//        return algebra;
//    }
    private double round(final double value, int decimalPlace) {
        double power_of_ten = 1;
        while (decimalPlace-- > 0) {
            power_of_ten *= 10.0;
        }
        return Math.round(value * power_of_ten)
                / power_of_ten;
    }

    public Parameters getParams() {
        return this.params;
    }

    public void setParams(final Parameters params) {
        this.params = params;
    }

    public void setFunction(final Node node, final ConnectionFunction function) {
        final List<Node> parents = this.semPm.getGraph().getParents(node);

        for (final Iterator<Node> j = parents.iterator(); j.hasNext(); ) {
            final Node _node = j.next();

            if (_node.getNodeType() == NodeType.ERROR) {
                j.remove();
            }
        }

        final HashSet<Node> parentSet = new HashSet<>(parents);
        final List<Node> inputList = Arrays.asList(function.getInputNodes());
        final HashSet<Node> inputSet = new HashSet<>(inputList);

        if (!parentSet.equals(inputSet)) {
            throw new IllegalArgumentException("The given function for " + node
                    + " may only use the parents of " + node + ": " + parents);
        }

        this.functions.put(node, function);
    }

    public ConnectionFunction getConnectionFunction(final Node node) {
        return this.functions.get(node);
    }

    public double[] getVariableMeans() {
        return this.variableMeans;
    }

    public boolean isSimulatedPositiveDataOnly() {
        return this.simulatedPositiveDataOnly;
    }

    public void setSimulatedPositiveDataOnly(final boolean simulatedPositiveDataOnly) {
        this.simulatedPositiveDataOnly = simulatedPositiveDataOnly;
    }

    public Matrix getImplCovar(final List<Node> nodes) {
        computeImpliedCovar();
        // Submatrix of implied covar for listed nodes only
        final int size = nodes.size();
        final Matrix implCovarMeas = new Matrix(size, size);

        final Map<Node, Integer> variablesHash = new HashMap<>();

        for (int k = 0; k < this.variableNodes.size(); k++) {
            variablesHash.put(this.variableNodes.get(k), k);
        }

        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                final Node iNode = nodes.get(i);
                final Node jNode = nodes.get(j);

                final int _i = variablesHash.get(iNode);
                final int _j = variablesHash.get(jNode);

                implCovarMeas.set(i, j, this.implCovar.get(_i, _j));
            }
        }

        return implCovarMeas;
    }
}
