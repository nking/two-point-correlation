package com.climbwithyourfeet.clustering;

import algorithms.connected.ConnectedValuesGroupFinder;
import algorithms.dimensionReduction.CURDecomposition;
import algorithms.disjointSets.DisjointSet2Node;
import algorithms.matrix.MatrixUtil;
import algorithms.misc.Histogram;
import algorithms.misc.MiscMath0;
import algorithms.util.FormatArray;
import algorithms.util.ResourceFinder;
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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.*;

/**
 *
 * @author nichole
 */
public class ClusterRecipesTest extends TestCase {

    public void test0() throws Exception {

        // if we had recipe ingredients,
        //    we could use them to make content-based recommendations
        //    or item-item collaborative filtering

        /*
        utility map w/ 0s for missing entries
         * 12008 users, 100 recipes.
         *         8 users made 2 recipe reviews.
         *         12000 users made 1 recipe review.
         *         extremely sparse dataset
         */
        /*RecipeReviewsReader reader = new RecipeReviewsReader();
        reader.readUserRecipeUtilityMatrix();
        double[][] userRecipeStars = reader.getUserRecipeStars();
        */
        AmazonFoodReviewsReader reader = new AmazonFoodReviewsReader();
        reader.readUserProductUtilityMatrix(25);
        double[][] userRecipeStars = reader.getUserProductScore();

        boolean printClusters = true;

        final String sep = System.getProperty("file.separator");
        String outDir = ResourceFinder.findOutputTestDirectory();
        FileWriter pOut = null;
        if (printClusters) {
            String path = outDir + sep + "utility_matrix_clusters.csv";
            pOut = new FileWriter(path, StandardCharsets.UTF_8);
        }

        // when k = 2, takes many tries to get a decent random selection.
        int k = 4;//2;
        // k=50 took 7+-5 iterations for good svd.s
        // k=25 took 1 iterations ...
        // k=11 took 2 iterations ...
        // k=2 took 40+-30 iterations ...
        MatrixUtil.SVDProducts svd = null;
        int sTrys = 0;
        while (true) {
            ++sTrys;
            try {
                svd = CURDecomposition.calculateDecomposition(userRecipeStars, k).getApproximateSVD();
            } catch (IllegalArgumentException ex) {
                continue;
            }
            if (svd.s.length < 2 || svd.s[0] == 0. || svd.s[1] == 0. || ((svd.s[0]/svd.s[1]) > 100)) {
                continue;
            }
            if (svd.s[0] <= 1E4) {
                break;
            }
        }
        System.out.println("s=" + Arrays.toString(svd.s) + " after " + sTrys + " attempts");

        System.out.println("vT dimensions = " + svd.vT.length + ", " + svd.vT[0].length);

        // reduce dimension to the 2 with largest eigenvalues
        /*
        m = 12008, n=100
        A * V = U * D
        A = [mxn] = U * D * V^T
             U = [m x r]  cols are "concepts" (aka latent factors, latent dimensions)
             D = [r X r]     diag is "concepts"
             V^T = [r X n]   rows are "concepts"
               V = [n x r]
        U_p = (which is p=r columns of U from A = SVD(A).U * SVD(A).D * SVD(A).V^T
             where length of each column is 18184
        V^T_p = [p x n]
        V_p = [n x p]   which is [100 x 2]

        A * V_p = [m x p]
           which gives us a columns of "concept" scores for each user (user is a row).
           can perform clustering on the first 2 columns.
        */
        // vT is 25 x 100
        int p = 2; // can do this for larger than 2 dimensions, but with increasing num of dimensions, distinguishing differences between points become smaller
        double[][] vp = MatrixUtil.transpose(svd.vT); // 100 x 25
        vp = MatrixUtil.copySubMatrix(vp, 0, vp.length - 1, 0, p - 1);
        // [12008x100] * [100x2] = [12008 x 2]
        double[][] projected = MatrixUtil.multiply(userRecipeStars, vp);

        TDoubleList c0 = new TDoubleArrayList();
        TDoubleList c1 = new TDoubleArrayList();
        TIntList indexes = new TIntArrayList();
        int i;
        for (i = 0; i < projected.length; ++i) {
            double x0 = projected[i][0];
            double x1 = projected[i][1];
            //if (!(x0 == 0. && x1 == 0.)) {
            if (x0 != 0. && x1 != 0.) {
                System.out.printf("%d [%.3f, %.3f]\n", i, x0, x1);
                c0.add(x0);
                c1.add(x1);
                indexes.add(i);
            }
        }
        System.out.printf("%d non-zero projected points\n", indexes.size());

        if (indexes.size() < 10) {
            System.out.printf("not enough points to build a histogram needed for clustering.\n");
            return;
        }

        // calculating all pairs distances is on the order of 1E8 space complexity
        // calculating all non-zero projected pairs distances is < 1E6 space complexity
        // calculating all one member non-zero projected pairs distances is ~ 1.5E7 space complexity

        /* find clusters within the 2 projected columns of the non-zero points.
         these are clusters of user's "concepts 0" and "concepts 1".
         see MMDS chaps 9 and 11.
         TODO: consider comparison to Latent Factor Analysis
         TODO: consider comparison to use of UMap

         "Mining of Massive Datasets" by Leskovec, Rajaraman, and Ullman.
         http://www.mmds.org/
         */

        /* make a histogram of the differences.
        Note that the crossValidationRiskEstimator algorithm expects that the data range of X was normalized
        to be between 0 and 1, inclusive.  Also, that h=1/m where m is pEst.length.
        */
        double[][] projectedNZ = MatrixUtil.zeros(c0.size(), 2);
        for (i = 0; i < c0.size(); ++i) {
            projectedNZ[i][0] = c0.get(i);
            projectedNZ[i][1] = c1.get(i);
        }

        int nNZ = projectedNZ.length;

        TDoubleList diffsNZ = new TDoubleArrayList();
        int j;
        for (i = 0; i < nNZ; ++i) {
            for (j = i+1; j < nNZ; ++j) {
                diffsNZ.add(calcDist(projectedNZ[i], projectedNZ[j]));
            }
        }

        long _n = (long)(nNZ * Math.log(nNZ)/Math.log(2));
        System.out.printf("projectedNZ.length=%d,diffsNZ.size=%d.  n*lg(n)=%d\n", nNZ, diffsNZ.size(), _n);

        // normalize diffsNZ to mean=0 and min=0, max=1
        //double[] mean = new double[1];
        //double[] stdev = new double[1];
        //double[] normProjectedNZ = Standardization.standardUnitNormalization(diffsNZ.toArray(), 1, mean, stdev);
        double[] minMaxProjectedNZ = minMaxScaling(diffsNZ.toArray());

        assert(diffsNZ.size() == minMaxProjectedNZ.length);

        double[] data = minMaxProjectedNZ;

        int n = diffsNZ.size();

        TIntSet nBinsS = new TIntHashSet();
        //nBinsS.add(Histogram.numberOfBinsFreedmanDiaconis(data));
        nBinsS.add(Histogram.numberOfBinsSturges(n));
        nBinsS.add(Histogram.numberOfBinsDoane(data));
        nBinsS.add(Histogram.numberOfBinsRice(n));
        nBinsS.add(Histogram.numberOfBinsSqrt(n));
        TIntList nBins = new TIntArrayList(nBinsS.toArray());
        nBins.sort();
        for (int nB = 5; nB < nBins.get(0); ++nB) {
            nBins.add(nB);
        }
        nBins.sort();
        double minS = Double.POSITIVE_INFINITY;
        int minNBins = 0;
        for (i = 0; i < nBins.size(); ++i) {
            int nB = nBins.get(i);
            if (nB == 0) {
                continue;
            }
            // hist[0] are the bin centers
            // hist[1] are the bin counts
            double[][] hist = Histogram.createHistogram(data, nB, 1.,0.);
            if (hist.length < 2 || hist[0].length < 5) {
                continue;
            }
            double[] pEst = divide(hist[1], data.length);
            double s = Histogram.crossValidationRiskEstimator(data.length, hist[0][1] - hist[0][0], pEst);
            if (s < minS) {
                minS = s;
                minNBins = nB;
            }
        }
        System.out.printf("smallest risk nBins=%d, all nBins tried=%s\n", minNBins, Arrays.toString(nBins.toArray()));
        double[][] hist = Histogram.createHistogram(data, minNBins, 1.,0.);

        double factor = 1.0;
        int critIdx = MiscMath0.findYMaxIndex(hist[1]);
        double pointSep = factor * hist[0][critIdx];

        ClusterFinder3 finder = new ClusterFinder3();
        List<TLongSet> groups = finder.findGroups(data, nNZ, pointSep);

        // get the original user ids using indexes
        List<TIntSet> groups0 = new ArrayList<TIntSet>();
        List<Set<String>> groups0UserIds = new ArrayList<Set<String>>();
        for (TLongSet group : groups) {
            TIntSet group0 = new TIntHashSet();
            groups0.add(group0);

            Set<String> group0UserId = new HashSet<String>();
            groups0UserIds.add(group0UserId);

            TLongIterator iter = group.iterator();
            while (iter.hasNext()) {
                long idx = iter.next();
                int oIdx = indexes.get((int)idx);
                group0.add(oIdx);

                String uId = reader.getUserIdxIdMap().get(oIdx);
                group0UserId.add(uId);
            }
            //System.out.printf("group %d userIds=%s\n", groups0UserIds.size() - 1,
            //        Arrays.toString(group0UserId.toArray()));
        }
        System.out.printf("\nfound %d clusters\n", groups0UserIds.size());

        // invert groups0 for cluster membership lookup
        /*TIntIntMap userIdClusterMap = new TIntIntHashMap();
        for (i = 0; i < groups0.size(); ++i) {
            TIntSet group0 = groups0.get(i);
            TIntIterator iter = group0.iterator();
            while (iter.hasNext()) {
                int idx = iter.next();
                userIdClusterMap.put(idx, i);
            }
        }*/

        ScatterChart01 plotter = new ScatterChart01();
        List<Double> x = new ArrayList<Double>();
        List<Double> y = new ArrayList<Double>();
        for (i = 0; i < projected.length; ++i) {
            x.add(projected[i][0]);
            y.add(projected[i][1]);
        }
        plotter.addXYData(x, y, Color.BLACK,"projected ("+projected.length+")");

        if (printClusters) {
            pOut.write(String.format("#cluster %d  userId   product scores\n", i));
        }
        Map<Integer, Set<String>> uniqueEntriesInClusters = new HashMap<>();
        for (i = 0; i < groups0.size(); ++i) {
            TIntSet group0 = groups0.get(i);
            x = new ArrayList<Double>();
            y = new ArrayList<Double>();
            TIntIterator iter = group0.iterator();
            Set<String> entries = new HashSet<>();
            uniqueEntriesInClusters.put(i, entries);
            while (iter.hasNext()) {
                int oIdx = iter.next();
                x.add(projected[oIdx][0]);
                y.add(projected[oIdx][1]);
                if (printClusters) {
                    String str = String.format(" %6d  %10s  %s\n", i, reader.getUserIdxIdMap().get(oIdx),
                            FormatArray.toString(userRecipeStars[oIdx], "%.0f"));
                    pOut.write(str);
                    entries.add(str);
                }
            }
            plotter.addXYData(x, y, getNextColorRGB(i), "z " + Integer.toString(i) + " ("+group0.size()+")");
        }

        XYChart chart = plotter.getChart();

        BitmapEncoder.saveBitmap(chart, outDir + sep + "projected_utility_matrix_clusters", BitmapEncoder.BitmapFormat.PNG);

        // looking for repeat entries... didn't clean the data
        for (int cId : uniqueEntriesInClusters.keySet()) {
            System.out.printf("\ncluster %d has %d unique rows of scores\n", cId, uniqueEntriesInClusters.get(cId).size());
        }
        // TODO: consider affects of associating the users in projected that are not in projectedNZ (hence not in groups0)
        //  to groups0 by proximity.

        //TODO: consider global and regional affects that can be applied to userRecipeStars
        // as summarized in MMDS ch09-recsys2.pdf

        //TODO: consider comparison to UMap

        //TODO: consider comparison to Latent Factor Analysis, iterative fit to factor matrices while excluding missing values

        if (printClusters) {
            if (pOut != null) {
                pOut.flush();
                pOut.close();
            }
        }
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
            initMap(diffs.length);
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

    // adapter from https://knowm.org/open-source/xchart/xchart-example-code/
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
