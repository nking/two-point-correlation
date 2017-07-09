package com.climbwithyourfeet.clustering;

import algorithms.imageProcessing.FFTUtil;
import algorithms.imageProcessing.Filters;
import algorithms.misc.Complex;
import algorithms.misc.MiscSorter;
import algorithms.util.PixelHelper;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.TLongSet;
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
    
    public int[] find(TLongSet pixelIdxs, final int width, final int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException(
                "pixelIdxs.size must be 12 or more");
        }

        double[] xA = new double[width];
        double[] yA = new double[height];

        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        TLongIterator iter = pixelIdxs.iterator();
        while (iter.hasNext()) {
            long pixIdx = iter.next();
            ph.toPixelCoords(pixIdx, width, xy);
            
            //DEBUG
            if (xy[0] >= xA.length || xy[0] < 0) {
                int z = 0;
            }
            if (xy[1] >= yA.length || xy[1] < 0) {
                int z = 0;
            }
            
            xA[xy[0]]++;
            yA[xy[1]]++;
        }
                
        FFTUtil fftUtil = new FFTUtil();
        
        // forward, normalize
        Complex[] fftX = fftUtil.create1DFFT(xA, true);
        Complex[] fftY = fftUtil.create1DFFT(yA, true);
    
        float minVX = Float.POSITIVE_INFINITY;
        float maxVX = Float.NEGATIVE_INFINITY;
        float[] fftX2 = new float[width];
        for (int i = 0; i < width; ++i) {
            fftX2[i] = (float)fftX[i].abs();
            if (fftX2[i] < minVX) {
                minVX = fftX2[i];
            }
            if (fftX2[i] > maxVX) {
                maxVX = fftX2[i];
            }
        }
        
        float minVY = Float.POSITIVE_INFINITY;
        float maxVY = Float.NEGATIVE_INFINITY;
        float[] fftY2 = new float[height];
        for (int i = 0; i < height; ++i) {
            fftY2[i] = (float)fftY[i].abs();
            if (fftY2[i] < minVY) {
                minVY = fftY2[i];
            }
            if (fftY2[i] > maxVY) {
                maxVY = fftY2[i];
            }
        }
        
        // scale to range 0:255
        float rangeX = maxVX - minVX;
        float rangeFactorX = 255.f/rangeX;
        for (int i = 0; i < width; ++i) {
            fftX2[i] -= minVX;
            fftX2[i] *= rangeFactorX;
        }
        
        float rangeY = maxVY - minVY;
        float rangeFactorY = 255.f/rangeY;
        for (int i = 0; i < height; ++i) {
            fftY2[i] -= minVY;
            fftY2[i] *= rangeFactorY;
        }
        
        if (debug) {
            float[][] img = new float[1][];
            img[0] = fftX2;
            long ts = System.currentTimeMillis();
            try {
                writeDebugImage(img, "fft_X" + ts, 1, img[0].length);
                img[0] = fftY2;
                writeDebugImage(img, "fft_Y" + ts, 1, img[0].length);
            } catch (IOException ex) {
                Logger.getLogger(ScaleFinder.class.getName()).log(Level.SEVERE, 
                    null, ex);
            }
        }
          
        Filters filters = new Filters();
        
        TIntList outputMaximaX = new TIntArrayList();
        
        TIntList outputMaximaY = new TIntArrayList();
        
        float thresholdRel = 0.85f;//0.1f;
       
        //outputMaximaX are the indexes of fftX2
        
        filters.peakLocalMax(fftX2, 0, thresholdRel, outputMaximaX);
        
        filters.peakLocalMax(fftY2, 0, thresholdRel, outputMaximaY);

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

        //TODO: below here consider if aggregation of values within a tolerance
        //  of similar values is needed.  the tolerance should depend
        //  upon the range of separations and on the value to be aggregated
        
        TIntIntMap freqMap = new TIntIntHashMap();
        for (int i = 0; i < maxima.size(); ++i) {
            int v = maxima.get(i);
            int c = freqMap.get(v);
            freqMap.put(v, c + 1);
        }
        
        if (freqMap.size() <= 1) {
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
        /*int limit = Math.round(0.8f * maxCount);
        if (limit < 1) {
            limit = 1;
        }*/
        
        freqMap.clear();
        for (int i = vsc.length - 1; i > -1; --i) {
            //if (vsc[i] < limit) {
            //    break;
            //}
            for (int j = i - 1; j > -1; --j) {
                //if (vsc[j] < limit) {
                //    break;
                //}
                int d = Math.abs(vs[i] - vs[j]);
                int c = freqMap.get(d);
                freqMap.put(d, c + 1);
            }
        }
        
        if (freqMap.size() == 0) {
            return -1;
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
                int v = Math.round(dt[i][j]);
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
