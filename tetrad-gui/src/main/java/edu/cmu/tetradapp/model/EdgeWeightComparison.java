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

import edu.cmu.tetrad.sem.SemIm;
import edu.cmu.tetrad.session.SessionModel;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.NumberFormatUtil;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.text.NumberFormat;

/**
 * @author Michael Freenor
 */
public class EdgeWeightComparison implements SessionModel {
    static final long serialVersionUID = 23L;

    private String name;
    private final SemIm reference;
    private final SemIm target;

    public EdgeWeightComparison(SemEstimatorWrapper reference, SemEstimatorWrapper target, Parameters parameters) {
        this.reference = reference.getEstimatedSemIm();
        this.target = target.getEstimatedSemIm();
    }

    public EdgeWeightComparison(SemImWrapper reference, SemEstimatorWrapper target, Parameters parameters) {
        this.reference = reference.getSemIm();
        this.target = target.getEstimatedSemIm();
    }

    public EdgeWeightComparison(SemImWrapper reference, SemImWrapper target, Parameters parameters) {
        this.reference = reference.getSemIm();
        this.target = target.getSemIm();
    }

    public String getDisplayString() {
        String displayString = "";

        SemIm ref = this.reference;
        Matrix referenceMatrix = ref.getEdgeCoef();
        Matrix targetMatrix = this.target.getEdgeCoef();

        if (targetMatrix.getNumColumns() != referenceMatrix.getNumColumns() || targetMatrix.getNumRows() != referenceMatrix.getNumRows())
            return "The SEM IM's you selected don't have the same number of variables!  No comparison is possible here.";

        double score = 0;
        for (int i = 0; i < ref.getEdgeCoef().getNumRows(); i++) {
            for (int j = 0; j < ref.getEdgeCoef().getNumColumns(); j++) {
                score += (targetMatrix.get(i, j) - referenceMatrix.get(i, j))
                        * (targetMatrix.get(i, j) - referenceMatrix.get(i, j));
            }
        }

        NumberFormat nf = NumberFormatUtil.getInstance().getNumberFormat();

        displayString += "Sum of squared differences of corresponding\nedge weights:\n\n" + nf.format(score);
        return displayString;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }


    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static DataWrapper serializableInstance() {
        return new DataWrapper(new Parameters());
    }
}



