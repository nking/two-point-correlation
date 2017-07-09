package com.climbwithyourfeet.clustering;

import algorithms.compGeometry.clustering.twopointcorrelation.RandomClusterAndBackgroundGenerator.CLUSTER_SEPARATION;
import algorithms.compGeometry.clustering.twopointcorrelation.AxisIndexer;
import algorithms.compGeometry.clustering.twopointcorrelation.BaseTwoPointTest;
import algorithms.compGeometry.clustering.twopointcorrelation.CreateClusterDataTest;
import algorithms.misc.Frequency;
import algorithms.util.ContourPlotter;
import algorithms.util.PairInt;
import algorithms.util.PixelHelper;
import algorithms.util.ResourceFinder;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntFloatMap;
import gnu.trove.map.TLongFloatMap;
import gnu.trove.map.hash.TIntFloatHashMap;
import gnu.trove.map.hash.TLongFloatHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.set.hash.TLongHashSet;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author nichole
 */
public class DTClusterFinder3Test extends BaseTwoPointTest {
    
    private Logger log = Logger.getLogger(this.getClass().getName());

    boolean plotContours = false;
    boolean plotClusters = true;
    boolean setDebug = true;
    
    /**
     *
     * @throws Exception
     */
    public void testFindSingleCenteredConvexCluster() throws Exception {
                
        ClusterPlotter plotter = new ClusterPlotter();
        
        int width = 20;
        int height = 20;
        
        // drawing a filled circle at (10, 10) of radius 5
        TLongSet pixIdxs = new TLongHashSet();
        
        /*
         9
         8
         7
         6
         5                   * * *
         4                 *       *
         3               *           *
         2             *               *
         1           *                   *
           0 1 2 3   * 6 7 8 9 0 1 2 3 4 *   7 8 9        
         9           *                   *
         8             *               *
         7               *           *
         6                 *       *
         5                   * * *
         4    
         3
         2
         1 
         0
           0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9
        */
        PixelHelper ph = new PixelHelper();
        //x=5:15, then -1
        int dr = 1;
        for (int y = 5; y <= 15; ++y) {
            //System.out.format("row %d  cols: %d to %d\n", y, 10-dr, 10+dr);
            for (int x = 10-dr; x <= 10+dr; ++x) {
                pixIdxs.add(ph.toPixelIndex(x, y, width));
            }
            if (y < 9) {
                dr++;
            } else if (y > 10) {
                dr--;
            }
        }
        
        DTClusterFinder clusterFinder = 
            new DTClusterFinder(pixIdxs, width, height);

        if (setDebug) {
            clusterFinder.setToDebug();
        }
        clusterFinder.setThreshholdFactor(1.f);

        clusterFinder.calculateBackgroundSeparation();

        clusterFinder.findClusters();

        int nGroups = clusterFinder.getNumberOfClusters();

        System.out.println("  nGroups=" + nGroups);

        List<TLongSet> groupListPix = clusterFinder.getGroups();

        TLongIterator iter;
        int[] xy = new int[2];

        TLongSet allClusters = new TLongHashSet();

        List<Set<PairInt>> groupList = new ArrayList<Set<PairInt>>(groupListPix.size());
        for (int k = 0; k < groupListPix.size(); ++k) {
            Set<PairInt> set = new HashSet<PairInt>();
            iter = groupListPix.get(k).iterator();
            while (iter.hasNext()) {
                long pixIdx = iter.next();
                ph.toPixelCoords(pixIdx, width, xy);
                PairInt p = new PairInt(xy[0], xy[1]);
                set.add(p);
                allClusters.add(pixIdx);
            }
            groupList.add(set);
        }
        
        Set<PairInt> points = ph.convert(pixIdxs, width);

        if (plotClusters) {
        plotter.addPlotWithoutHull(
            0, width + 1, 0, height + 1, 
            points, groupList, 
            clusterFinder.getBackgroundSeparationHolder().bckGndSep,
            "circle_");

        plotter.writeFile("circle_");
        }

        BackgroundSeparationHolder sh = 
            clusterFinder.getBackgroundSeparationHolder();

        StatsHelper kdsh = new StatsHelper();
        TLongFloatMap probMap = new TLongFloatHashMap();
        TLongFloatMap probEMap = new TLongFloatHashMap();

        kdsh.calculateProbabilities(
            sh, allClusters, width, height, probMap, probEMap);

        /*
        float[] allProbs = new float[width * height];
        TIntFloatIterator iter2 = probMap.iterator();
        for (int i2 = 0; i2 < probMap.size(); ++i2) {
            iter2.advance();
            int pixIdx = iter2.key();
            float p = iter2.value();
            allProbs[pixIdx] = p;

            ph.toPixelCoords(pixIdx, width, xy);
            System.out.println(Arrays.toString(xy) + " p=" + p
                + " err=" + probEMap.get(pixIdx));
        }
        */

        if (plotContours) {
        ContourPlotter plotter2 = new ContourPlotter();
        plotter2.writeFile(probMap, width, height,
            "circle___");
        }
                
    }
    
