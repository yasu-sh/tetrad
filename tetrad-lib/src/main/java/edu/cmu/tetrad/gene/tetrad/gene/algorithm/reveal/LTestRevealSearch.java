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

package edu.cmu.tetrad.gene.tetrad.gene.algorithm.reveal;

import java.io.*;
import java.util.StringTokenizer;

/**
 * This is merely a main program used to read a binarized measurement data set
 * and to instantiate a RevealSearch and run one or more search methods of that
 * instance.
 *
 * @author Frank Wimberly
 */
public class LTestRevealSearch {
    static int ngenes = 5;
    static int ntimes = 400;

    static int[][] cases = new int[LTestRevealSearch.ntimes][LTestRevealSearch.ngenes];

    public static void main(final String[] argv) {

        final String fileName = argv[0];

        final InputStream s;
        StringTokenizer st;

        try {
            s = new FileInputStream(fileName);
        } catch (final IOException e) {
            System.out.println("Cannot open file " + fileName);
            return;
        }

        final BufferedReader in = new BufferedReader(new InputStreamReader(s));
        for (int k = 0; k < LTestRevealSearch.ntimes; k++) {
            try {
                st = new StringTokenizer(in.readLine());
                for (int j = 0; j < LTestRevealSearch.ngenes; j++) {
                    LTestRevealSearch.cases[k][j] = Integer.parseInt(st.nextToken(" "));
                }
            } catch (final IOException e) {
                System.out.println("Read error in " + fileName);
                return;
            }
        }

        //System.out.println("case 0 " + cases[0][0] + " " + cases[0][1] + " " +
        //                               cases[0][2] + " " + cases[0][3] + " " +
        //                               cases[0][4]);

        for (int k = 0; k < LTestRevealSearch.ntimes; k++) {
            for (int j = 0; j < LTestRevealSearch.ngenes; j++) {
                if (LTestRevealSearch.cases[k][j] == -1) {
                    LTestRevealSearch.cases[k][j] = 0;
                }
            }
        }

        final String[] names = {"Gene 0", "Gene 1", "Gene 2", "Gene 3", "Gene 4"};

        final RevealSearch rs = new RevealSearch(LTestRevealSearch.cases, names);

        final int lag = 1;
        rs.exhaustiveSearch(lag);
    }

}




