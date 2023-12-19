package com.climbwithyourfeet.clustering;

import algorithms.connected.ConnectedValuesGroupFinder;
import algorithms.dimensionReduction.CURDecomposition;
import algorithms.disjointSets.DisjointSet2Node;
import algorithms.matrix.MatrixUtil;
import algorithms.misc.Histogram;
import algorithms.misc.MiscMath0;
import algorithms.util.FormatArray;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.TDoubleList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TDoubleArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TIntHashSet;
import junit.framework.TestCase;
import org.knowm.xchart.BitmapEncoder;
import org.knowm.xchart.XYChart;
import org.knowm.xchart.XYChartBuilder;
import org.knowm.xchart.XYSeries;
import org.knowm.xchart.demo.charts.ExampleChart;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.*;
import java.io.*;
import java.util.List;
import java.util.*;
import java.util.logging.Logger;

/**
 *
 * @author nichole
 */
public class ClusterRecipesTest extends TestCase {

    private Logger log = Logger.getLogger(this.getClass().getSimpleName());

    public void test0() throws Exception {

        /*
        if (true) {
            //AmazonFoodReviewsReader reader = new AmazonFoodReviewsReader();
            //reader.readUserProductUtilityMatrix(25);
            AmazonFoodReviewsReaderWriter a = new AmazonFoodReviewsReaderWriter();
            //a.writeSortedProductFileForCleanedInput();
            //a.writeProductUseScoreFile(100);
            a.writeSortedProductFileForCleanedInput2();
            return;
        }*/

        // if we had recipe ingredients,
        //    we could use them to make content-based recommendations
        //    or item-item collaborative filtering

        /* find clusters within the 2 projected columns of the user-recipe utility matrix.
         these are clusters of user's "concepts 0" and "concepts 1".
         see MMDS chaps 9 and 11.
         TODO: consider comparison to Latent Factor Analysis.
              unfortunately, need dense matrices for this, even
              if implementing from scratch using Expectation-Maximization.
         TODO: consider comparison to use of UMap

         "Mining of Massive Datasets" by Leskovec, Rajaraman, and Ullman.
         http://www.mmds.org/
         */

        /*
        need to build the utility matrix of users vs products and reduce the dimensions to 2 using SVD or CUR Decomposition.

        I opted to use the sparse matrix tensors and linear algebra available from the pytorch API.
        see script src/test/python/AmazonFoodReviewsSparseUtilityMatrixHandler.py
        which requires files written by
        AmazonFoodReviewsReaderWriter a = new AmazonFoodReviewsReaderWriter();
        a.writeSortedProductFileForCleanedInput();

        this file reads files written by AmazonFoodReviewsSparseUtilityMatrixHandler.py
        and uses the FindClusters3 class to find the clusters in the 2 subsets of projected data.

         */

        String eol = System.getProperty("line.separator");
        String fSep = System.getProperty("file.separator");

        String inFilePath = AmazonFoodReviewsReaderWriter.testResDir + AmazonFoodReviewsReaderWriter.sep +
                "amazon_fine_food_reviews_projected_sep.txt";

        File f = new File(inFilePath);
        if (!f.exists()) {
            log.severe("ERROR: file " + inFilePath + " does not exist.  You must run the python script at " +
                    " src/test/python/AmazonFoodReviewsSparseUtilityMatrixHandler.py to write it.");
            return;
        }

        Double critSep = null;
        BufferedReader in = null;
        try {
            in = new BufferedReader(new FileReader(f));
            critSep = Double.parseDouble(in.readLine().trim());
        } finally {
            if (in != null) {
                in.close();
            }
        }

        if (critSep == null) {
            throw new IOException("ERROR: file " + inFilePath + " does not exist.  You must run the python script at " +
                    " src/test/python/AmazonFoodReviewsSparseUtilityMatrixHandler.py to write it.");
        }

        for (int subsetNumber : new int[]{1, 2}) {

            inFilePath = AmazonFoodReviewsReaderWriter.testResDir + AmazonFoodReviewsReaderWriter.sep +
                    "amazon_fine_food_reviews_projected_subset_"+subsetNumber+"_diffs.csv";

            f = new File(inFilePath);
            if (!f.exists()) {
                log.severe("ERROR: file " + inFilePath + " does not exist.  You must run the python script at " +
                        " src/test/python/AmazonFoodReviewsSparseUtilityMatrixHandler.py to write it.");
                return;
            }

            // userId1,userId2
            List<String> userId1 = new ArrayList<String>();
            List<String> userId2 = new ArrayList<String>();
            TDoubleList distance = new TDoubleArrayList();

            String[] items = null;
            in = null;
            try {
                in = new BufferedReader(new FileReader(f));
                String line = in.readLine();
                if (line == null) {
                    throw new IOException("could not read a line from " + inFilePath);
                }
                while (line != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        break;
                    }
                    items = line.split(",");
                    //userId1,useerId2,dist
                    userId1.add(items[0]);
                    userId2.add(items[1]);
                    distance.add(Double.parseDouble(items[2]));
                    line = in.readLine();
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }

            int n = distance.size();
            log.info(String.format("read %d distances\n", n));

            //distances.size() == n0*(n0-1)/2.
            int n0 = (int) Math.ceil(Math.sqrt(n * 2 - 1));// sqrt(2*nd+1)

            inFilePath = AmazonFoodReviewsReaderWriter.testResDir + AmazonFoodReviewsReaderWriter.sep +
                    "amazon_fine_food_reviews_projected_subset_"+subsetNumber+".csv";
            f = new File(inFilePath);
            if (!f.exists()) {
                log.severe("ERROR: file " + inFilePath + " does not exist.  You must run the python script at " +
                        " src/test/python/AmazonFoodReviewsSparseUtilityMatrixHandler.py to write it.");
                return;
            }

            // these indexes are also the pair indexes in ClusterFinder3
            Map<Integer, String> projLineIdUserId = new HashMap<>();
            List<Double> xProj = new ArrayList<>();
            List<Double> yProj = new ArrayList<>();
            in = null;
            int i = 0;
            try {
                in = new BufferedReader(new FileReader(f));
                String line = in.readLine();
                if (line == null) {
                    throw new IOException("could not read a line from " + inFilePath);
                }
                while (line != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        break;
                    }
                    items = line.split(",");
                    // userId,proj[0],proj[1]

                    projLineIdUserId.put(i, items[0]);
                    xProj.add(Double.parseDouble(items[1]));
                    yProj.add(Double.parseDouble(items[2]));

                    line = in.readLine();
                    ++i;
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }
            assert (i == n0);


            ClusterFinder3 finder = new ClusterFinder3();
            List<TLongSet> groups = finder.findGroups(distance.toArray(), n0, critSep);

            System.out.printf("\nfound %d clusters\n", groups.size());

            ScatterChart01 plotter = new ScatterChart01();
            plotter.addXYData(xProj, yProj, Color.BLACK, "projected (" + xProj.size() + ")");

            List<Double> xC;
            List<Double> yC;
            for (i = 0; i < groups.size(); ++i) {
                TLongSet group = groups.get(i);
                xC = new ArrayList<Double>();
                yC = new ArrayList<Double>();
                TLongIterator iter2 = group.iterator();
                while (iter2.hasNext()) {
                    int idx = (int) iter2.next();
                    xC.add(xProj.get(idx));
                    yC.add(yProj.get(idx));
                }
                plotter.addXYData(xC, yC, getNextColorRGB(i), "z " + Integer.toString(i) + " (" + group.size() + ")");
            }

            XYChart chart = plotter.getChart();

            String outFilePath = "." + fSep + "bin" + fSep + "test-classes" + fSep +
                    "amazon_fine_food_reviews_projected_subset_"+subsetNumber+"_clusters.png";
            BitmapEncoder.saveBitmap(chart, outFilePath, BitmapEncoder.BitmapFormat.PNG);

            log.info("wrote " + outFilePath);

            outFilePath = "." + fSep + "bin" + fSep + "test-classes" + fSep +
                    "amazon_fine_food_reviews_projected_subset_"+subsetNumber+"_clusters.csv";
            BufferedWriter out = null;
            try {
                out = new BufferedWriter(new FileWriter(outFilePath));
                out.write("#cluster %d  userId\n");
                for (i = 0; i < groups.size(); ++i) {
                    TLongIterator iter2 = groups.get(i).iterator();
                    out.write(Integer.toString(i));
                    out.write(",");
                    while (iter2.hasNext()) {
                        int idx = (int) iter2.next();
                        out.write(projLineIdUserId.get(idx));
                        out.write(eol);
                    }
                }
            } finally {
                if (out != null) {
                    out.flush();
                    out.close();
                }
            }

            log.info("wrote " + outFilePath);
        }

        // TODO: consider affects of associating the users in projected that are not in projectedNZ (hence not in groups0)
        //  to groups0 by proximity.
    }

