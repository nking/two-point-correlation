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
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntFloatMap;
import gnu.trove.map.hash.TIntFloatHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
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
        TIntSet pixIdxs = new TIntHashSet();
        
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

        List<TIntSet> groupListPix = clusterFinder.getGroups();

        TIntIterator iter;
        int[] xy = new int[2];

        TIntSet allClusters = new TIntHashSet();

        List<Set<PairInt>> groupList = new ArrayList<Set<PairInt>>(groupListPix.size());
        for (int k = 0; k < groupListPix.size(); ++k) {
            Set<PairInt> set = new HashSet<PairInt>();
            iter = groupListPix.get(k).iterator();
            while (iter.hasNext()) {
                int pixIdx = iter.next();
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
        TIntFloatMap probMap = new TIntFloatHashMap();
        TIntFloatMap probEMap = new TIntFloatHashMap();

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
