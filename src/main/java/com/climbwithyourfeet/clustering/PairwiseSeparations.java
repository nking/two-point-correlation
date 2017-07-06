package com.climbwithyourfeet.clustering;

import algorithms.connected.ConnectedValuesGroupFinder;
import algorithms.misc.MinMaxPeakFinder;
import algorithms.misc.MiscMath0;
import algorithms.misc.MiscSorter;
import algorithms.util.PixelHelper;
import algorithms.util.PolygonAndPointPlotter;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author nichole
 */
public class PairwiseSeparations {
    
    private boolean debug = false;
    
    private final Logger log = Logger.getLogger(this.getClass().getName());
    
    public void setToDebug() {
        debug = true;
    }

    public static class ScaledPoints {
        public TIntSet pixelIdxs;
        int width;
        int height;
        int xScale;
        int yScale;
    }
    
    public BackgroundSeparationHolder extract(TIntSet pixelIdxs, int width, 
        int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException(
                "pixelIdxs.size must be 12 or more");
        }
        
        // these are the non-point distances to the points        
        int[][] dt = DistanceTransformUtil.transform(pixelIdxs, width, height);
        
        for (int i = 0; i < dt.length; ++i) {
            for (int j = 0; j < dt[i].length; ++j) {
                int d = dt[i][j];
                if (d > 0) {
                    if (d > 2) {
                        dt[i][j] = (int)Math.round(Math.sqrt(d));
                    }
                }
            }
        }
        
        /*
        within dt 
           want to find the local maxima in which the surrounding points are
           all smaller in value.
        
           need to use a connected value group finder to gather the adjacent
              pixels w/ same values > 0
        
           need to make an adjacency map of those groups.
        
           then will find which groups, that is values, are the maxima.
        
           the critical separation is the smallest of the maxima, but will
               want to look at the counts of that where possible.
        
        caveats:
           single group of points in center will have no maxima found as 
               described unless there are gaps in points in the cluster.
               if there are gaps,
                  that will be found.
               else the background separation will be found to be 1
        */
               
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
                
        ConnectedValuesGroupFinder finder = new ConnectedValuesGroupFinder();
        finder.setMinimumNumberInCluster(1);
        List<TIntSet> valueGroups = finder.findGroups(dt);
     
        long ts = System.currentTimeMillis();
        
