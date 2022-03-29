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
import edu.cmu.tetrad.algcomparison.independence.FisherZ;
import edu.cmu.tetrad.algcomparison.simulation.Simulations;
import edu.cmu.tetrad.algcomparison.statistic.*;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.Params;

/**
 * My script.
 *
 * @author jdramsey
 */
public class TestSimulatedFmri2 {

    public void TestCycles_Data_fMRI_FASK() {
        Parameters parameters = new Parameters();
        parameters.set(Params.PENALTY_DISCOUNT, 8);
        parameters.set(Params.DEPTH, -1);
        parameters.set(Params.TWO_CYCLE_ALPHA, 1e-15);

        parameters.set(Params.NUM_RUNS, 60);
//        parameters.set("randomSelectionSize", 1);

        parameters.set("Structure", "Placeholder");

        Statistics statistics = new Statistics();

        statistics.add(new ParameterColumn("Structure"));
        statistics.add(new AdjacencyPrecision());
        statistics.add(new AdjacencyRecall());
//        statistics.add(new MathewsCorrAdj());
        statistics.add(new ArrowheadPrecision());
        statistics.add(new ArrowheadRecall());
        statistics.add(new TwoCyclePrecision());
        statistics.add(new TwoCycleRecall());
        statistics.add(new TwoCycleFalsePositive());
        statistics.add(new TwoCycleFalseNegative());
        statistics.add(new TwoCycleTruePositive());
        statistics.add(new ElapsedTime());
        statistics.setWeight("AHR", 1.0);
        statistics.setWeight("2CP", 1.0);
        statistics.setWeight("2CR", 1.0);
        statistics.setWeight("2CFP", 1.0);

        Simulations simulations = new Simulations();

        final String dir = "/Users/user/Downloads/CyclesTestingData/";
        final String subdir = "data_fslfilter_concat";

        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network1_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network2_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network3_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network4_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network5_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network5_cont", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network5_cont_p3n7", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network5_cont_p7n3", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network6_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network6_cont", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network7_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network7_cont", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network8_amp_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network8_amp_cont", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network8_cont_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network9_amp_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network9_amp_cont", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Network9_cont_amp", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Diamond", subdir));
        simulations.add(new LoadContinuousDataAndSingleGraph(
                dir + "Markov_Complex_1", subdir));

        Algorithms algorithms = new Algorithms();

        algorithms.add(new edu.cmu.tetrad.algcomparison.algorithm.multi.Fask(new FisherZ()));
//
        Comparison comparison = new Comparison();

        comparison.setShowAlgorithmIndices(true);
        comparison.setShowSimulationIndices(true);
        comparison.setSortByUtility(false);
        comparison.setShowUtilities(false);
        comparison.setSaveGraphs(false);
        comparison.setTabDelimitedTables(false);
        comparison.setSaveGraphs(true);

        final String directory = "comparison_testing_nonconcat";

        comparison.compareFromSimulations(directory, simulations, algorithms, statistics, parameters);
    }

    public static void main(String... args) {
        new TestSimulatedFmri2().TestCycles_Data_fMRI_FASK();
    }
}



