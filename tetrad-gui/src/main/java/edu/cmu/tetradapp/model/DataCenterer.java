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

import edu.cmu.tetrad.data.*;
import edu.cmu.tetrad.graph.Node;
import edu.cmu.tetrad.util.Matrix;
import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableUtils;

import java.util.List;

/**
 * Converts a continuous data set to a correlation matrix.
 *
 * @author josephramsey
 */
public class DataCenterer extends DataWrapper {

    static final long serialVersionUID = 23L;

    //=============================CONSTRUCTORS==============================//
    private DataCenterer(DataWrapper wrapper, Parameters params) {
        DataModelList inList = wrapper.getDataModelList();
        DataModelList outList = new DataModelList();

        for (DataModel model : inList) {
            if (!(model instanceof DataSet)) {
                throw new IllegalArgumentException("Not a data set: " + model.getName());
            }

            DataSet dataSet = (DataSet) model;

            Matrix data2 = DataUtils.centerData(dataSet.getDoubleData());
            List<Node> list = dataSet.getVariables();
            DataSet dataSet2 = new BoxDataSet(new VerticalDoubleDataBox(data2.transpose().toArray()), list);
            dataSet2.setName(model.getName());
            outList.add(dataSet2);
        }

        setDataModel(outList);
        setSourceGraph(wrapper.getSourceGraph());

        LogDataUtils.logDataModelList("Conversion of data to centered form.", getDataModelList());
    }

    /**
     * Generates a simple exemplar of this class to test serialization.
     *
     * @see TetradSerializableUtils
     */
    public static PcRunner serializableInstance() {
        return PcRunner.serializableInstance();
    }

}