        if (debug) {
            float[] x = new float[valueGroups.size()];
            float[] y = new float[x.length];
            for (int i = 0; i < valueGroups.size(); ++i) {
                TIntSet group = valueGroups.get(i);
                int tPix = group.iterator().next();
                ph.toPixelCoords(tPix, width, xy);
                int v = dt[xy[0]][xy[1]];
                x[i] = v;
                y[i] = group.size();
            }
            float xMax = MiscMath0.findMax(x);
            float yMin = MiscMath0.findMin(y);
            float yMax = MiscMath0.findMax(y);
            PolygonAndPointPlotter plotter;
            try {
                plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0, xMax, yMin, yMax, x, y, x, y,
                    "separation");
                plotter.writeFile("_separation_" + ts);
            } catch (IOException ex) {
                Logger.getLogger(PairwiseSeparations.class.getName()).log(Level.SEVERE, null, ex);
            }
            Runtime.getRuntime().gc();
        }
        
        // key = index of values Group, value = adjacent indexes of valuesGroup
        TIntObjectMap<TIntSet> adjMap = createAdjacencyMap(
            valueGroups, width, height);

        // TODO: below here consider if aggregation of values within a tolerance
        // of similar values is needed.  The tolerance should depend upon the 
        // range of separations and on the value to be aggregated.
        //  ...such a correction can be done with kernel smoothing using 
        //     a kernel bandwidth fixed in the distance axis.
        //     if implemented with fast wavelet transforms, 
        //     the results are instead a K-Nearest Neighbors smoothing 
        //     unless the input is resampled to fixed spacing.

        // search for valueGroups which has value larger than all adj neighbors
        // key = valueGroups index, value = dt value for the group
        TIntIntMap groupMaximaIdxs = new TIntIntHashMap();
        
        // key = value, value = count
        TIntIntMap valueCounts = new TIntIntHashMap();
        
        int minMaxima = Integer.MAX_VALUE;
        
        for (int i = 0; i < valueGroups.size(); ++i) {
            
            if (!adjMap.containsKey(i)) {
                continue;
            }
            
            TIntSet group = valueGroups.get(i);
            
            int tPix = group.iterator().next();
            ph.toPixelCoords(tPix, width, xy);
            int v = dt[xy[0]][xy[1]];
            
            boolean allAreLower = true;
            
            TIntSet adj = adjMap.get(i);
            
            TIntIterator iter2 = adj.iterator();
            while (iter2.hasNext()) {
                int aIdx = iter2.next();
                TIntSet group2 = valueGroups.get(aIdx);
                int tPix2 = group2.iterator().next();
                ph.toPixelCoords(tPix2, width, xy);
                int v2 = dt[xy[0]][xy[1]];
            
                if (v2 > v) {
                    allAreLower = false;
                    break;
                }
            }
            
            if (allAreLower) {
                groupMaximaIdxs.put(i, v);
                int c = valueCounts.get(v);
                valueCounts.put(v, c + group.size());
                
                if (v < minMaxima) {
                    minMaxima = v;
                }
            }
        }
        
        adjMap = null;
        
        // look at frequency of groupMaximaIdxs values
        int[] maximaValues = new int[valueCounts.size()];
        int[] maximaCounts = new int[valueCounts.size()];
        TIntIntIterator iter = valueCounts.iterator();
        for (int i = 0; i < valueCounts.size(); ++i) {
            iter.advance();
            maximaValues[i] = iter.key();
            maximaCounts[i] = iter.value();
        }
        
        MiscSorter.sortBy1stArg(maximaValues, maximaCounts);
       
        if (debug) {
            int[] x = maximaValues;
            int[] y = maximaCounts;
            float xMax = MiscMath0.findMax(x);
            float yMin = MiscMath0.findMin(y);
            float yMax = MiscMath0.findMax(y);
            PolygonAndPointPlotter plotter;
            try {
                plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0, xMax, yMin, yMax, x, y, x, y,
                    "max sep");
            plotter.writeFile("_separation_maxima_" + ts);
            } catch (IOException ex) {
                Logger.getLogger(PairwiseSeparations.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
     
        float[] qs = MiscMath0.calcQuartiles(maximaCounts, true);
        System.out.println("qs=" + Arrays.toString(qs));
                
        MinMaxPeakFinder finder2 = new MinMaxPeakFinder();
        float avgMin = finder2.calculateMeanOfSmallest(maximaCounts, 0.03f);
        
        int minMaximaIdx = -1;
        int firstZeroIdx = -1;
        for (int i = 0; i < maximaValues.length; ++i) {
            int d = maximaValues[i];
            if (minMaximaIdx == -1 && (d == minMaxima)) {
                minMaximaIdx = i;
            }
            if (d > minMaxima) {
                int c = maximaCounts[i];
                if (c <= avgMin) {
                    firstZeroIdx = i;
                    break;
                }
            }
        }
        if (firstZeroIdx == -1) {
            firstZeroIdx = minMaximaIdx + 1;
            assert(firstZeroIdx < maximaValues.length);
        }
        
        int maxCountIdx0 = -1;
        int maxCount0 = Integer.MIN_VALUE;
        for (int i = 0; i < firstZeroIdx; ++i) {
            if (maximaCounts[i] > maxCount0) {
                maxCountIdx0 = i;
                maxCount0 = maximaCounts[i];
            }
        }
        minMaximaIdx = maxCountIdx0;
        minMaxima = maxCount0;
        
        System.out.println("found background separation=" 
            + maximaValues[minMaximaIdx]);
        
        BackgroundSeparationHolder h = new BackgroundSeparationHolder();
        
        h.approxH = 1;
        
        int m2 = (int)Math.round(maximaValues[minMaximaIdx]/Math.sqrt(2));
        h.setXYBackgroundSeparations(m2, m2);
        
        h.setTheThreeSeparations(new float[]{
            0, maximaValues[minMaximaIdx], 
            maximaValues[firstZeroIdx]});
        
        h.setAndNormalizeCounts(new float[]{
            maxCount0, maxCount0, maximaCounts[firstZeroIdx]});
       
        // calculate the errors for the 3 points
        float[] errors = new float[] {
            h.calcError(h.threeSCounts[0], h.threeS[0], 0, h.approxH),
            h.calcError(h.threeSCounts[1], h.threeS[1], 0, h.approxH),
            h.calcError(h.threeSCounts[2], h.threeS[2], 0, h.approxH)
        };
        h.setTheThreeErrors(errors);
        
        return h;
    }
    
    public ScaledPoints scaleThePoints(TIntSet pixelIdxs, int width, int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException(
                "pixelIdxs.size must be 12 or more");
        }
                
        ScaleFinder sf = new ScaleFinder();
        
        int[] xyScales = sf.find1D(pixelIdxs, width, height);
        
        TIntSet pixelIdxs2;
        int width2, height2;
        if (xyScales[0] <= 1 && xyScales[1] <= 1) {
            pixelIdxs2 = new TIntHashSet(pixelIdxs);
            width2 = width;
            height2 = height;
        } else if (xyScales[0] <= 1 && xyScales[1] > 1) {
            pixelIdxs2 = new TIntHashSet(pixelIdxs.size());
            width2 = width;
            height2 = height/xyScales[1];            
        } else if (xyScales[0] > 1 && xyScales[1] <= 1) {
            pixelIdxs2 = new TIntHashSet(pixelIdxs.size());
            width2 = width/xyScales[0];
            height2 = height;
        } else {
            // scale both axes
            pixelIdxs2 = new TIntHashSet(pixelIdxs.size());
            width2 = width/xyScales[0];
            height2 = height/xyScales[1];
        }
        
        if (pixelIdxs2.isEmpty()) {
            
            PixelHelper ph = new PixelHelper();
            int[] xy = new int[2];
            
            TIntIterator iter = pixelIdxs.iterator();
            while (iter.hasNext()) {
                
                int pixIsx = iter.next();
                
                ph.toPixelCoords(pixIsx, width, xy);
                
                if (xyScales[0] > 1) {
                    xy[0] /= xyScales[0];
                }
                if (xyScales[1] > 1) {
                    xy[1] /= xyScales[1];
                }
                
                int pixIdx2 = ph.toPixelIndex(xy[0], xy[1], width2);
                
                pixelIdxs2.add(pixIdx2);
            }
        }
        
        ScaledPoints sp = new ScaledPoints();
        sp.pixelIdxs = pixelIdxs2;
        sp.width = width2;
        sp.height = height2;
        sp.xScale = xyScales[0];
        sp.yScale = xyScales[1];
    
        return sp;
    }
    
    private TIntObjectMap<TIntSet> createAdjacencyMap(
        List<TIntSet> groupList, int width, int height) {
        
        TIntIntMap pixGroupMap = new TIntIntHashMap();
        for (int i = 0; i < groupList.size(); ++i) {
            TIntSet group = groupList.get(i);
            TIntIterator iter = group.iterator();
            while (iter.hasNext()) {
                int pixIdx = iter.next();
                assert(!pixGroupMap.containsKey(pixIdx));
                pixGroupMap.put(pixIdx, i);
            }
        }
        
        PixelHelper ph = new PixelHelper();
        int[] dx8 = new int[]{-1, -1,  0,  1, 1, 1, 0, -1};
        int[] dy8 = new int[]{ 0, -1, -1, -1, 0, 1, 1,  1};
        
        int[] xy = new int[2];
        
        int nBSLen = groupList.size();
        
        System.out.println("nBSLen=" + nBSLen);
        
        TIntObjectMap<TIntSet> adjMap = new TIntObjectHashMap<TIntSet>();
        
        for (int i = 0; i < groupList.size(); ++i) {
            TIntSet group = groupList.get(i);
            TIntIterator iter = group.iterator();
            TIntSet adj = adjMap.get(i);
            
            while (iter.hasNext()) {
                int pixIdx = iter.next();
                ph.toPixelCoords(pixIdx, width, xy);
                
                for (int k = 0; k < dx8.length; ++k) {
                    int vX = xy[0] + dx8[k];
                    int vY = xy[1] + dy8[k];
                    if (vX < 0 || vY < 0 || vX >= width || vY >= height) {
                        continue;
                    }
                    int pixIdx2 = ph.toPixelIndex(vX, vY, width);
                    
                    if (!pixGroupMap.containsKey(pixIdx2)) {
                        continue;
                    }
                    
                    int j = pixGroupMap.get(pixIdx2);
                    
                    if (i == j) {
                        continue;
                    }
                    
                    if (adj == null) {
                        adj = new TIntHashSet();
                        adjMap.put(i, adj);
                    }
                    adj.add(j);
                }
            }
        }
        
        return adjMap;
    }
    
    private void writeDebugImage(double[][] dt, String fileSuffix, int width, 
        int height) throws IOException {

        BufferedImage outputImage = new BufferedImage(width, height,
            BufferedImage.TYPE_BYTE_GRAY);

        WritableRaster raster = outputImage.getRaster();

        for (int i = 0; i < dt.length; ++i) {
            for (int j = 0; j < dt[0].length; ++j) {
                int v = (int)Math.round(dt[i][j]);
                raster.setSample(i, j, 0, v);
            }
        }

        // write to an output directory.  we have user.dir from system properties
        // but no other knowledge of users's directory structure
        URL baseDirURL = this.getClass().getClassLoader().getResource(".");
        String baseDir = null;
        if (baseDirURL != null) {
            baseDir = baseDirURL.getPath();
        } else {
            baseDir = System.getProperty("user.dir");
        }
        if (baseDir == null) {
            return;
        }
        File t = new File(baseDir + "/bin");
        if (t.exists()) {
            baseDir = t.getPath();
        } else if ((new File(baseDir + "/target")).exists()) {
            baseDir = baseDir + "/target";
        }

        // no longer need to use file.separator
        String outFilePath = baseDir + "/" + fileSuffix + ".png";

        ImageIO.write(outputImage, "PNG", new File(outFilePath));

        Logger.getLogger(this.getClass().getName()).info("wrote " + outFilePath);
    }
    
}
