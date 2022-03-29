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

import edu.cmu.tetrad.data.CorrelationMatrix;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.ICovarianceMatrix;
import edu.cmu.tetrad.graph.Node;

import java.util.List;

/**
 * Implements a test of tetrad constraints in a known correlation matrix. It might be useful for debugging
 * BuildPureClusters/Purify-like algorithm.
 *
 * @author Ricardo Silva
 */

public class PopulationTetradTest implements TetradTest {
    private final CorrelationMatrix CorrelationMatrix;
    private final boolean[] bvalues;
    private final double epsilon = 0.001;

    public PopulationTetradTest(final CorrelationMatrix CorrelationMatrix) {
        this.CorrelationMatrix = CorrelationMatrix;
        this.bvalues = new boolean[3];
    }

    public String[] getVarNames() {
        return this.CorrelationMatrix.getVariableNames().toArray(new String[0]);
    }

    public List<Node> getVariables() {
        return this.CorrelationMatrix.getVariables();
    }

    public DataSet getDataSet() {
        return null;
    }

    /**
     * Population scores: assumes CorrelationMatrix is the population covariance CorrelationMatrix. Due to numerical
     * rounding problems, we need a parameter epsilon to control it. Nothing here is implemented for discrete data
     * (yet).
     */

    public int tetradScore(final int v1, final int v2, final int v3, final int v4) {
        int count = 0;

        final double p_12 = this.CorrelationMatrix.getValue(v1, v2);
        final double p_13 = this.CorrelationMatrix.getValue(v1, v3);
        final double p_14 = this.CorrelationMatrix.getValue(v1, v4);
        final double p_23 = this.CorrelationMatrix.getValue(v2, v3);
        final double p_24 = this.CorrelationMatrix.getValue(v2, v4);
        final double p_34 = this.CorrelationMatrix.getValue(v3, v4);

        for (int i = 0; i < 3; i++) {
            this.bvalues[i] = false;
        }

        if (Math.abs(p_12 * p_34 - p_13 * p_24) < this.epsilon) {
            count++;
            this.bvalues[0] = true;
        }
        if (Math.abs(p_12 * p_34 - p_14 * p_23) < this.epsilon) {
            count++;
            this.bvalues[1] = true;
        }
        if (Math.abs(p_13 * p_24 - p_14 * p_23) < this.epsilon) {
            count++;
            this.bvalues[2] = true;
        }
        return count;
    }

    public boolean tetradScore3(final int v1, final int v2, final int v3, final int v4) {
        return tetradScore(v1, v2, v3, v4) == 3;
    }

    public boolean tetradScore1(final int v1, final int v2, final int v3, final int v4) {
        if (tetradScore(v1, v2, v3, v4) != 1) {
            return false;
        }
        return this.bvalues[2];
    }

    public boolean tetradHolds(final int v1, final int v2, final int v3, final int v4) {
        final double p_12 = this.CorrelationMatrix.getValue(v1, v2);
        final double p_13 = this.CorrelationMatrix.getValue(v1, v3);
        final double p_24 = this.CorrelationMatrix.getValue(v2, v4);
        final double p_34 = this.CorrelationMatrix.getValue(v3, v4);
        this.bvalues[0] = Math.abs(p_12 * p_34 - p_13 * p_24) < this.epsilon;
        return this.bvalues[0];
    }

    public boolean oneFactorTest(final int a, final int b, final int c, final int d) {
        return tetradScore3(a, b, c, d);
    }

    public boolean oneFactorTest(final int a, final int b, final int c, final int d, final int e) {
        return tetradScore3(a, b, c, d) && tetradScore3(a, b, c, e) &&
                tetradScore3(b, c, d, e);
    }

    public boolean oneFactorTest(final int a, final int b, final int c, final int d, final int e, final int f) {
        return tetradScore3(a, b, c, d) && tetradScore3(b, c, d, e) &&
                tetradScore3(c, d, e, f);
    }

    public boolean twoFactorTest(final int a, final int b, final int c, final int d) {
        tetradScore(a, b, c, d);
        return this.bvalues[2];
    }

    public boolean twoFactorTest(final int a, final int b, final int c, final int d, final int e) {
        tetradScore(a, b, d, e);

        if (!this.bvalues[2]) {
            return false;
        }

        tetradScore(a, c, d, e);

        if (!this.bvalues[2]) {
            return false;
        }

        tetradScore(b, c, d, e);
        return this.bvalues[2];
    }

    public boolean twoFactorTest(final int a, final int b, final int c, final int d, final int e, final int f) {
        if (!twoFactorTest(a, b, c, d, e)) {
            return false;
        }

        if (!twoFactorTest(a, b, c, d, f)) {
            return false;
        }

        return twoFactorTest(a, b, c, e, f);
    }

    public double tetradPValue(final int v1, final int v2, final int v3, final int v4) {
        return -1;
    }

    public double tetradPValue(final int i1, final int j1, final int k1, final int l1, final int i2, final int j2, final int k2, final int l2) {
        return 0;  //To change body of implemented methods use File | Settings | File Templates.
    }

    public double getSignificance() {
        return 0;
    }

    public void setSignificance(final double sig) {
        throw new UnsupportedOperationException();
    }

    public ICovarianceMatrix getCovMatrix() {
        return null;
    }
}





