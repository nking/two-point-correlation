package com.climbwithyourfeet.clustering;

import algorithms.imageProcessing.DistanceTransform;
import algorithms.misc.Frequency;
import algorithms.misc.MinMaxPeakFinder;
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
     * @param pixelIdxs points within the bounds of width and height.
     * set should not be smaller than a dozen
     * @param width data width with expectation that range starts at 0.
     * @param height data height with expectation that range starts at 0.
     * @return 
     */
    public SurfaceDensityScaled extractSufaceDensity(TIntSet pixelIdxs,
        int width, int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException("pixelIdxs.size must be 12 or more");
        }
                
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
            
            /*
            yMaxIdx is used to scale down the pixel coordinates, reducing the
            spacing between them, so that the peak of distance transform
            non-zero distances in vC list is 1 in vF list
            
            yMaxIdx might be near in value to a cF value at a smaller index,
            so will find the maxima through yMaxIdx and use the first
            */
                      
            MinMaxPeakFinder mmpf = new MinMaxPeakFinder();
            float[] cFF = new float[yMaxIdx + 1];
            for (int ii = 0; ii <= yMaxIdx; ++ii) {
                cFF[ii] = cF.get(ii);
            }
            float minAvg = mmpf.calculateMeanOfSmallest(cFF, 0.03f);
            int[] peakIdxs = mmpf.findPeaks(cFF, minAvg, 2.0f);
                        
            if (peakIdxs != null && peakIdxs.length > 0) {
               
                int peakIdx = 0;
                if (peakIdxs[0] == 0 && peakIdxs.length == 1) {
                    //this is the significant peak
                    yMaxIdx = 0;
                } else {
            
                    TIntSet pixelIdxs2 = null;
                    int width2, height2;
                    
                    yMaxIdx = peakIdxs[peakIdx];
                        
                    // factor to divide x or by:
                    // no sqrt(2) because using chessboard distances
                    sds.xyScaleFactor = (int) Math.round(vF.get(yMaxIdx));

                    pixelIdxs2 = new TIntHashSet(pixelIdxs.size());
                    width2 = width / sds.xyScaleFactor;
                    height2 = height / sds.xyScaleFactor;

                    PixelHelper ph = new PixelHelper();
                    int[] xy = new int[2];
                    TIntIterator iter = pixelIdxs.iterator();
                    while (iter.hasNext()) {
                        int pixIdx = iter.next();
                        ph.toPixelCoords(pixIdx, width, xy);
                        int pixIdx2 = ph.toPixelIndex(
                            xy[0] / sds.xyScaleFactor, 
                            xy[1] / sds.xyScaleFactor, width2);
                        pixelIdxs2.add(pixIdx2);
                    }
                                                
                    if (pixelIdxs2.size() < 12) {
                        if (peakIdx > 0) {
                            peakIdx--;
                            yMaxIdx = peakIdxs[peakIdx];
                        } // else //should not happen unless pixIdxs is small
                    } else {
                        
                        distTrans = dtr.applyMeijsterEtAl(pixelIdxs2, width2, height2);            
                        vF = new TIntArrayList();
                        cF = new TIntArrayList();
                        f.calcFrequency(distTrans, vF, cF, true);

                        yMaxIdx = MiscMath0.findYMaxIndex(cF);
                    }
                }   
            }
        }
        
        if (debug) {

            log.info("print dist trans for " + pixelIdxs.size() + " points "
                + "within width=" + width + " height=" + height);

            int[] minMax = MiscMath0.findMinMaxValues(distTrans);

            log.info("min and max =" + Arrays.toString(minMax));

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
                    sds.values[count2] = 1.f /(float)(v * v);
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
