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

import edu.cmu.tetrad.util.TetradSerializable;

/**
 * A class for implementing constraints on the values of the freeParameters of of instances of the SemIm class.  The
 * constraint can either be on the value of a single parameter in relation to a given value (double) or it can constrain
 * the relative values of two freeParameters.  There is a companion class ParamConstraintType that specifies whether the
 * constraint implements an equality, less than or greater than relation.
 *
 * @author Frank Wimberly
 */
public class ParamConstraint implements TetradSerializable {
    static final long serialVersionUID = 23L;


    private final double number;
    private final Parameter param2;
    private ParamConstraintType type;
    private final SemIm semIm;

    /**
     * The first constructor specifies the parameter and a number and the type of relation imposed by the constraint.
     * The SemIm is required because the freeParameters' values are determined by it.
     */
    public ParamConstraint(SemIm semIm, Parameter param1,
                           ParamConstraintType type, double number) {
        this.semIm = semIm;
        this.param2 = null;
        this.type = type;
        this.number = number;
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     */
    public static ParamConstraint serializableInstance() {
        return new ParamConstraint(SemIm.serializableInstance(),
                Parameter.serializableInstance(), ParamConstraintType.EQ, 1.0);
    }

    public ParamConstraintType getType() {
        return this.type;
    }

    public void setType(ParamConstraintType type) {
        this.type = type;
    }

    public double getNumber() {
        return this.number;
    }

    public Parameter getParam2() {
        return this.param2;
    }

    /**
     * This method is for testing whether a value that might be assigned to a parameter would satisfy it.  This is
     * useful during a procedure which searches possible values of freeParameters to find that value which is optimal
     * with respect to some measure of fit of the parameterized SEM to some dataset.
     *
     * @return true if the value would satisfy the constraint.
     */
    public boolean wouldBeSatisfied(double testValue) {
        return this.type == ParamConstraintType.NONE || this.param2 == null && (this.type == ParamConstraintType.EQ && testValue == this.number || this.type == ParamConstraintType.GT && testValue > this.number || this.type == ParamConstraintType.LT && testValue < this.number);

    }

    public SemIm getSemIm() {
        return this.semIm;
    }
}