    public void testWithADBScanFigure() throws IOException {
        
        AxisIndexer indexer = 
            CreateClusterDataTest.getWikipediaDBScanExampleData();
        
        int[] minMaxXY = new int[4];
        minMaxXY[0] = Integer.MAX_VALUE;
        minMaxXY[1] = Integer.MIN_VALUE;
        minMaxXY[2] = Integer.MAX_VALUE;
        minMaxXY[3] = Integer.MIN_VALUE;
        Set<PairInt> points = new HashSet<PairInt>();
        for (int k = 0; k < indexer.getNXY(); ++k) {
            PairInt p = new PairInt(
                Math.round(indexer.getX()[k]),
                Math.round(indexer.getY()[k]));
            points.add(p);
            if (p.getX() < minMaxXY[0]) {
                minMaxXY[0] = p.getX();
            }
            if (p.getY() < minMaxXY[2]) {
                minMaxXY[2] = p.getY();
            }
            if (p.getX() > minMaxXY[1]) {
                minMaxXY[1] = p.getX();
            }
            if (p.getY() > minMaxXY[3]) {
                minMaxXY[3] = p.getY();
            }
        }

        int width = minMaxXY[1] + 1;
        int height = minMaxXY[3] + 1;        
        
        ClusterPlotter plotter = new ClusterPlotter();

        PixelHelper ph = new PixelHelper();
        TLongSet pixIdxs = ph.convert(points, width);

        DTClusterFinder clusterFinder
            = new DTClusterFinder(pixIdxs, width, height);

        if (setDebug) {
            clusterFinder.setToDebug();
        }
        clusterFinder.setThreshholdFactor(1.f);

        //clusterFinder.setBackgroundSeparation(7, 7);//7 to 3 are fine
        clusterFinder.calculateBackgroundSeparation();

        clusterFinder.findClusters();

        int nGroups = clusterFinder.getNumberOfClusters();

        System.out.println("  nGroups=" + nGroups);

        List<TLongSet> groupListPix = clusterFinder.getGroups();

        TLongIterator iter;
        int[] xy = new int[2];

        TLongSet allClusters = new TLongHashSet();

        List<Set<PairInt>> groupList = new ArrayList<Set<PairInt>>(groupListPix.size());
        for (int k = 0; k < groupListPix.size(); ++k) {
            Set<PairInt> set = new HashSet<PairInt>();
            iter = groupListPix.get(k).iterator();
            while (iter.hasNext()) {
                long pixIdx = iter.next();
                ph.toPixelCoords(pixIdx, width, xy);
                PairInt p = new PairInt(xy[0], xy[1]);
                set.add(p);
                allClusters.add(pixIdx);
            }
            groupList.add(set);
        }

        if (plotClusters) {
            plotter.addPlotWithoutHull(
                (int) Math.floor(minMaxXY[0] - 1),
                (int) Math.ceil(minMaxXY[1] + 1),
                (int) Math.floor(minMaxXY[2] - 1),
                (int) Math.ceil(minMaxXY[3] + 1),
                points, groupList,
                clusterFinder.getBackgroundSeparationHolder().bckGndSep,
                "dbscan_");

            plotter.writeFile("dbcan_");
        }

        BackgroundSeparationHolder sh
            = clusterFinder.getBackgroundSeparationHolder();

        StatsHelper kdsh = new StatsHelper();
        TLongFloatMap probMap = new TLongFloatHashMap();
        TLongFloatMap probEMap = new TLongFloatHashMap();

        kdsh.calculateProbabilities(
            sh, allClusters, width, height, probMap, probEMap);

        /*
        float[] allProbs = new float[width * height];
        TIntFloatIterator iter2 = probMap.iterator();
        for (int i2 = 0; i2 < probMap.size(); ++i2) {
            iter2.advance();
            int pixIdx = iter2.key();
            float p = iter2.value();
            allProbs[pixIdx] = p;

            ph.toPixelCoords(pixIdx, width, xy);
            System.out.println(Arrays.toString(xy) + " p=" + p
                + " err=" + probEMap.get(pixIdx));
        }
         */
        if (plotContours) {
            ContourPlotter plotter2 = new ContourPlotter();
            plotter2.writeFile(probMap, width, height,
                "dbscan_contour2_");
        }
    }
    
    /**
     *
     * @param clrCount
     * @return
     */
    public int getNextColorRGB(int clrCount) {
        
        if (clrCount == -1) {
            clrCount = 0;
        }
        
        clrCount = clrCount % 6;
        
        int c = Color.BLUE.getRGB();
        switch (clrCount) {
            case 0:
                c = Color.BLUE.getRGB();
                break;
            case 1:
                c = Color.PINK.getRGB();
                break;
            case 2:
                c = Color.GREEN.getRGB();
                break;
            case 3:
                c = Color.RED.getRGB();
                break;
            case 4:
                c = Color.CYAN.getRGB();
                break;
            case 5:
                c = Color.MAGENTA.getRGB();
                break;
            default:
                break;
        }
        
        return c;
    }

}
