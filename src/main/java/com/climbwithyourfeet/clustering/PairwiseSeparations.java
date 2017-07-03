package com.climbwithyourfeet.clustering;

import algorithms.imageProcessing.FFTUtil;
import algorithms.imageProcessing.Filters;
import algorithms.misc.Complex;
import algorithms.misc.Misc0;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.TIntSet;
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
    
    public void extract(TIntSet pixelIdxs, int width, int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException(
                "pixelIdxs.size must be 12 or more");
        }
        
        //TODO: rewrite this method to use 1D methods for each axis
        
        //NOTE:  a look at extracting the factor that the 
        //    x and y axis of points can be scaled down by
    
        double[][] input = Misc0.convertToBinary(pixelIdxs, width, height);
        
        FFTUtil fftUtil = new FFTUtil();
        
        // forward, normalize
        Complex[][] fTr = fftUtil.create2DFFT(input, true, true);
    
        double minV = Double.POSITIVE_INFINITY;
        double maxV = Double.NEGATIVE_INFINITY;
        double[][] ftr2 = new double[width][];
        for (int i = 0; i < width; ++i) {
            ftr2[i] = new double[height];
            for (int j = 0; j < height; ++j) {
                
                ftr2[i][j] = fTr[i][j].abs();
                
                if (ftr2[i][j] < minV) {
                    minV = ftr2[i][j];
                }
                if (ftr2[i][j] > maxV) {
                    maxV = ftr2[i][j];
                }                
            }
        }
        
        // scale to range 0:255
        double range = maxV - minV;
        double rangeFactor = 255./range;
        
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {                
                ftr2[i][j] -= minV;
                ftr2[i][j] *= rangeFactor;
            }
        }
        
        try {
            writeDebugImage(ftr2, 
                "fft_" + System.currentTimeMillis(), width,
                height);
        } catch (IOException ex) {
            Logger.getLogger(PairwiseSeparations.class.getName()).log(Level.SEVERE, null, ex);
        }
        
        float[][] ftr3 = new float[width][];
        for (int i = 0; i < width; ++i) {
            ftr3[i] = new float[height];
            for (int j = 0; j < height; ++j) {
                ftr3[i][j] = (float)ftr2[i][j];
            }
        }
            
        Filters filters = new Filters();
        
        TIntList outputKeypoints0 = new TIntArrayList(); 
        TIntList outputKeypoints1 = new TIntArrayList();
        
        float thresholdRel = 0.85f;//0.1f;
        
        filters.peakLocalMax(ftr3, 0, thresholdRel,
            outputKeypoints0, outputKeypoints1);

        // when the points are separated by a larger scale than 1 regularly,
        // that pattern is apparent at
        // xScale = width/(spacing of brightest peaks)
        // and same for the y axis.
        
        
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
