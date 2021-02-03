package com.climbwithyourfeet.clustering;

import algorithms.compGeometry.clustering.twopointcorrelation.RandomClusterAndBackgroundGenerator.CLUSTER_SEPARATION;
import algorithms.compGeometry.clustering.twopointcorrelation.AxisIndexer;
import algorithms.compGeometry.clustering.twopointcorrelation.BaseTwoPointTest;
import algorithms.compGeometry.clustering.twopointcorrelation.CreateClusterDataTest;
import algorithms.util.ContourPlotter;
import algorithms.util.PairInt;
import algorithms.util.PixelHelper;
import algorithms.util.ResourceFinder;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.TLongFloatMap;
import gnu.trove.map.hash.TLongFloatHashMap;
import gnu.trove.set.TLongSet;
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
public class ClusterFinder2Test extends BaseTwoPointTest {
    
    //NOTE: these tests multiply the points by a scale factor to test that the
    // cluster finder still works at other scales.
    
    private Logger log = Logger.getLogger(this.getClass().getName());

    boolean plotContours = false;
    boolean plotClusters = true;
    boolean setDebug = false;
    
    /**
     *
     * @throws Exception
     */
    public void testFindRanGenClusters() throws Exception {
        
        //NOTE: high density results in using a higher threshold during the
        //   stage of finding clusters with the estimated critical density
        //float threshFactor = 2.5f;
        
        float xmin = 0;
        float xmax = 300;
        float ymin = 0;
        float ymax = 300;

        long seed = System.currentTimeMillis();
        //seed = 1491310649097L;

        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");

        log.info("SEED=" + seed);
        
        sr.setSeed(seed);

        int nSwitches = 4;

        int nIterPerBackground = 1;

        AxisIndexer indexer = null;

        int count = 0;
        
        ClusterPlotter plotter = new ClusterPlotter();

        //TODO: improve these simulated clusters and assert the numbers
        
        for (int ii = 0; ii < nIterPerBackground; ii++) {
            for (int i = 0; i < nSwitches; i++) {
                switch(i) {
                    case 0:
                        //~100
                        indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            //10, 100, 110, 100.0f);
                            3, 33, 33, 0.1f);
                        break;
                    case 1: {
                        int[] clusterNumbers = new int[]{50, 300, 1000};
                        int nBackgroundPoints = 500;
                        CLUSTER_SEPARATION clusterSeparation = CLUSTER_SEPARATION.LARGE;

                        indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            clusterNumbers, nBackgroundPoints, clusterSeparation);
                        break;
                    }
                    case 2: {
                        int[] clusterNumbers = new int[]{2000, 300, 1000};
                        int nBackgroundPoints = 10000;
                        CLUSTER_SEPARATION clusterSeparation = CLUSTER_SEPARATION.LARGE;

                        indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            clusterNumbers, nBackgroundPoints, clusterSeparation);
                        break;
                    }
                    default: {
                         // 100*100
                         indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            3, 30, 60, 100.0f);
                         break;
                    }
                }
               
                log.info(" " + count + " (" + indexer.getNXY() + " points) ... ");
                System.out.println(" " + count + " (" + indexer.getNXY() + " points) ... ");

                /*
                INCREASE SPACING by factor of 4
                */
                
                int xscl = 4;
                int yscl = 4;
                
                int[] minMaxXY = new int[4];
                minMaxXY[0] = Integer.MAX_VALUE;
                minMaxXY[1] = Integer.MIN_VALUE;
                minMaxXY[2] = Integer.MAX_VALUE;
                minMaxXY[3] = Integer.MIN_VALUE;
                Set<PairInt> points = new HashSet<PairInt>();
                for (int k = 0; k < indexer.getNXY(); ++k) {
                    PairInt p = new PairInt(
                       xscl*Math.round(indexer.getX()[k]),
                       yscl*Math.round(indexer.getY()[k]));
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
                
                PixelHelper ph = new PixelHelper();
                TLongSet pixIdxs = ph.convert(points, width);
                
                ClusterFinder clusterFinder = 
                    new ClusterFinder(pixIdxs, width, height);
                
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
                
                if (plotClusters) {
                    plotter.addPlotWithoutHull(
                        (int)Math.floor(minMaxXY[0] - 1), 
                        (float)Math.ceil(minMaxXY[1] + 1), 
                        (int)Math.floor(minMaxXY[2] - 1), 
                        1.05f*(float)Math.ceil(minMaxXY[3] + 1), 
                        points, groupList, 
                        clusterFinder.getBackgroundSeparationHolder().bckGndSep,
                        "ran" + ii + "_" + i);

                    plotter.writeFile("random2_");
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
                        "random_contour2_" + i);
                }
                
                count++;
            }
        }
        
        plotter.writeFile("random2_");

        log.info("SEED=" + seed);
    }
    
    private void printDT(int[][] dt) {
        
        int w = dt.length;
        int h = dt[0].length;
        
        StringBuilder sb2 = new StringBuilder();
        for (int j = 0; j < h; ++j) {
            sb2.append("row ").append(j).append(": ");
            for (int i = 0; i < w; ++i) {
                sb2.append(String.format(" %3d", dt[i][j]));
            }
            sb2.append("\n");
        }
        System.out.println(sb2.toString());
    }
    
    /**
     *
     * @throws Exception
     */
    public void testFindClustersOtherData() throws Exception {
        
        String[] fileNames = {
            "Aggregation.txt", 
            "Compound.txt", 
            "pathbased.txt" , 
            "spiral.txt",
            "D31.txt", 
           "R15.txt" , 
            "jain.txt", 
            "flame.txt",
            //"a1.txt", "a2.txt", "a3.txt"
            /*,
            "s1.txt", "s2.txt", "s3.txt", "s4.txt",
            "birch1.txt", "birch2.txt", "birch3.txt" */
        };
        
        ClusterPlotter plotter = new ClusterPlotter();
        
        for (int i = 0; i < fileNames.length; i++) {
        //for (int i = 2; i < fileNames.length; i++) {
        //for (int i = 3; i < 4; i++) {

            String fileName = fileNames[i];
            
            //NOTE:  for i=8, distance transform needs alot of memory for array size, so have divided numbers there by 10
            AxisIndexer indexer = CreateClusterDataTest.getUEFClusteringDataset(
                fileName);
            
            int xscl = 4;
            int yscl = 4;
                
            int[] minMaxXY = new int[4];
            minMaxXY[0] = Integer.MAX_VALUE;
            minMaxXY[1] = Integer.MIN_VALUE;
            minMaxXY[2] = Integer.MAX_VALUE;
            minMaxXY[3] = Integer.MIN_VALUE;
            Set<PairInt> points = new HashSet<PairInt>();
            for (int k = 0; k < indexer.getNXY(); ++k) {
                PairInt p = new PairInt(
                    xscl * Math.round(indexer.getX()[k]),
                    yscl * Math.round(indexer.getY()[k]));
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

            PixelHelper ph = new PixelHelper();
            TLongSet pixIdxs = ph.convert(points, width);

            ClusterFinder clusterFinder = 
                new ClusterFinder(pixIdxs, width, height);

            if (setDebug) {
                clusterFinder.setToDebug();
            }
            clusterFinder.setThreshholdFactor(1.f);

            clusterFinder.calculateBackgroundSeparation();

            clusterFinder.findClusters();

            float[] seps = clusterFinder.getBackgroundSeparationHolder().bckGndSep;
            // 0:  0.3 to 0.7
            // 1:  0.3 ish
            // 2:  0.15 to 0.42
            // 3:  0.3 to 0.55
            // 4:  0.3 to 0.75
            // 5:  approx 0.35 to 0.75
            // 6:  0.285 to 0.35
            // 7:  0.1 to 0.3
            switch (i) {
                case 0:
                case 1:
                case 2:
                case 6:
                case 7:
              //      assertEquals(1.0f*xscl, seps[0]);
              //      assertEquals(1.0f*xscl, seps[1]);
                    break;
                case 3:
                case 4:
                case 5:
              //      assertTrue(seps[0] >= 1.0f*xscl && seps[0] <= 2.0f*xscl);
              //      assertTrue(seps[1] >= 1.0f*xscl && seps[1] <= 2.0f*xscl);
                    break;
                default:
                    break;
            }
            
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
                    (int)Math.floor(minMaxXY[0] - 1), 
                    (int)Math.ceil(minMaxXY[1] + 1), 
                    (int)Math.floor(minMaxXY[2] - 1), 
                    (int)Math.ceil(minMaxXY[3] + 1), 
                    points, groupList,
                    clusterFinder.getBackgroundSeparationHolder().bckGndSep,
                    "other" + i);

                plotter.writeFile("other2_");
            }

            BackgroundSeparationHolder sh = 
                clusterFinder.getBackgroundSeparationHolder();

            StatsHelper kdsh = new StatsHelper();
            TLongFloatMap probMap = new TLongFloatHashMap();
            TLongFloatMap probEMap = new TLongFloatHashMap();

            kdsh.calculateProbabilities(
                sh, allClusters, width, height, probMap, probEMap);

            /*
            float[] allProbs = new float[width*height];
            TIntFloatIterator iter2 = probMap.iterator();
            for (int ii = 0; ii < probMap.size(); ++ii) {
                iter2.advance();
                int pixIdx = iter2.key();
                float p = iter2.value();
                allProbs[pixIdx] = p;
                
                ph.toPixelCoords(pixIdx, width, xy);
                System.out.println(Arrays.toString(xy) + " p=" + p + 
                    " err=" + probEMap.get(pixIdx));
            }*/
            
            if (plotContours) {
                ContourPlotter plotter2 = new ContourPlotter();
                plotter2.writeFile(probMap, width, height, 
                    "other_contour2_" + i);
            }
        }
        
        plotter.writeFile("other2_");
        
    }
    
    /**
     *
     * @param values
     * @param fileName
     * @return
     * @throws IOException
     */
    public static String writeDataset(float[] values, String fileName) throws 
        IOException {
        
        if (values == null) {
            throw new IllegalArgumentException("values cannot be null");
        }

        String outFilePath = ResourceFinder.findDirectory("bin") + "/" +
            fileName;
        
        FileOutputStream fs = null;
        ObjectOutputStream os = null;

        try {
            File file = new File(outFilePath);
            file.delete();
            file.createNewFile();

            fs = new FileOutputStream(file);
            os = new ObjectOutputStream(fs);
            
            os.writeInt(values.length);

            int count = 0;

            for (float v : values) {

                os.writeFloat(v);

                if ((count % 10) == 0) {
                    os.flush();
                }

                count++;
            }

            os.flush();

        } finally {

            if (os != null) {
                os.close();
            }
            if (fs != null) {
                fs.close();
            }
        }
        
        return outFilePath;        
    }
    
    private BufferedImage createImage(Set<PairInt> points, int width, int height) {
        
        BufferedImage outputImage = new BufferedImage(width, height, 
            BufferedImage.TYPE_INT_RGB);
        
        for (PairInt p : points) {
            int rgb = Color.WHITE.getRGB();
            outputImage.setRGB(p.getX(), p.getY(), rgb);
        }
        
        return outputImage;
    }

    private void addAlternatingColorPointSets(BufferedImage img, 
        List<Set<PairInt>> groups, int nExtraForDot) {
        
        int width = img.getWidth();
        int height = img.getHeight();
        
        int clrCount = -1;
        int clr = -1;
        
        for (int i = 0; i < groups.size(); ++i) {
                                    
            clr = getNextColorRGB(clrCount);
            
            Set<PairInt> group = groups.get(i);
            
            for (PairInt p : group) {
                
                int x = p.getX();
                int y = p.getY();

                for (int dx = (-1 * nExtraForDot); dx < (nExtraForDot + 1); dx++) {
                    float xx = x + dx;
                    if ((xx > -1) && (xx < (width - 1))) {
                        for (int dy = (-1 * nExtraForDot); dy < (nExtraForDot + 1); dy++) {
                            float yy = y + dy;
                            if ((yy > -1) && (yy < (height - 1))) {
                                img.setRGB((int) xx, (int) yy, clr);
                            }
                        }
                    }
                }
                img.setRGB(p.getX(), p.getY(), clr);
                clrCount++;
            }
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

    private void writeImage(BufferedImage img, String fileName) throws IOException {
        
        String outFilePath = ResourceFinder.findDirectory("bin") + "/" +
            fileName;
        
        ImageIO.write(img, "PNG", new File(outFilePath));
    }
}
