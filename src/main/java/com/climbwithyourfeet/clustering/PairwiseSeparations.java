package com.climbwithyourfeet.clustering;

import algorithms.imageProcessing.FFTUtil;
import algorithms.imageProcessing.Filters;
import algorithms.misc.Complex;
import algorithms.misc.Frequency;
import algorithms.misc.Misc0;
import algorithms.util.PixelHelper;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
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
    
    private static class ScaledPoints {
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
        
        ScaledPoints sp = scaleThePoints(pixelIdxs, width, height);
        
        // these are the non-point distances to the points        
        int[][] dt = DistanceTransformUtil.transform(sp.pixelIdxs, sp.width, sp.height);
        
        for (int i = 0; i < dt.length; ++i) {
            for (int j = 0; j < dt[i].length; ++j) {
                int d = dt[i][j];
                if (d > 0) {
                    if (d > 2) {
                        d = (int)Math.round(Math.sqrt(d));
                        dt[i][j] = d;
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
        
           the critical separation is the largest of the maxima, but will
               want to look at the counts of that where possible.
        
        caveats:
            single group of points in center will have no maxima found as 
                described unless there are gaps in points in the cluster.
                if there are gaps,
                    that will be found.
                else the critical separation will be found to be 1
        
        Note that throughout the refactoring in code:
            -- the factor of 2 is dropped here for 2 points within distance
            -- the facotr of 2 should be dropped in the group finder
            -- a factor of 1/thresholdFactor should possibly be applied 
               here for the caveat case, for example
        */
        
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
