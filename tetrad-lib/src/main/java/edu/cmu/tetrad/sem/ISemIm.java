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

package edu.cmu.tetrad.sem;

import edu.cmu.tetrad.data.Simulator;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;

import java.util.List;

/**
 * An interface for SemIM's; see implementations.
 *
 * @author josephramsey
 */
public interface ISemIm extends Simulator {
    long serialVersionUID = 23L;

    SemPm getSemPm();

    double[] getFreeParamValues();

    void setFreeParamValues(double[] params);

    double getParamValue(Parameter parameter);

    void setParamValue(Parameter parameter, double value);

    void setFixedParamValue(Parameter parameter, double value);

    double getParamValue(Node nodeA, Node nodeB);

    void setParamValue(Node nodeA, Node nodeB, double value);

    List<Parameter> getFreeParameters();

    int getNumFreeParams();

    List<Parameter> getFixedParameters();

    int getSampleSize();

    void setParameterBoundsEnforced(boolean b);

    double getScore();

    boolean isParameterBoundsEnforced();

    List<Node> listUnmeasuredLatents();

    boolean isCyclic();

    boolean isEstimated();

    List<Node> getVariableNodes();

    double getMean(Node node);

    double getMeanStdDev(Node node);

    double getIntercept(Node node);

    void setErrVar(Node nodeA, double value);

    void setEdgeCoef(Node x, Node y, double value);

    void setIntercept(Node y, double intercept);

    void setMean(Node node, double value);

    double getStandardError(Parameter parameter, int maxFreeParamsForStatistics);

    double getTValue(Parameter parameter, int maxFreeParamsForStatistics);

    double getPValue(Parameter parameter, int maxFreeParamsForStatistics);

    double getPValue();

    double getVariance(Node nodeA, Matrix implCovar);

    double getStdDev(Node node, Matrix implCovar);

    List<Node> getMeasuredNodes();

    Matrix getImplCovarMeas();

    Matrix getImplCovar(boolean recalculate);

    double getBicScore();

    double getRmsea();

    double getCfi();

    double getChiSquare();

    boolean isSimulatedPositiveDataOnly();

}



