package com.climbwithyourfeet.clustering;

import algorithms.compGeometry.clustering.twopointcorrelation.RandomClusterAndBackgroundGenerator.CLUSTER_SEPARATION;
import algorithms.compGeometry.clustering.twopointcorrelation.AxisIndexer;
import algorithms.compGeometry.clustering.twopointcorrelation.BaseTwoPointTest;
import algorithms.compGeometry.clustering.twopointcorrelation.CreateClusterDataTest;
import algorithms.util.ContourPlotter;
import algorithms.util.PairInt;
import algorithms.util.PixelHelper;
import algorithms.util.ResourceFinder;
import gnu.trove.iterator.TIntIterator;
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
public class DTClusterFinderKDE2Test extends BaseTwoPointTest {
    
    private Logger log = Logger.getLogger(this.getClass().getName());

    boolean plotContours = false;
    boolean plotClusters = true;
    boolean setDebug = true;
    
    /*
    looking at 2 datasets to consider regriding the points so that the most
    common spacing in the distance transform is "1"
    as a first step in making a canonical array of surface densities.
    
    then need to address the surface density 0.05 used to distinguish closely
    spaced frequencies from further.
    
    
    changing the spacing by factor 4 and looking at frequency of
    distance transform
    
    for spacing = default (1)
    var data_points_0 = [
    {x:1.0, y:3230.0},
    {x:2.0, y:2388.0},
    {x:4.0, y:2151.0},
    {x:5.0, y:3933.0},
    {x:8.0, y:1826.0},
    {x:9.0, y:1774.0},
    {x:10.0, y:3381.0},
    {x:13.0, y:3180.0},
    
    for spacing factor 4 times that:  
    (would expect same peaks at 4 times to 5.7 times higher x)
    
    {x:1.0, y:5579.0},
    {x:2.0, y:5574.0},
    {x:4.0, y:4790.0},
    {x:5.0, y:9570.0},  <====
    {x:8.0, y:3945.0},
    {x:9.0, y:4001.0},
    {x:10.0, y:7290.0}, <====
    {x:13.0, y:6210.0},
    {x:16.0, y:3230.0},
    {x:17.0, y:6217.0}, <====
    {x:18.0, y:2916.0},
    {x:20.0, y:5716.0},
    {x:25.0, y:7628.0}, <====
    {x:26.0, y:5205.0},
    {x:29.0, y:5200.0},
    {x:32.0, y:2388.0},
    {x:34.0, y:4732.0},
    
    // --------------------
    same look at dataset which is all clustered points, no background points
    {x:1.0, y:210.0},
    {x:2.0, y:87.0},
    {x:4.0, y:91.0},
    {x:5.0, y:83.0},
    
    and 4 times spacing:
    var data_points_0 = [
    {x:1.0, y:1809.0},
    {x:2.0, y:1802.0},
    {x:4.0, y:1076.0},
    {x:5.0, y:2141.0}, <====
    {x:8.0, y:610.0},
    {x:9.0, y:343.0},
    {x:10.0, y:533.0},
    {x:13.0, y:299.0},
    {x:16.0, y:210.0},
    {x:17.0, y:371.0},
    {x:18.0, y:105.0},
    {x:20.0, y:282.0},
    {x:25.0, y:293.0},
    {x:26.0, y:260.0},
    {x:29.0, y:244.0},
    */
    
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
        seed = 1491310649097L;

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
                