    public Color getNextColorRGB(int clrCount) {

        if (clrCount == -1) {
            clrCount = 0;
        }

        clrCount = clrCount % 6;

        switch (clrCount) {
            case 0:
                return Color.BLUE;
            case 1:
                return Color.PINK;
            case 2:
                return Color.GREEN;
            case 3:
                return Color.RED;
            case 4:
                return Color.CYAN;
            case 5:
                return Color.MAGENTA;
            default:
                return Color.BLUE;
        }
    }

    /**
     * scale values to range [0,1.] inclusive
     * @param a
     * @return
     */
    private double[] minMaxScaling(double[] a) {
        double[] minMax = MiscMath0.getMinMax(a);
        double range = minMax[1] - minMax[0];
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; ++i) {
            out[i] = (a[i] - minMax[0])/range;
        }
        return out;
    }

    private double[] divide(double[] a, double div) {
        double[] out = new double[a.length];
        for (int i = 0; i < a.length; ++i) {
            out[i] = a[i]/div;
        }
        return out;
    }

    private double calcDist(double[] x0, double[] x1) {
        return Math.sqrt(Math.pow(x0[0] - x1[0], 2) + Math.pow(x0[1] - x1[1], 2));
    }

    private void checkForNan(double[][] c, String label) {
        for (int i = 0;i < c.length; ++i) {
            for (int j = 0; j < c[i].length; ++j) {
                if (Double.isNaN(c[i][j])) {
                    System.out.printf("NAN %s[%d][%d]", label, i, j);
                }
            }
        }
    }

    static class ClusterFinder3 extends ConnectedValuesGroupFinder {

        /**
         *
         * @param diffs
         * @param n0 the original number of rows for data used to create pair diffs
         * @param sep
         * @return list of sets of indices relative to the original data that the pair diffs were created from.
         * the sets of indices are points that have a difference <= sep with another point within the same set.
         * the sets are clusters.
         */
        public List<TLongSet> findGroups(double[] diffs, int n0, double sep) {
            initMap(n0);
            findClustersIterative(diffs, n0, sep);
            List<TLongSet> groupList = prune();
            return groupList;
        }
        private void initMap(int n) {
            super.pixNodes = new TLongObjectHashMap<DisjointSet2Node<Long>>();
            for (int i = 0; i < n; ++i) {
                DisjointSet2Node<Long> pNode = disjointSetHelper.makeSet(new DisjointSet2Node<Long>(Long.valueOf(i)));
                pixNodes.put(i, pNode);
            }
        }
        private void findClustersIterative(double[] diffs, int n0, double sep) {
            int dIdx = 0;
            int n = diffs.length;
            double d;
            int i;
            int j;
            for (i = 0; i < n0; ++i) {
                for (j = i+1; j < n0; ++j) {
                    d = Math.abs(diffs[dIdx]);
                    if (d <= sep) {
                        processPair(i, j);
                    }
                    ++dIdx;
                }
            }
        }
    }

    // adapted from https://knowm.org/open-source/xchart/xchart-example-code/
    public class ScatterChart01 implements ExampleChart<XYChart> {

        final XYChart chart;

        public ScatterChart01(){
            // Create Chart
            chart = new XYChartBuilder().width(800).height(600).build();

            // Customize Chart
            chart.getStyler().setDefaultSeriesRenderStyle(XYSeries.XYSeriesRenderStyle.Scatter);
            chart.getStyler().setChartTitleVisible(false);
            chart.getStyler().setLegendPosition(Styler.LegendPosition.InsideSW);
            chart.getStyler().setMarkerSize(10);
        }

        public XYSeries addXYData(double[] x, double[] y, String label) {
            // Series
            List<Double> xData = new LinkedList<Double>();
            List<Double> yData = new LinkedList<Double>();
            for (int i = 0; i < x.length; i++) {
                xData.add(x[i]);
                yData.add(y[i]);
            }
            XYSeries series = chart.addSeries(label, xData, yData);
            return series;
        }
        public XYSeries addXYData(List<Double> xData, List<Double> yData, String label) {
            // Series
            XYSeries series = chart.addSeries(label, xData, yData);
            return series;
        }

        public void addXYData(List<Double> xData, List<Double> yData, java.awt.Color clr, String label) {
            XYSeries series = addXYData(xData, yData, label);
            //series.setLineColor(clr);
            series.setMarkerColor(clr);
            series.setMarker(SeriesMarkers.DIAMOND);
            //series.setLineStyle(SeriesLines.SOLID);
        }
        public void addXYData(double[] x, double[] y, java.awt.Color clr, String label) {
            XYSeries series = addXYData(x, y, label);
            //series.setLineColor(clr);
            series.setMarkerColor(clr);
            series.setMarker(SeriesMarkers.PLUS);
            //series.setLineStyle(SeriesLines.SOLID);
        }

        @Override
        public XYChart getChart() {
            return chart;
        }

        @Override
        public String getExampleChartName() {
            return null;
        }

    }

}
