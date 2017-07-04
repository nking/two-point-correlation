package com.climbwithyourfeet.clustering;

import algorithms.VeryLongBitString;
import algorithms.connected.ConnectedValuesGroupFinder;
import algorithms.imageProcessing.FFTUtil;
import algorithms.imageProcessing.Filters;
import algorithms.misc.Complex;
import algorithms.misc.Frequency;
import algorithms.misc.Misc0;
import algorithms.util.PixelHelper;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
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
    
    public void extract(TIntSet pixelIdxs, int width, int height) {
        
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
               else the critical separation will be found to be 1
        
        Note that throughout the refactoring in code:
            -- the factor of 2 is dropped here for 2 points within distance
            -- the factor of 2 should be dropped in the group finder
            -- a factor of 1/thresholdFactor should possibly be applied 
               here for the caveat case, for example
        */
                
        ConnectedValuesGroupFinder finder = new ConnectedValuesGroupFinder();
        List<TIntSet> valueGroups = finder.findGroups(dt);
        
        // key = index of values Group, value = adjacent indexes of valuesGroup
        TIntObjectMap<VeryLongBitString> adjMap = createAdjacencyMap(
            valueGroups, width, height);
            
        // search for valueGroups which has value larger than all neighbors
        TIntSet groupMaximaIdxs = new TIntHashSet();
        
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        for (int i = 0; i < valueGroups.size(); ++i) {
            
            if (!adjMap.containsKey(i)) {
                continue;
            }
            
            TIntSet group = valueGroups.get(i);
            
            int tPix = group.iterator().next();
            ph.toPixelCoords(tPix, width, xy);
            int v = dt[xy[0]][xy[1]];
            
            boolean allAreLower = true;
            
            VeryLongBitString adj = adjMap.get(i);
            
            int[] adjGroupIdxs = adj.getSetBits();
            for (int aIdx : adjGroupIdxs) {
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
                groupMaximaIdxs.add(i);
            }
        }
        
        // look at frequency of groupMaximaIdxs values
        // 
        
        
        /*
        Frequency f = new Frequency();
        TIntList values = new TIntArrayList();
        TIntList count = new TIntArrayList();
        f.calcFrequency(dt, values, count, true);
        */
        
    }
    
    public ScaledPoints scaleThePoints(TIntSet pixelIdxs, int width, int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException(
                "pixelIdxs.size must be 12 or more");
        }
                
        ScaleFinder sf = new ScaleFinder();
        
        int[] xyScales = sf.find(pixelIdxs, width, height);
        
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
        sp.height = height;
        sp.xScale = xyScales[0];
        sp.yScale = xyScales[1];
    
        return sp;
    }
    
    private TIntObjectMap<VeryLongBitString> createAdjacencyMap(
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
        
        TIntObjectMap<VeryLongBitString> adjMap 
            = new TIntObjectHashMap<VeryLongBitString>();
        
        for (int i = 0; i < groupList.size(); ++i) {
            TIntSet group = groupList.get(i);
            TIntIterator iter = group.iterator();
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
                    
                    VeryLongBitString adj = adjMap.get(i);
                    if (adj == null) {
                        adj = new VeryLongBitString(nBSLen);
                        adjMap.put(i, adj);
                    }
                    adj.setBit(j);
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
