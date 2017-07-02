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

    private float calcSNRelTAdj(TIntList a, int idx, float minAvg) {
    
        float v = (float)a.get(idx);
        float adj0 = -1;
        float adj1 = -1;
        if ((idx > 0) && a.get(idx - 1) > minAvg) {
            adj0 = v/(float)a.get(idx - 1);
        }
        if (((idx + 1) < a.size()) && a.get(idx + 1) > minAvg) {
            adj1 = v/(float)a.get(idx + 1);
        }
        float adj = Math.max(adj0, adj1);
        
        return adj;
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
     * @return array of pairwise surface densities, that is, an array
     * that holds the inverse of pairwise separations.
     */
    public SurfaceDensityScaled extractSufaceDensity(TIntSet pixelIdxs,
        int width, int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException("pixelIdxs.size must be 12 or more");
        }
                
        DistanceTransform dtr = new DistanceTransform();
        int[][] distTrans = dtr.applyMeijsterEtAl(pixelIdxs, width, height);

        // calculate frequency of non-zero square distances 
        // to see if need to re-sample data
        
        Frequency f = new Frequency();
        //vF are the unique square distances from distTrans
        TIntList vF = new TIntArrayList();
        //vC are the number of occurrences of each unique square distance 
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
            
            if (peakIdxs == null || peakIdxs.length == 0) {
                // yMaxIdx is the index to use for scaling
                
            } else if (peakIdxs != null && peakIdxs.length > 0) {
               
                //float vp0 = vF.get(peakIdxs[0]);
                //float vpYm = vF.get(yMaxIdx);
                
                //float sn0 = vp0/minAvg;
                //float snYM = vpYm/minAvg;
                
                float sn0Adj = calcSNRelTAdj(cF, peakIdxs[0], minAvg);
                float snYMAdj = calcSNRelTAdj(cF, yMaxIdx, minAvg);
                
                if (sn0Adj > 0.9*snYMAdj) {
                    yMaxIdx = peakIdxs[0];
                }                
            }
            
            TIntSet pixelIdxs2 = null;
            int width2, height2;                    

            // the distance transform carries square distances, excepting
            // values 0, 1, 2
            double dist = vF.get(yMaxIdx);
            if (dist > 2) {
                dist = Math.sqrt(dist);
                if (Math.ceil(dist) > dist) {
                    dist = Math.ceil(dist);
                } else {
                    dist = Math.floor(dist);
                }
            }
            // factor to divide x, y or by:
            // no sqrt(2) because using chessboard distances
            sds.xyScaleFactor = (int) dist;

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
                
                yMaxIdx = 0;
                
            } else {

                distTrans = dtr.applyMeijsterEtAl(pixelIdxs2, width2, height2);            
                vF = new TIntArrayList();
                cF = new TIntArrayList();
                f.calcFrequency(distTrans, vF, cF, true);

                yMaxIdx = MiscMath0.findYMaxIndex(cF);
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
        
        // using a linear surface density, that is, the
        // distance between 2 points.
        sds.values = new float[w * h];
        int maxDist = Integer.MIN_VALUE;
        int count2 = 0;
        for (int i0 = 0; i0 < w; ++i0) {
            for (int j0 = 0; j0 < h; ++j0) {
                int v = distTrans[i0][j0];
                if (v > 0) {
                    float dist = v;
                    if (dist > 2) {
                        dist = (float)Math.sqrt(dist);
                    }
                    sds.values[count2] = 1.f/dist;
                    count2++;
                    if (dist > maxDist) {
                        maxDist = (int)dist;
                    }
                }
            }
        }
        
        sds.values = Arrays.copyOf(sds.values, count2);
        
        if (maxDist == 1) {
            sds.surfDensRes = (1.f/(float)maxDist);
        } else {
            sds.surfDensRes = (1.f/(float)(maxDist - 1.f)) - (1.f/(float)maxDist);
        }
        
        if (debug) {
            System.out.println("scaling factor=" + sds.xyScaleFactor);
        }
        
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
