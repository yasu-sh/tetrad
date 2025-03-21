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

package edu.cmu.tetrad.data;

import cern.colt.matrix.DoubleMatrix2D;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.ForkJoinPoolInstance;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Vector;
import org.apache.commons.math3.util.FastMath;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.text.NumberFormat;
import java.util.*;
import java.util.concurrent.RecursiveTask;

/**
 * Stores a covariance matrix together with variable names and sample size, intended as a representation of a data set.
 * When constructed from a continuous data set, the matrix is not checked for positive definiteness; however, when a
 * covariance matrix is supplied, its positive definiteness is always checked. If the sample size is less than the
 * number of variables, the positive definiteness is "spot-checked"--that is, checked for various submatrices.
 *
 * @author josephramsey
 * @see CorrelationMatrix
 */
public class CovarianceMatrixOnTheFly implements ICovarianceMatrix {
    static final long serialVersionUID = 23L;
    private boolean verbose = false;

    /**
     * The name of the covariance matrix.
     *
     * @serial May be null.
     */
    private String name;

    /**
     * The variables (in order) for this covariance matrix.
     *
     * @serial Cannot be null.
     */
    private List<Node> variables;

    /**
     * The size of the sample from which this covariance matrix was calculated.
     *
     * @serial Range &gt; 0.
     */
    private int sampleSize;

    /**
     * Stored matrix data. Should be square. This may be set by derived classes, but it must always be set to a
     * legitimate covariance matrix.
     *
     * @serial Cannot be null. Must be symmetric and positive definite.
     */
    private Matrix matrix;

    /**
     * @serial Do not remove this field; it is needed for serialization.
     */
    private DoubleMatrix2D matrixC;

    /**
     * The list of selected variables.
     *
     * @serial Cannot be null.
     */
    private Set<Node> selectedVariables = new HashSet<>();

    /**
     * The knowledge for this data.
     *
     * @serial Cannot be null.
     */
    private Knowledge knowledge = new Knowledge();

    private double[][] vectors = null;

    private final double[] variances;


    //=============================CONSTRUCTORS=========================//

    /**
     * Constructs a new covariance matrix from the given data set. If dataSet is a BoxDataSet with a
     * VerticalDoubleDataBox, the data will be mean-centered by the constructor; is non-mean-centered version of the
     * data is needed, the data should be copied before being send into the constructor.
     *
     * @throws IllegalArgumentException if this is not a continuous data set.
     */
    public CovarianceMatrixOnTheFly(DataSet dataSet) {
        this(dataSet, false);
    }

