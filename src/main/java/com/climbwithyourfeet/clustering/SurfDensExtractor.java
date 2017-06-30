package com.climbwithyourfeet.clustering;

import algorithms.imageProcessing.DistanceTransform;
import algorithms.misc.Frequency;
import algorithms.misc.MiscMath0;
import algorithms.util.PolygonAndPointPlotter;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
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
    
    /**
     * extract surface density points using distance transform.
     * The runtime complexity is O(N_pixels).
     * 
     * @param pixelIdxs points within the bounds of width and height
     * @param width data width with expectation that range starts at 0.
     * @param height data height with expectation that range starts at 0.
     * @return 
     */
    public float[] extractSufaceDensity(TIntSet pixelIdxs,
        int width, int height) {
        
        DistanceTransform dtr = new DistanceTransform();
        int[][] distTrans = dtr.applyMeijsterEtAl(pixelIdxs, width, height);

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
            
            /*
            -- look at dt results
            -- look at x spacing only
            -- look at y spacing only
            */
            // calculate frequency of non-zero distances
            Frequency f = new Frequency();
            TIntList v = new TIntArrayList();
            TIntList c = new TIntArrayList();
            f.calcFrequency(distTrans, v, c, true);

            // calculate x spacings only:
            int[][] dtX = new int[distTrans.length][];
            for (int i = 0; i < distTrans.length; ++i) {
                dtX[i] = dtr.applyMeijsterEtAl1D(distTrans[i]);
            }
            TIntList vX = new TIntArrayList();
            TIntList cX = new TIntArrayList();
            f.calcFrequency(distTrans, vX, cX, true);
            
            // calculate y spacings only:
            int[][] distTrans2 = new int[distTrans[0].length][];
            for (int i = 0; i < distTrans[0].length; ++i) {
                distTrans2[i] = new int[distTrans.length];
            }
            for (int i = 0; i < distTrans.length; ++i) {
                for (int j = 0; j < distTrans[i].length; ++j) {
                    distTrans2[j][i] = distTrans[i][j];
                }
            }
            int[][] dtY = new int[distTrans2.length][];
            for (int i = 0; i < distTrans2.length; ++i) {
                dtY[i] = dtr.applyMeijsterEtAl1D(distTrans2[i]);
            }
            TIntList vY = new TIntArrayList();
            TIntList cY = new TIntArrayList();
            f.calcFrequency(distTrans2, vY, cY, true);
            
            try {
                PolygonAndPointPlotter plotter2 = new PolygonAndPointPlotter();
                
                int[] x = v.toArray(new int[v.size()]);
                int[] y = c.toArray(new int[c.size()]);
                float minX = 0;
                float maxX = 1.2f * MiscMath0.findMax(x);
                float minY = 0;
                float maxY = 1.2f * MiscMath0.findMax(y);
                plotter2.addPlot(minX, maxX, minY, maxY, x, y, x, y, 
                    "dist freq");
                
                x = vX.toArray(new int[vX.size()]);
                y = cX.toArray(new int[cX.size()]);
                maxX = 1.2f * MiscMath0.findMax(x);
                maxY = 1.2f * MiscMath0.findMax(y);
                plotter2.addPlot(minX, maxX, minY, maxY, x, y, x, y, 
                    "dist X freq");
                
                x = vY.toArray(new int[vY.size()]);
                y = cY.toArray(new int[cY.size()]);
                maxX = 1.2f * MiscMath0.findMax(x);
                maxY = 1.2f * MiscMath0.findMax(y);
                plotter2.addPlot(minX, maxX, minY, maxY, x, y, x, y, 
                    "dist Y freq");
                
                System.out.println("plot: " + 
                    plotter2.writeFile("dist_freqs+" 
                    + System.currentTimeMillis()));
            
            } catch (Exception e) {
                
            }
        }
        
        int w = distTrans.length;
        int h = distTrans[0].length;
        
        float[] values = new float[w * h];
        int count2 = 0;
        for (int i0 = 0; i0 < w; ++i0) {
            for (int j0 = 0; j0 < h; ++j0) {
                int v = distTrans[i0][j0];
                if (v > 0) {
                    values[count2] = (float) (1. / Math.sqrt(v));
                    count2++;
                }
            }
        }
        
        values = Arrays.copyOf(values, count2);
    
        return values;
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
