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
public class ClusterFinderTest extends BaseTwoPointTest {
    
    private Logger log = Logger.getLogger(this.getClass().getName());

    boolean plotContours = false;
    boolean plotClusters = true;
    boolean setDebug = true;
    
    public void testFindRanGenClusters() throws Exception {
        
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
                
                PixelHelper ph = new PixelHelper();
                TLongSet pixIdxs = ph.convert(points, width);
                
                ClusterFinder clusterFinder
                    = new ClusterFinder(pixIdxs, width, height);

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
                        (int) Math.floor(minMaxXY[0] - 1),
                        (float) Math.ceil(minMaxXY[1] + 1),
                        (int) Math.floor(minMaxXY[2] - 1),
                        1.05f*(float) Math.ceil(minMaxXY[3] + 1),
                        points, groupList, 
                        clusterFinder.getBackgroundSeparationHolder().bckGndSep,
                        "ran" + ii + "_" + i);

                    plotter.writeFile("random_");
                }

                /*
                BackgroundSeparationHolder sh
                    = clusterFinder.getBackgroundSeparationHolder();

                StatsHelper kdsh = new StatsHelper();
                TLongFloatMap probMap = new TLongFloatHashMap();
                TLongFloatMap probEMap = new TLongFloatHashMap();

                kdsh.calculateProbabilities(
                    sh, allClusters, width, height, probMap, probEMap);
                */
                
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

                /*
                if (plotContours) {
                ContourPlotter plotter2 = new ContourPlotter();
                plotter2.writeFile(probMap, width, height,
                    "other_contour_" + i);
                }
                */
                count++;
            }
        }
        
        plotter.writeFile("random_");

        log.info("SEED=" + seed);
    }
    
    /**
     *
     * @throws Exception
     */
    public void testFindClustersOtherData() throws Exception {
        
        String[] fileNames = {
            "Aggregation.txt", 
            "Compound.txt", 
            "pathbased.txt",
            "spiral.txt",
            "D31.txt", 
            "R15.txt" , 
            "jain.txt",
            "flame.txt",
            "a1.txt", 
            "a2.txt", 
            "a3.txt",
            //"s1.txt", 
            
            //"s2.txt", 
            //"s3.txt", "s4.txt",
            //"birch1.txt", "birch2.txt", "birch3.txt"
        };
        
        //TODO: rewrite plotter to read from a file
        // instead of writing numbers in file
        ClusterPlotter plotter = new ClusterPlotter();
        
        for (int i = 0; i < fileNames.length; i++) {
        //for (int i = 8; i < fileNames.length; i++) {
        //for (int i = 6; i < 7; i++) {

            String fileName = fileNames[i];
            
            //NOTE:  for i=8, distance transform needs alot of memory for array size, so have divided numbers there by 10
            AxisIndexer indexer = CreateClusterDataTest.getUEFClusteringDataset(
                fileName);
            
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
                assertTrue(p.getX() > -1);
                assertTrue(p.getY() > -1);
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
            
            ClusterFinder clusterFinder
                = new ClusterFinder(pixIdxs, width, height);

            if (setDebug) {
                clusterFinder.setToDebug();
            }
            clusterFinder.setThreshholdFactor(1.f);

            clusterFinder.calculateBackgroundSeparation();

            clusterFinder.findClusters();

            int nGroups = clusterFinder.getNumberOfClusters();

            System.out.println("  nGroups=" + nGroups);

            float[] seps = clusterFinder.getBackgroundSeparationHolder()
                .bckGndSep;
            // 0:  0.3 to 0.7
            // 1:  0.3 ish
            // 2:  0.15 to 0.42
            // 3:  0.3 to 0.55
            // 4:  0.3 to 0.75
            // 5:  approx 0.35 to 0.75
            // 6:  0.285 to 0.35
            // 7:  0.1 to 0.3
            switch(i) {
                case 0:
                case 1:
                case 2:
                case 6:
                case 7:
            //        assertEquals(1.0f, seps[0]);
            //        assertEquals(1.0f, seps[1]);
                    break;                
                case 3:
                case 4:
                case 5:
           //         assertTrue(seps[0] >= 1.0f && seps[0] <= 2.0f);
           //         assertTrue(seps[1] >= 1.0f && seps[1] <= 2.0f);
                    break;
                default:
                    break;
            }
            
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
                    (float) Math.floor(minMaxXY[0] - 1),
                    (float) Math.ceil(minMaxXY[1] + 1),
                    0.95f*(float) Math.floor(minMaxXY[2] - 1),
                    1.05f*(float) Math.ceil(minMaxXY[3] + 1),
                    points, groupList,
                    clusterFinder.getBackgroundSeparationHolder().bckGndSep,
                    "other_" + i);

                plotter.writeFile("other_");
            }

            /*
            BackgroundSeparationHolder sh
                = clusterFinder.getBackgroundSeparationHolder();

            StatsHelper kdsh = new StatsHelper();
            TLongFloatMap probMap = new TLongFloatHashMap();
            TLongFloatMap probEMap = new TLongFloatHashMap();

            kdsh.calculateProbabilities(
                sh, allClusters, width, height, probMap, probEMap);
            */
            
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
            }
            */
            
            /*
            if (plotContours) {
            ContourPlotter plotter2 = new ContourPlotter();
            plotter2.writeFile(probMap, width, height, 
                "other_contour_" + i);
            }*/
        }
        
        plotter.writeFile("other_");
    }
    
    /**
     *
     * @throws Exception
     */
    public void estNoClusters() throws Exception {
        
        /* Goal of this test is to examine the substructure created by increasing numbers of randomly 
           placed points.
        
        NOTE: increased the threshold factor from 2.5 to 10 to assert that
        the clusters are not found above background for that threshold factor.
        
           
           1000 x 1000 unit^2 space to place
           
               N=100,450,900,4500,9000,14500,19000,24500,29000
               random points                
               
           And track, N, linear density, and the number of groups found.
           
                  N=(100)  calcLinDens=(0.1236)  expectedLinDens=(0.0100)  nGroups=(0)
                  N=(450)  calcLinDens=(0.1489)  expectedLinDens=(0.0212)  nGroups=(0)
                  N=(900)  calcLinDens=(0.1564)  expectedLinDens=(0.0300)  nGroups=(0)
                  N=(4500)  calcLinDens=(0.1580)  expectedLinDens=(0.0671)  nGroups=(125)
                  N=(9000)  calcLinDens=(0.1536)  expectedLinDens=(0.0949)  nGroups=(665)
                  N=(14500)  calcLinDens=(0.1716)  expectedLinDens=(0.1204)  nGroups=(1391)
                  N=(19000)  calcLinDens=(0.1747)  expectedLinDens=(0.1378)  nGroups=(2160)
                  N=(24500)  calcLinDens=(0.1553)  expectedLinDens=(0.1565)  nGroups=(2922)
                  N=(29000)  calcLinDens=(0.1544)  expectedLinDens=(0.1703)  nGroups=(2980)
               SEED=1386750505246
               
            Can see that after N=1000, begin to see groups form from randomly close points.
                roughly nGroups is less than or equal to (0.2*N)
         */
        
        int[] numberOfBackgroundPoints = new int[]{
            100,450,900,4500,9000,14500,19000,24500,29000
        };
        
        int[] nGroupsFound = new int[numberOfBackgroundPoints.length];
        float[] expectedLinearDensities = new float[nGroupsFound.length];
        float[] calcLinearDensities = new float[nGroupsFound.length];
        
        float xmin = 0;
        float xmax = 300;
        float ymin = 0;
        float ymax = 300;

        long seed = System.currentTimeMillis();

        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");

        //seed = 1387775326745l;

        log.info("SEED=" + seed);
        
        sr.setSeed(seed);

        AxisIndexer indexer = null;

        int count = 0;
        
        ClusterPlotter plotter = new ClusterPlotter();
        
        for (int ii = 0; ii < numberOfBackgroundPoints.length; ii++) { 
            
            int xyStartOffset = 0;
            
            float[] xb = new float[numberOfBackgroundPoints[ii]];
            float[] yb = new float[numberOfBackgroundPoints[ii]];
            
            double expectedDensity = Math.sqrt(numberOfBackgroundPoints[ii])/(xmax-xmin);

            createRandomPointsInRectangle(sr, numberOfBackgroundPoints[ii],
                xmin, xmax, ymin, ymax, xb, yb, xyStartOffset);
                    
            float[] xbe = new float[numberOfBackgroundPoints[ii]];
            float[] ybe = new float[numberOfBackgroundPoints[ii]];
            for (int i = 0; i < numberOfBackgroundPoints[ii]; i++) {
                // simulate x error as a percent error of 0.03 for each bin
                xbe[i] = xb[i] * 0.03f;
                ybe[i] = (float) (Math.sqrt(yb[i]));
            }
            
            indexer = new AxisIndexer();
            
            indexer.sortAndIndexX(xb, yb, xbe, ybe, xbe.length);
                        
            log.info(" " + ii + " (" + indexer.getNumberOfPoints() + " points) ... ");

            int[] minMaxXY = new int[4];
            minMaxXY[0] = Integer.MAX_VALUE;
            minMaxXY[1] = Integer.MIN_VALUE;
            minMaxXY[2] = Integer.MAX_VALUE;
            minMaxXY[3] = Integer.MIN_VALUE;
            Set<PairInt> points = new HashSet<PairInt>();
            for (int k = 0; k < indexer.getNXY(); ++k) {
                PairInt p = new PairInt(Math.round(indexer.getX()[k]),
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
                (int)Math.ceil(minMaxXY[1] + 1), 
                (int)Math.floor(minMaxXY[2] - 1), 
                (int)Math.ceil(minMaxXY[3] + 1), 
                points, groupList, 
                clusterFinder.getBackgroundSeparationHolder().bckGndSep,
                "no_clusters_" + ii);

            plotter.writeFile("no_clusters_");
            }
                
            float frac = ((float)nGroups/(float)points.size());
            log.info("nPoints=" + points.size() + " nGroups=" + nGroups
                + " frac=" + frac);
            
            assertTrue(frac < 0.2);
        }
        
        plotter.writeFile("no_clusters_");
        
        log.info("SEED=" + seed);
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
