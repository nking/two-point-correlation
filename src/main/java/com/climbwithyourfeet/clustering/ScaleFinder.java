package com.climbwithyourfeet.clustering;

import algorithms.imageProcessing.FFTUtil;
import algorithms.imageProcessing.Filters;
import algorithms.misc.Complex;
import algorithms.misc.Misc0;
import algorithms.misc.MiscSorter;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
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
public class ScaleFinder {
    
    private boolean debug = false;
    
    private final Logger log = Logger.getLogger(this.getClass().getName());
    
    public void setToDebug() {
        debug = true;
    }
    
    public int[] find(TIntSet pixelIdxs, int width, int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException(
                "pixelIdxs.size must be 12 or more");
        }
        
        //TODO: rewrite this method to use 1D methods for each axis
        
        double[][] input = Misc0.convertToBinary(pixelIdxs, width, height);
        
        FFTUtil fftUtil = new FFTUtil();
        
        // forward, normalize
        Complex[][] fTr = fftUtil.create2DFFT(input, true, true);
    
        float minV = Float.POSITIVE_INFINITY;
        float maxV = Float.NEGATIVE_INFINITY;
        float[][] ftr2 = new float[width][];
        for (int i = 0; i < width; ++i) {
            ftr2[i] = new float[height];
            for (int j = 0; j < height; ++j) {
                
                ftr2[i][j] = (float)fTr[i][j].abs();
                
                if (ftr2[i][j] < minV) {
                    minV = ftr2[i][j];
                }
                if (ftr2[i][j] > maxV) {
                    maxV = ftr2[i][j];
                }                
            }
        }
        
        // scale to range 0:255
        float range = maxV - minV;
        float rangeFactor = 255.f/range;
        
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {                
                ftr2[i][j] -= minV;
                ftr2[i][j] *= rangeFactor;
            }
        }
        
        try {
            writeDebugImage(ftr2, "fft_" + System.currentTimeMillis(), width,
                height);
        } catch (IOException ex) {
            Logger.getLogger(ScaleFinder.class.getName()).log(Level.SEVERE, 
                null, ex);
        }
          
        Filters filters = new Filters();
        
        TIntList outputMaximaX = new TIntArrayList(); 
        TIntList outputMaximaY = new TIntArrayList();
        
        float thresholdRel = 0.85f;//0.1f;
        
        filters.peakLocalMax(ftr2, 0, thresholdRel, outputMaximaX, 
            outputMaximaY);

        // when the points are separated by a larger scale than 1 regularly,
        // that pattern is apparent at
        // xScale = width/(spacing of brightest peaks)
        // and same for the y axis.
        
        int xSpacing = determineSpacing(outputMaximaX);
        
        int ySpacing = determineSpacing(outputMaximaY);
        
        int[] out = new int[2];
        
        if (xSpacing > 1) {
            out[0] = width/xSpacing;
        } else {
            out[0] = 1;
        }
        
        if (ySpacing > 1) {
            out[1] = height/ySpacing;
        } else {
            out[1] = 1;
        }
        
        return out;
    }
    
    private int determineSpacing(TIntList maxima) {
        
        if (maxima.size() < 2) {
            return -1;
        }
        
        // a brute force calculation of minimum separation between the
        // maxima, but wanting to use only the most frequent points
        // to do so.
        
        TIntIntMap freqMap = new TIntIntHashMap();
        for (int i = 0; i < maxima.size(); ++i) {
            int v = maxima.get(i);
            int c = freqMap.get(v);
            freqMap.put(v, c + 1);
        }
        
        if (freqMap.size() == 1) {
            return -1;
        }
        
        int[] vs = new int[freqMap.size()];
        int[] vsc = new int[vs.length];
        TIntIntIterator iter = freqMap.iterator();
        for (int i = 0; i < freqMap.size(); ++i) {
            iter.advance();
            vs[i] = iter.key();
            vsc[i] = iter.value();
        }
        MiscSorter.sortBy1stArg(vsc, vs);
        
        int maxCount = vsc[vsc.length - 1];
        int limit = (int)Math.round(0.8f * maxCount);
        if (limit < 1) {
            limit = 1;
        }
        
        freqMap.clear();
        for (int i = vsc.length - 1; i > -1; --i) {
            if (vsc[i] < limit) {
                break;
            }
            for (int j = i - 1; j > -1; --j) {
                if (vsc[j] < limit) {
                    break;
                }
                int d = Math.abs(vs[i] - vs[j]);
                int c = freqMap.get(d);
                freqMap.put(d, c + 1);
            }
        }
        
        // sort by highest frequency and smallest separation
        vs = new int[freqMap.size()];
        vsc = new int[vs.length];
        iter = freqMap.iterator();
        for (int i = 0; i < freqMap.size(); ++i) {
            iter.advance();
            vs[i] = iter.key();
            vsc[i] = iter.value();
        }
        
        //order by devreasing frequency vsc
        //    and increasing value vs
        MiscSorter.sortBy1stArgDecrThen2ndIncr(vsc, vs);
                
        return vs[0];
    }
    
     private void writeDebugImage(float[][] dt, String fileSuffix, int width, 
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
