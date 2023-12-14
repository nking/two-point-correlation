package com.climbwithyourfeet.clustering;

import algorithms.matrix.MatrixUtil;
import algorithms.util.ResourceFinder;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;

public class LungCancerReader {
    /*
    - Data was published in :
          Hong, Z.Q. and Yang, J.Y. "Optimal Discriminant Plane for a Small
          Number of Samples and Design Method of Classifier on the Plane",
          Pattern Recognition, Vol. 24, No. 4, pp. 317-324, 1991.
        - Donor: Stefan Aeberhard, stefan@coral.cs.jcu.edu.au
        - Date : May, 1992

    32 lines
    57 (1 class attribute, 56 predictive)
    attribute 1 is the class label.
        - All predictive attributes are nominal, taking on integer
          values 0-3
    Missing Attribute Values: Attributes 5 and 39 (*)
    (5 lines have missing data)

    9. Class Distribution:
        - 3 classes,
                1.)     9 observations
                2.)     13     "
                3.)     10     "

     reading the data into:
      y = column data 0, the class
      x = columns 1 through 56
     */

    private int[] y;
    private double[][] x;

    private int[] yNoMissingData;
    private double[][] xNoMissingData;

    public void readData() throws IOException {
        int missingValue = -1;
        int nRows = 32;
        int nCols = 57;
        y = new int[nRows];
        x = MatrixUtil.zeros(nRows, nCols);

        yNoMissingData = new int[nRows - 5];
        xNoMissingData = MatrixUtil.zeros(nRows - 5, nCols);

        int i;

        final String sep = System.getProperty("file.separator");
        String testDir = ResourceFinder.findTestResourcesDirectory();
        String path = testDir + sep + "ucl_ml_datasets" + sep + "lung+cancer" + sep + "lung-cancer_data.txt";
        File f = new File(path);
        if (!f.exists()) {
            throw new IOException("could not find file at " + path);
        }

        String[] items;

        FileReader reader = null;
        BufferedReader in = null;
        i = 0;
        int ii = 0;
        int j;
        try {
            in = new BufferedReader(new FileReader(f));

            String line = in.readLine();
            boolean missingData;
            while (line != null && i < nRows) {
                line = line.trim();
                if (line.isEmpty()) {
                    break;
                }
                missingData = false;
                items = line.split(",");
                assert(items.length == nCols);
                y[i] = Integer.parseInt(items[0]);
                for (j = 1; j < items.length; ++j) {
                    if (items[j].equals("?")) {
                        x[i][j] = missingValue;
                        missingData = true;
                    } else {
                        x[i][j] = Integer.parseInt(items[j]);
                    }
                }
                if (!missingData) {
                    yNoMissingData[ii] = Integer.parseInt(items[0]);
                    for (j = 1; j < items.length; ++j) {
                        xNoMissingData[ii][j] = Integer.parseInt(items[j]);
                    }
                    ++ii;
                }
                ++i;
                line = in.readLine();
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (reader != null) {
                reader.close();
            }
        }
    }

    public int[] getY() {
        return Arrays.copyOf(y, y.length);
    }

    public double[][] getX() {
        return MatrixUtil.copy(x);
    }

    public double[][] getXNoMissingData() {
        return MatrixUtil.copy(xNoMissingData);
    }

    public int[] getYNoMissingData() {
        return Arrays.copyOf(yNoMissingData, yNoMissingData.length);
    }

}