                int[] minMaxXY = new int[4];
                minMaxXY[0] = Integer.MAX_VALUE;
                minMaxXY[1] = Integer.MIN_VALUE;
                minMaxXY[2] = Integer.MAX_VALUE;
                minMaxXY[3] = Integer.MIN_VALUE;
                Set<PairInt> points = new HashSet<PairInt>();
                for (int k = 0; k < indexer.getNXY(); ++k) {
                    PairInt p = new PairInt(
                        Math.round(1.f*indexer.getX()[k]),
                        Math.round(1.f*indexer.getY()[k]));
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
                TIntSet pixIdxs = ph.convert(points, width);
                
                DTClusterFinder clusterFinder = 
                    new DTClusterFinder(pixIdxs, width, height);
                
                if (setDebug) {
                    clusterFinder.setToDebug();
                }
                //clusterFinder.setThreshholdFactor(threshFactor);

                clusterFinder.setCriticalDensityMethod(
                    DTClusterFinder.CRIT_DENS_METHOD.KDE);
                
                clusterFinder.calculateCriticalDensity();
                
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
                
                if (plotClusters) {
                plotter.addPlotWithoutHull(
                    (int)Math.floor(minMaxXY[0] - 1), 
                    (int)Math.ceil(minMaxXY[1] + 1), 
                    (int)Math.floor(minMaxXY[2] - 1), 
                    (int)Math.ceil(minMaxXY[3] + 1), 
                    points, groupList, clusterFinder.getCriticalDensity(), 
                    "ran" + ii + "_" + i);
                
                plotter.writeFile("random_kde_");
                }
                
                KDEDensityHolder dh = (KDEDensityHolder) clusterFinder.getDensities();

                KDEStatsHelper kdsh = new KDEStatsHelper();
                TIntFloatMap probMap = new TIntFloatHashMap();
                TIntFloatMap probEMap = new TIntFloatHashMap();

                kdsh.calculateProbabilities(
                    dh, allClusters, width, height, probMap, probEMap);

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
                    "other_contour_" + i);
                }
                
                count++;
            }
        }
        
        plotter.writeFile("random_kde_");

        log.info("SEED=" + seed);
    }
    
    /**
     *
     * @throws Exception
     */
    public void estFindClustersOtherData() throws Exception {
        
        String[] fileNames = {
            "Aggregation.txt", 
            "Compound.txt", 
            "Pathbased.txt" , 
            "Spiral.txt",
            "D31.txt", 
           "R15.txt" , 
            "Jain.txt", 
            "Flame.txt",
            //"a1.txt", "a2.txt", "a3.txt"
            /*,
            "s1.txt", "s2.txt", "s3.txt", "s4.txt",
            "birch1.txt", "birch2.txt", "birch3.txt" */
        };
        
        ClusterPlotter plotter = new ClusterPlotter();
        
        //for (int i = 0; i < fileNames.length; i++) {
        for (int i = 0; i < 1; i++) {

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
                    4 * Math.round(indexer.getX()[k]),
                    4 * Math.round(indexer.getY()[k]));
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
            TIntSet pixIdxs = ph.convert(points, width);
            
            DTClusterFinder clusterFinder
                = new DTClusterFinder(pixIdxs, width, height);

        //    if (setDebug) {
            clusterFinder.setToDebug();
        //    }
            
            clusterFinder.setCriticalDensityMethod(
                DTClusterFinder.CRIT_DENS_METHOD.KDE);
            
            clusterFinder.calculateCriticalDensity();
            clusterFinder.findClusters();
            //clusterFinder.setCriticalDensity(dens);

            int nGroups = clusterFinder.getNumberOfClusters();

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
            
            if (plotClusters) {
            plotter.addPlotWithoutHull(
                (int)Math.floor(minMaxXY[0] - 1), 
                (int)Math.ceil(minMaxXY[1] + 1), 
                (int)Math.floor(minMaxXY[2] - 1), 
                (int)Math.ceil(minMaxXY[3] + 1), 
                points, groupList, clusterFinder.getCriticalDensity(), 
                "other_" + i);
            }
            
            KDEDensityHolder dh = (KDEDensityHolder) clusterFinder.getDensities();

            KDEStatsHelper kdsh = new KDEStatsHelper();
            TIntFloatMap probMap = new TIntFloatHashMap();
            TIntFloatMap probEMap = new TIntFloatHashMap();
            
            kdsh.calculateProbabilities(
                dh, allClusters, width, height, probMap, probEMap);
            
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
                "other_contour_" + i);
            }
        }
        
        plotter.writeFile("other_kde_");
        
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
