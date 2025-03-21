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

package edu.cmu.tetrad.data;

import edu.cmu.tetrad.util.MultiDimIntTable;

import java.util.Arrays;


/**
 * Stores a cell count table of arbitrary dimension. Provides methods for incrementing particular cells and for
 * calculating marginals.
 *
 * @author josephramsey
 * @see edu.cmu.tetrad.util.MultiDimIntTable
 */
public final class CellTable {


//    /**
//     * Stores a copy of coordinates for temporary use. (Reused.)
//     */
//    private int[] coordCopy;

    /**
     * The value used in the data for missing values.
     */
    private int missingValue = -99;

    private final MultiDimIntTable table;

    /**
     * Constructs a new cell table using the given array for dimensions, initializing all cells in the table to zero.
     *
     * @param dims an <code>int[]</code> value
     */
    public CellTable(int[] dims) {
        this.table = new MultiDimIntTable(dims);
    }

    public void addToTable(DataSet dataSet, int[] indices) {
        int[] dims = new int[indices.length];

        for (int i = 0; i < indices.length; i++) {
            DiscreteVariable variable =
                    (DiscreteVariable) dataSet.getVariable(indices[i]);
            dims[i] = variable.getNumCategories();
        }

        this.table.reset(dims);

        int[] coords = new int[indices.length];

        points:
        for (int i = 0; i < dataSet.getNumRows(); i++) {
            for (int j = 0; j < indices.length; j++) {
                try {
                    coords[j] = dataSet.getInt(i, indices[j]);
                } catch (Exception e) {
                    e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
                    coords[j] = dataSet.getInt(i, j);
                }

                if (coords[j] == getMissingValue()) {
                    continue points;
                }
            }

            this.table.increment(coords, 1);
        }
    }

    /**
     * @param varIndex the index of the variable in question.
     * @return the number of dimensions of the variable.
     */
    public int getNumValues(int varIndex) {
        return this.table.getDims(varIndex);
    }

    /**
     * Calculates a marginal sum for the cell table. The variables over which marginal sums should be taken are
     * indicated by placing "-1's" in the appropriate positions in the coordinate argument. For instance, to find the
     * margin for v0 = 1, v1 = 3, and v3 = 2, where the marginal sum ranges over all values of v2 and v4, the array [1,
     * 3, -1, 2, -1] should be used.
     *
     * @param coords an array of the sort described above.
     * @return the marginal sum specified.
     */
    public long calcMargin(int[] coords) {
        int[] coordCopy = internalCoordCopy(coords);

        int sum = 0;
        int i = -1;

        while (++i < coordCopy.length) {
            if (coordCopy[i] == -1) {
                for (int j = 0; j < this.table.getDimension(i); j++) {
                    coordCopy[i] = j;
                    sum += calcMargin(coordCopy);
                }

                coordCopy[i] = -1;
                return sum;
            }
        }

        return this.table.getValue(coordCopy);
    }

    /**
     * An alternative way to specify a marginal calculation. In this case, coords specifies a particular cell in the
     * table, and varIndices is an array containing the indices of the variables over which the margin sum should be
     * calculated. The sum is over the cell specified by 'coord' and all of the cells which differ from that cell in any
     * of the specified coordinates.
     *
     * @param coords     an <code>int[]</code> value
     * @param marginVars an <code>int[]</code> value
     * @return an <code>int</code> value
     */
    public long calcMargin(int[] coords, int[] marginVars) {
        int[] coordCopy = internalCoordCopy(coords);

        for (int marginVar : marginVars) {
            coordCopy[marginVar] = -1;
        }

        return calcMargin(coordCopy);
    }

    /**
     * Makes a copy of the coordinate array so that the original is not messed up.
     */
    private int[] internalCoordCopy(int[] coords) {
        int[] coordCopy = Arrays.copyOf(coords, coords.length);

//        if ((this.coordCopy == null) ||
//                (this.coordCopy.length != coords.length)) {
//            this.coordCopy = new int[coords.length];
//        }
//
//        System.arraycopy(coords, 0, this.coordCopy, 0, coords.length);

        return coordCopy;
    }

    private int getMissingValue() {
        return this.missingValue;
    }

    public void setMissingValue(int missingValue) {
        this.missingValue = missingValue;
    }

    public long getValue(int[] testCell) {
        return this.table.getValue(testCell);
    }
}