    public CovarianceMatrixOnTheFly(DataSet dataSet, boolean verbose) {
        if (!dataSet.isContinuous()) {
            throw new IllegalArgumentException("Not a continuous data set.");
        }

        this.variables = Collections.unmodifiableList(dataSet.getVariables());
        this.sampleSize = dataSet.getNumRows();

        if (verbose) {
            System.out.println("Calculating variable vectors");
        }

        if (dataSet instanceof BoxDataSet) {

            DataBox box = ((BoxDataSet) dataSet).getDataBox();

            if (box instanceof VerticalDoubleDataBox) {
                if (verbose) {
                    System.out.println("Getting vectors from VerticalDoubleDataBox");
                }
//                box = box.copy();

                if (!dataSet.getVariables().equals(variables)) throw new IllegalArgumentException();

                vectors = ((VerticalDoubleDataBox) box).getVariableVectors();

                if (verbose) {
                    System.out.println("Calculating means");
                }

                Vector means = DataUtils.means(vectors);
                demean(vectors, means);
            } else if (box instanceof DoubleDataBox) {
                if (verbose) {
                    System.out.println("Getting vectors from DoubleDataBox");
                }
                if (!dataSet.getVariables().equals(variables)) throw new IllegalArgumentException();
                double[][] horizData = ((DoubleDataBox) box).getData();

                if (verbose) {
                    System.out.println("Transposing data");
                }

                vectors = new double[horizData[0].length][horizData.length];

                for (int i = 0; i < horizData.length; i++) {
                    for (int j = 0; j < horizData[0].length; j++) {
                        vectors[j][i] = horizData[i][j];
                    }
                }

                if (verbose) {
                    System.out.println("Calculating means");
                }

                Vector means = DataUtils.means(vectors);
                demean(vectors, means);
            }


        }

        if (vectors == null) {
            if (verbose) {
                System.out.println("Copying data");
            }

            Matrix doubleData = dataSet.getDoubleData().copy();

            if (verbose) {
                System.out.println("Calculating means");
            }

            Vector means = DataUtils.means(doubleData);

            if (verbose) {
                System.out.println("Demeaning");
            }

            demean(vectors, means);

            if (verbose) {
                System.out.println("Getting vectors from data");
            }

            vectors = new double[variables.size()][];

            for (int i = 0; i < variables.size(); i++) {
                vectors[i] = matrix.getColumn(i).toArray();
            }
        }

        if (verbose) {
            System.out.println("Calculating variances");
        }

        this.variances = new double[variables.size()];

        class VarianceTask extends RecursiveTask<Boolean> {
            private final int chunk;
            private final int from;
            private final int to;

            public VarianceTask(int chunk, int from, int to) {
                this.chunk = chunk;
                this.from = from;
                this.to = to;
            }

            @Override
            protected Boolean compute() {
                if (to - from <= chunk) {
                    for (int i = from; i < to; i++) {
                        double d = 0.0D;

                        int count = 0;

                        double[] v1 = vectors[i];

                        for (int k = 0; k < sampleSize; ++k) {
                            if (Double.isNaN(v1[k])) {
                                continue;
                            }

                            d += v1[k] * v1[k];
                            count++;
                        }

                        double v = d;
//                        v /= (sampleSize - 1);
                        v /= (count - 1);

                        variances[i] = v;

                        if (v == 0) {
                            System.out.println("Zero variance! " + variables.get(i));
                        }
                    }

                    return true;
                } else {
                    final int numIntervals = 4;

                    int step = (to - from) / numIntervals + 1;

                    List<VarianceTask> tasks = new ArrayList<>();

                    for (int i = 0; i < numIntervals; i++) {
                        VarianceTask task = new VarianceTask(chunk, from + i * step, FastMath.min(from + (i + 1) * step, to));
                        tasks.add(task);
                    }

                    invokeAll(tasks);

                    return true;
                }
            }
        }

        int NTHREADS = Runtime.getRuntime().availableProcessors() * 10;
        int _chunk = variables.size() / NTHREADS + 1;
        int minChunk = 100;
        int chunk = FastMath.max(_chunk, minChunk);

        VarianceTask task = new VarianceTask(chunk, 0, variables.size());
        ForkJoinPoolInstance.getInstance().getPool().invoke(task);

        if (verbose) {
            System.out.println("Done with variances.");
        }


    }

    public static void demean(double[][] data, Vector means) {
        for (int j = 0; j < data.length; j++) {
            for (int i = 0; i < data[j].length; i++) {
                data[j][i] -= means.get(j);
            }
        }
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ICovarianceMatrix serializableInstance() {
        List<Node> variables = new ArrayList<>();
        Node x = new ContinuousVariable("X");
        variables.add(x);
        Matrix matrix = Matrix.identity(1);
        return new CovarianceMatrix(variables, matrix, 100); //
    }

    //============================PUBLIC METHODS=========================//

    /**
     * @return the list of variables (unmodifiable).
     */
    public final List<Node> getVariables() {
        return this.variables;
    }

    /**
     * @return the variable names, in order.
     */
    public final List<String> getVariableNames() {
        List<String> names = new ArrayList<>();

        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            names.add(variable.getName());
        }

        return names;
    }

    /**
     * @return the variable name at the given index.
     */
    public final String getVariableName(int index) {
        if (index >= getVariables().size()) {
            throw new IllegalArgumentException("Index out of range: " + index);
        }

        Node variable = getVariables().get(index);
        return variable.getName();
    }

    /**
     * @return the dimension of the covariance matrix.
     */
    public final int getDimension() {
        return variables.size();
    }

    /**
     * The size of the sample used to calculated this covariance matrix.
     *
     * @return The sample size (&gt; 0).
     */
    public final int getSampleSize() {
        return this.sampleSize;
    }

    /**
     * Gets the name of the covariance matrix.
     */
    public final String getName() {
        return this.name;
    }

    /**
     * Sets the name of the covariance matrix.
     */
    public final void setName(String name) {
        this.name = name;
    }

    /**
     * @return the knowledge associated with this data.
     */
    public final Knowledge getKnowledge() {
        return this.knowledge.copy();
    }

    /**
     * Associates knowledge with this data.
     */
    public final void setKnowledge(Knowledge knowledge) {
        if (knowledge == null) {
            throw new NullPointerException();
        }

        this.knowledge = knowledge.copy();
    }

