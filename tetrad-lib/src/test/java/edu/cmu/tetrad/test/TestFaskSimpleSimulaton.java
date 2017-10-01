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

package edu.cmu.tetrad.test;

import edu.cmu.tetrad.algcomparison.Comparison;
import edu.cmu.tetrad.algcomparison.algorithm.Algorithms;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Fang;
import edu.cmu.tetrad.algcomparison.algorithm.multi.FasLofs;
import edu.cmu.tetrad.algcomparison.algorithm.multi.Fask;
import edu.cmu.tetrad.algcomparison.simulation.LoadContinuousDataAndSingleGraph;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.data.ContinuousVariable;
import edu.cmu.tetrad.data.DataSet;
import edu.cmu.tetrad.data.DataUtils;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.graph.SemGraph;
import edu.cmu.tetrad.regression.RegressionDataset;
import edu.cmu.tetrad.regression.RegressionResult;
import edu.cmu.tetrad.search.Lofs2;
import edu.cmu.tetrad.sem.GeneralizedSemIm;
import edu.cmu.tetrad.sem.GeneralizedSemPm;
import edu.cmu.tetrad.sem.LargeScaleSimulation;
import edu.cmu.tetrad.sem.SemPm;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.RandomUtil;
import org.junit.Test;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;

import static edu.cmu.tetrad.util.StatUtils.*;
import static java.lang.Math.*;
import static java.lang.Math.max;

/**
 * An example script to simulate data and run a comparison analysis on it.
 *
 * @author jdramsey
 */
public class TestFaskSimpleSimulaton {

    public void simulation() {

        int xtoy = 0;
        int ytox = 0;

        int sampleSize = 1000;

        for (int i = 0; i < 1000; i++) {

            // Pick coefficients.
            double a = .2 + RandomUtil.getInstance().nextDouble() * 0.6;
            double b = .2 + RandomUtil.getInstance().nextDouble() * 0.6;
            double c = .2 + RandomUtil.getInstance().nextDouble() * 0.6;

            double[] z = new double[sampleSize];
            double[] y = new double[sampleSize];
            double[] x = new double[sampleSize];

            // Generate data for x, y, z, x -> y, x < -z -> y
            for (int j = 0; j < sampleSize; j++) {
                z[j] = rand();
                x[j] = c * z[j] + rand();
                y[j] = a * x[j] + b * z[j] + rand();
            }

            // Center variables.
            x = DataUtils.center(x);
            y = DataUtils.center(y);
            z = DataUtils.center(z);

            // Swap x and y so y->x instead.
            double[] w = x;
            x = y;
            y = w;

            if (leftright(x, y))
                xtoy = xtoy + 1;

            if (leftright(y, x))
                ytox = ytox + 1;
        }

        System.out.println("xtoy = " + xtoy);
        System.out.println("ytox = " + ytox);

    }

    private double rand() {
//        return RandomUtil.getInstance().nextNormal(0, 1);
        return RandomUtil.getInstance().nextBeta(2, 5);
    }

    private boolean leftright(double[] x, double[] y) {
        return r(x, y) > 0;
    }

    private double r(double[] x, double[] y) {
        return q(x, y) - q(y, x);
    }

    private double q(double[] x, double[] y) {
        return cv(x, y, x) / cv(x, x, x) - cv(x, y, y) / cv(x, x, y);
    }

    private double symmetric(double[] x, double[] y) {
        return cv(x, y, x) / cv(x, x, x) - cv(x, y, y) / cv(y, y, y);
    }

    public static double cv(double[] x, double[] y, double[] condition) {
        double exy = 0.0;
        double ex = 0.0;
        double ey = 0.0;

        int n = 0;

        for (int k = 0; k < x.length; k++) {
            if (condition[k] > 0.0) {
                exy += x[k] * y[k];
                ex += x[k];
                ey += y[k];
                n++;
            }
        }

        return exy / n - (ex / n) * (ey / n);
    }


    public static void main(String... args) {
        new TestFaskSimpleSimulaton().simulation();
    }
}




