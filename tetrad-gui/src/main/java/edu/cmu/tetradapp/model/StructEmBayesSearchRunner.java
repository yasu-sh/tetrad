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

package edu.cmu.tetradapp.model;

import edu.cmu.tetrad.bayes.BayesIm;
import edu.cmu.tetrad.bayes.BayesPm;
import edu.cmu.tetrad.bayes.FactoredBayesStructuralEM;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.graph.Graph;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradLogger;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.io.IOException;
import java.io.ObjectInputStream;

/**
 * Wraps a Bayes Pm for use in the Tetrad application.
 *
 * @author josephramsey
 * @author Frank Wimberly adapted for EM Bayes estimator and structural EM Bayes
 * search
 */
public class StructEmBayesSearchRunner implements SessionModel, GraphSource {
    static final long serialVersionUID = 23L;

    /**
     * @serial Can be null.
     */
    private String name;

    /**
     * @serial Cannot be null.
     */
    private BayesPm bayesPm;

    /**
     * @serial Cannot be null.
     */
    private DataSet dataSet;

    /**
     * @serial Cannot be null.
     */
    private BayesIm estimatedBayesIm;

    //===============================CONSTRUCTORS============================//

    private StructEmBayesSearchRunner(DataWrapper dataWrapper,
                                      BayesPmWrapper bayesPmWrapper) {
        if (dataWrapper == null) {
            throw new NullPointerException(
                    "BayesDataWrapper must not be null.");
        }

        if (bayesPmWrapper == null) {
            throw new NullPointerException("BayesPmWrapper must not be null");
        }

        this.dataSet = (DataSet) dataWrapper.getSelectedDataModel();
        this.bayesPm = bayesPmWrapper.getBayesPm();

        estimate(this.dataSet, this.bayesPm);
        log();
    }

    public StructEmBayesSearchRunner(Simulation simulation,
                                     BayesPmWrapper bayesPmWrapper) {
        this((DataWrapper) simulation, bayesPmWrapper);
    }

    public StructEmBayesSearchRunner(DataWrapper dataWrapper,
                                     BayesPmWrapper bayesPmWrapper, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (bayesPmWrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        DataSet dataSet =
                (DataSet) dataWrapper.getSelectedDataModel();

        FactoredBayesStructuralEM estimator = new FactoredBayesStructuralEM(
                dataSet, bayesPmWrapper.getBayesPm());
        this.dataSet = estimator.getDataSet();

        try {
            this.estimatedBayesIm =
                    estimator.maximization(params.getDouble("tolerance", 0.0001));

        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        }

        log();
    }

    public StructEmBayesSearchRunner(DataWrapper dataWrapper,
                                     BayesImWrapper bayesImWrapper, Parameters params) {
        if (dataWrapper == null) {
            throw new NullPointerException();
        }

        if (bayesImWrapper == null) {
            throw new NullPointerException();
        }

        if (params == null) {
            throw new NullPointerException();
        }

        DataSet dataSet =
                (DataSet) dataWrapper.getSelectedDataModel();

        this.bayesPm = bayesImWrapper.getBayesIm().getBayesPm();

        FactoredBayesStructuralEM estimator =
                new FactoredBayesStructuralEM(dataSet, this.bayesPm);
        this.dataSet = estimator.getDataSet();

        try {
            this.estimatedBayesIm =
                    estimator.maximization(params.getDouble("tolerance", 0.0001));
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(
                    "Please specify the search tolerance first.");
        }

        log();
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

    //================================PUBLIC METHODS========================//

    public BayesIm getEstimatedBayesIm() {
        return this.estimatedBayesIm;
    }

    private void estimate(DataSet DataSet, BayesPm bayesPm) {
        final double thresh = 0.0001;

        try {
            FactoredBayesStructuralEM estimator =
                    new FactoredBayesStructuralEM(DataSet, bayesPm);
            this.dataSet = estimator.getDataSet();
            this.estimatedBayesIm = estimator.maximization(thresh);
        } catch (ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();
            throw new RuntimeException("Value assignments between Bayes PM " +
                    "and discrete data set do not match.");
        }
    }

    public DataSet getDataSet() {
        return this.dataSet;
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
     */
    private void readObject(ObjectInputStream s)
            throws IOException, ClassNotFoundException {
        s.defaultReadObject();

        if (this.estimatedBayesIm == null) {
            throw new NullPointerException();
        }

        if (this.dataSet == null) {
            throw new NullPointerException();
        }
    }

    public Graph getGraph() {
        return this.estimatedBayesIm.getBayesPm().getDag();
    }

    public String getName() {
        return this.name;
    }

    public void setName(String name) {
        this.name = name;
    }

    private void log() {
        TetradLogger.getInstance().log("info", "EM-Estimated Bayes IM");
        TetradLogger.getInstance().log("im", "" + this.estimatedBayesIm);
    }
}