    /**
     * @return a submatrix of the covariance matrix with variables in the given order.
     */
    public final ICovarianceMatrix getSubmatrix(int[] indices) {
        List<Node> submatrixVars = new LinkedList<>();

        for (int indice : indices) {
            submatrixVars.add(variables.get(indice));
        }

        Matrix cov = new Matrix(indices.length, indices.length);

        for (int i = 0; i < indices.length; i++) {
            for (int j = i; j < indices.length; j++) {
                double d = getValue(indices[i], indices[j]);
                cov.set(i, j, d);
                cov.set(j, i, d);
            }
        }

        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    public final ICovarianceMatrix getSubmatrix(int[] indices, int[] dataRows) {
        List<Node> submatrixVars = new LinkedList<>();

        for (int indice : indices) {
            submatrixVars.add(variables.get(indice));
        }

        Matrix cov = new Matrix(indices.length, indices.length);

        for (int i = 0; i < indices.length; i++) {
            for (int j = i; j < indices.length; j++) {
                double d = getValue(indices[i], indices[j], dataRows);
                cov.set(i, j, d);
                cov.set(j, i, d);
            }
        }

        return new CovarianceMatrix(submatrixVars, cov, getSampleSize());
    }

    public final ICovarianceMatrix getSubmatrix(List<String> submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return a submatrix of this matrix, with variables in the given order.
     */
    public final CovarianceMatrixOnTheFly getSubmatrix(String[] submatrixVarNames) {
        throw new UnsupportedOperationException();
    }

    /**
     * @return the value of element (i,j) in the matrix
     */
    public final double getValue(int i, int j) {
        if (i == j) {
            return variances[i];
        }

        double d = 0.0D;

        double[] v1 = vectors[i];
        double[] v2 = vectors[j];
        int count = 0;

        for (int k = 0; k < sampleSize; k++) {
            if (Double.isNaN(v1[k])) continue;
            if (Double.isNaN(v2[k])) continue;

            d += v1[k] * v2[k];
            count++;
        }

        double v = d;
//        v /= (sampleSize - 1);
        v /= (count - 1);
        return v;
    }

    public final double getValue(int i, int j, int[] rows) {
        double d = 0.0D;

        double[] v1 = vectors[i];
        double[] v2 = vectors[j];
        int count = 0;

        for (int k : rows) {
            if (Double.isNaN(v1[k])) continue;
            if (Double.isNaN(v2[k])) continue;

            d += v1[k] * v2[k];
            count++;
        }

        double v = d;
//        v /= (sampleSize - 1);
        v /= (count - 1);
//        v /= count;
        return v;
    }

    public void setMatrix(Matrix matrix) {
        this.matrix = matrix;
        checkMatrix();
    }

    public final void setSampleSize(int sampleSize) {
        if (sampleSize <= 0) {
            throw new IllegalArgumentException("Sample size must be > 0.");
        }

        this.sampleSize = sampleSize;
    }

    /**
     * @return the size of the square matrix.
     */
    public final int getSize() {
        return getVariables().size();
    }

    /**
     * @return a copy of the covariance matrix.
     */
    public final Matrix getMatrix() {
        Matrix matrix = new Matrix(getDimension(), getDimension());

        for (int i = 0; i < getDimension(); i++) {
            for (int j = 0; j < getDimension(); j++) {
                matrix.set(i, j, getValue(i, j));
            }
        }

        return matrix;
    }

    public final Matrix getMatrix(int[] rows) {
        Matrix matrix = new Matrix(getDimension(), getDimension());

        for (int i = 0; i < getDimension(); i++) {
            for (int j = 0; j < getDimension(); j++) {
                matrix.set(i, j, getValue(i, j, rows));
            }
        }

        return matrix;
    }

    public final void select(Node variable) {
        if (variables.contains(variable)) {
            getSelectedVariables().add(variable);
        }
    }

    public final void clearSelection() {
        getSelectedVariables().clear();
    }

    public final boolean isSelected(Node variable) {
        if (variable == null) {
            throw new NullPointerException("Null variable. Try again.");
        }

        return getSelectedVariables().contains(variable);
    }

    public final List<String> getSelectedVariableNames() {
        List<String> selectedVariableNames = new LinkedList<>();

        for (Node variable : selectedVariables) {
            selectedVariableNames.add(variable.getName());
        }

        return selectedVariableNames;
    }

    /**
     * Prints out the matrix
     */
    public final String toString() {
        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        StringBuilder buf = new StringBuilder();

        int numVars = getVariableNames().size();
        buf.append(getSampleSize()).append("\n");

        for (int i = 0; i < numVars; i++) {
            String name = getVariableNames().get(i);
            buf.append(name).append("\t");
        }

        buf.append("\n");

        for (int j = 0; j < numVars; j++) {
            for (int i = 0; i <= j; i++) {
                buf.append(nf.format(getValue(i, j))).append("\t");
            }
            buf.append("\n");
        }

        return buf.toString();
    }

    @Override
    public boolean isContinuous() {
        return true;
    }

    @Override
    public boolean isDiscrete() {
        return false;
    }

    @Override
    public boolean isMixed() {
        return false;
    }

    public void setVariables(List<Node> variables) {
        if (variables.size() != this.variables.size()) throw new IllegalArgumentException("Wrong # of variables.");
        this.variables = variables;
    }

    public boolean isVerbose() {
        return verbose;
    }

    public void setVerbose(boolean verbose) {
        this.verbose = verbose;
    }

    @Override
    public Matrix getSelection(int[] rows, int[] cols) {
        Matrix m = new Matrix(rows.length, cols.length);

        if (Arrays.equals(rows, cols)) {
            for (int i = 0; i < rows.length; i++) {
                for (int j = i; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                    m.set(j, i, value);
                }
            }
        } else {
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j]);
                    m.set(i, j, value);
                }
            }
        }

        return m;
    }

    public Matrix getSelection(int[] rows, int[] cols, int[] dataRows) {
        Matrix m = new Matrix(rows.length, cols.length);

        if (Arrays.equals(rows, cols)) {
            for (int i = 0; i < rows.length; i++) {
                for (int j = i; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j], dataRows);
                    m.set(i, j, value);
                    m.set(j, i, value);
                }
            }
        } else {
            for (int i = 0; i < rows.length; i++) {
                for (int j = 0; j < cols.length; j++) {
                    double value = getValue(rows[i], cols[j], dataRows);
                    m.set(i, j, value);
                }
            }
        }

        return m;
    }

    //========================PRIVATE METHODS============================//

    public Node getVariable(String name) {
        for (int i = 0; i < getVariables().size(); i++) {
            Node variable = getVariables().get(i);
            if (name.equals(variable.getName())) {
                return variable;
            }
        }

        return null;
    }

    @Override
    public DataModel copy() {
        return this;
    }

    @Override
    public void setValue(int i, int j, double v) {
        throw new IllegalArgumentException();
    }

    @Override
    public void removeVariables(List<String> remaining) {
        ICovarianceMatrix cov = getSubmatrix(remaining);
        this.matrix = cov.getMatrix();
        this.variables = cov.getVariables();
        clearSelection();
    }

    private Set<Node> getSelectedVariables() {
        return selectedVariables;
    }

    /**
     * Checks the sample size, variable, and matrix information.
     */
    private void checkMatrix() {
        int numVars = variables.size();

        for (Node variable : variables) {
            if (variable == null) {
                throw new NullPointerException();
            }
        }

        if (sampleSize < 1) {
            throw new IllegalArgumentException(
                    "Sample size must be at least 1.");
        }

        if (numVars != matrix.getNumRows() || numVars != matrix.getNumColumns()) {
            throw new IllegalArgumentException("Number of variables does not " +
                    "equal the dimension of the matrix.");
        }
    }

    /**
     * Adds semantic checks to the default deserialization method. This method must have the standard signature for a
     * readObject method, and the body of the method must begin with "s.defaultReadObject();". Other than that, any
     * semantic checks can be specified and do not need to stay the same from version to version. A readObject method of
     * this form may be added to any class, even if Tetrad sessions were previously saved out using a version of the
     * class that didn't include it. (That's what the "s.defaultReadObject();" is for. See J. Bloch, Effective Java, for
     * help.
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (getVariables() == null) {
            throw new NullPointerException();
        }

        if (matrixC != null) {
            matrix = new Matrix(matrixC.toArray());
            matrixC = null;
        }

        if (knowledge == null) {
            throw new NullPointerException();
        }

        if (sampleSize < -1) {
            throw new IllegalStateException();
        }

        if (selectedVariables == null) {
            selectedVariables = new HashSet<>();
        }
    }
}





