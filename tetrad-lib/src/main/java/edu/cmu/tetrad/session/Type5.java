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

package edu.cmu.tetrad.session;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetrad.util.TetradSerializableExcluded;


/**
 * A sample class to be wrapped in a SessionNode as a model.
 */
public class Type5 implements SessionModel, TetradSerializableExcluded {
    static final long serialVersionUID = 23L;

    /**
     * It should not be possible to constuct Type5, because it contains two arguments of the same type. There is in
     * principle no way to decide which argument should be passed in which position.
     */
    public Type5(Type1 model1, Type1 model2, Parameters parameters) {
    }

    public static Type5 serializableInstance() {
        return new Type5(Type1.serializableInstance(),
                Type1.serializableInstance(), new Parameters());
    }

    public boolean equals(Object o) {
        return (o instanceof Type5);
    }

    /**
     * Sets the name of the session model.
     */
    public void setName(String name) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    /**
     * @return the name of the session model.
     */
    public String getName() {
        return null;  //To change body of implemented methods use File | Settings | File Templates.
    }
}





