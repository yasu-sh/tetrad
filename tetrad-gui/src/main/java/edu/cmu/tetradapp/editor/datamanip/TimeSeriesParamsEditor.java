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

package edu.cmu.tetradapp.editor.datamanip;

import edu.cmu.tetrad.util.Parameters;
import edu.cmu.tetradapp.editor.ParameterEditor;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;

/**
 * @author Tyler Gibson
 */
public class TimeSeriesParamsEditor extends JPanel implements ParameterEditor {

    /**
     * The params.
     */
    private Parameters params;


    /**
     * Empty constructor that does nothing, call <code>setup()</code> to build panel.
     */
    public TimeSeriesParamsEditor() {
        super(new BorderLayout());
    }


    /**
     * Sets the parameters.
     */
    public void setParams(Parameters params) {
        this.params = params;
    }

    /**
     * Does nothing
     */
    public void setParentModels(Object[] parentModels) {

    }

    /**
     * Builds the panel.
     */
    public void setup() {
        SpinnerNumberModel model = new SpinnerNumberModel(this.params.getInt("numTimeLags", 1),
                0, Integer.MAX_VALUE, 1);
        JSpinner jSpinner = new JSpinner(model);
        jSpinner.setPreferredSize(jSpinner.getPreferredSize());

        model.addChangeListener(e -> {
            SpinnerNumberModel model1 = (SpinnerNumberModel) e.getSource();
            TimeSeriesParamsEditor.this.params.set("numTimeLags", model1.getNumber().intValue());
        });

        Box b1 = Box.createHorizontalBox();
        b1.add(new JLabel("Number of time lags: "));
        b1.add(Box.createHorizontalGlue());
        b1.add(Box.createHorizontalStrut(15));
        b1.add(jSpinner);
        b1.setBorder(new EmptyBorder(10, 10, 10, 10));
        add(b1, BorderLayout.CENTER);
    }

    public boolean mustBeShown() {
        return true;
    }
}




