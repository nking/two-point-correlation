package com.climbwithyourfeet.clustering;

import algorithms.imageProcessing.DistanceTransform;
import algorithms.misc.Frequency;
import algorithms.misc.MiscMath0;
import algorithms.util.PixelHelper;
import algorithms.util.PolygonAndPointPlotter;
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
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 * extract surface density points
 * 
 * @author nichole
 */
public class SurfDensExtractor {
    
    private boolean debug = false;
    
    private final Logger log = Logger.getLogger(this.getClass().getName());
    
    public void setToDebug() {
        debug = true;
    }
    
    public static class SurfaceDensityScaled {
        public float[] values = null;
        public int xyScaleFactor = 1;
        public float surfDensRes = 0.05f;
    }
    
    /**
     * extract surface density points using distance transform.
     * The runtime complexity is O(N_pixels).
     * 
     * @param pixelIdxs points within the bounds of width and height
     * @param width data width with expectation that range starts at 0.
     * @param height data height with expectation that range starts at 0.
     * @return 
     */
    public SurfaceDensityScaled extractSufaceDensity(TIntSet pixelIdxs,
        int width, int height) {
                
        DistanceTransform dtr = new DistanceTransform();
        int[][] distTrans = dtr.applyMeijsterEtAl(pixelIdxs, width, height);

        // calculate frequency of non-zero distances to see if need to re-sample
        // data
        Frequency f = new Frequency();
        TIntList vF = new TIntArrayList();
        TIntList cF = new TIntArrayList();
        f.calcFrequency(distTrans, vF, cF, true);

        int yMaxIdx = MiscMath0.findYMaxIndex(cF);
        
        SurfaceDensityScaled sds = new SurfaceDensityScaled();
        
        if (vF.get(yMaxIdx) > 1.0f) {
            
            TIntSet pixelIdxs2 = null;
            int width2, height2;
            
            while (true) {
                // factor to divide x or by:
                sds.xyScaleFactor = (int)Math.ceil(vF.get(yMaxIdx)*Math.sqrt(2));

                // factor to mult dist by would be sqrt(2) / xyScaleFactor

                // re-scale the points and re-do the distance transform
                pixelIdxs2 = new TIntHashSet(pixelIdxs.size());
                width2 = width/sds.xyScaleFactor;
                height2 = height/sds.xyScaleFactor;

                PixelHelper ph = new PixelHelper();
                int[] xy = new int[2];

                TIntIterator iter = pixelIdxs.iterator();
                while (iter.hasNext()) {
                    int pixIdx = iter.next();
                    ph.toPixelCoords(pixIdx, width, xy);
                    int pixIdx2 = ph.toPixelIndex(
                        xy[0]/sds.xyScaleFactor, xy[1]/sds.xyScaleFactor, width2);
                    pixelIdxs2.add(pixIdx2);
                }
                
                //if the number of pixels has been reduced too much,
                //the maximum index was likely not much larger than
                //the smaller index we are looking for
                
                if ((pixelIdxs.size()/pixelIdxs2.size()) < 3) {
                    break;
                }
                if (yMaxIdx == 0) {
                    break;
                }
                
                yMaxIdx = MiscMath0.findYMaxIndex(cF.subList(0, yMaxIdx));
            }
            
            distTrans = dtr.applyMeijsterEtAl(pixelIdxs2, width2, height2);            
        
            vF = new TIntArrayList();
            cF = new TIntArrayList();
            f.calcFrequency(distTrans, vF, cF, true);

            int yMaxIdx2 = MiscMath0.findYMaxIndex(cF);
            assert(yMaxIdx2 == 0);
        }
        
        if (debug) {

            log.info("print dist trans for " + pixelIdxs.size() + " points "
                + "within width=" + width + " height=" + height);

            int[] minMax = MiscMath0.findMinMaxValues(distTrans);

            log.info("min and max =" + Arrays.toString(minMax));

          //  try {
          //    writeDebugImage(distTrans, 
          //        Long.toString(System.currentTimeMillis()), width, height);
          //  } catch (IOException ex) {
          //      Logger.getLogger(this.getClass().getName()).log(Level.SEVERE, null, ex);
          //  }
            
            try {
                PolygonAndPointPlotter plotter2 = new PolygonAndPointPlotter();
                
                int[] x = vF.toArray(new int[vF.size()]);
                int[] y = cF.toArray(new int[cF.size()]);
                float minX = 0;
                float maxX = 1.2f * MiscMath0.findMax(x);
                float minY = 0;
                float maxY = 1.2f * MiscMath0.findMax(y);
                plotter2.addPlot(minX, maxX, minY, maxY, x, y, x, y, 
                    "dist freq");
                
                System.out.println("plot: " + 
                    plotter2.writeFile("dist_freqs+" 
                    + System.currentTimeMillis()));
            
            } catch (Exception e) {
                
            }
        }
        
        int w = distTrans.length;
        int h = distTrans[0].length;
        
        sds.values = new float[w * h];
        int count2 = 0;
        for (int i0 = 0; i0 < w; ++i0) {
            for (int j0 = 0; j0 < h; ++j0) {
                int v = distTrans[i0][j0];
                if (v > 0) {
                    sds.values[count2] = (float) (1. / Math.sqrt(v));
                    count2++;
                }
            }
        }
        
        sds.values = Arrays.copyOf(sds.values, count2);
    
        //TODO: determine a canonical surface density resolution
        
        return sds;
    }

    private void writeDebugImage(int[][] dt, String fileSuffix, int width, 
        int height) throws IOException {

        BufferedImage outputImage = new BufferedImage(width, height,
            BufferedImage.TYPE_BYTE_GRAY);

        WritableRaster raster = outputImage.getRaster();

        for (int i = 0; i < dt.length; ++i) {
            for (int j = 0; j < dt[0].length; ++j) {
                int v = dt[i][j];
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
        String outFilePath = baseDir + "/distance_transform_" + fileSuffix + ".png";

        ImageIO.write(outputImage, "PNG", new File(outFilePath));

        Logger.getLogger(this.getClass().getName()).info("wrote " + outFilePath);
    }
    
}
