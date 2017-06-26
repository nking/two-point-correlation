package com.climbwithyourfeet.clustering;

import algorithms.misc.*;
import algorithms.util.Errors;
import java.io.IOException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * uses histograms to find the first peak that determines the critical density.
 * 
 * @author nichole
 */
public class CriticalDensityHistogram extends AbstractCriticalDensity {
    
    private boolean debug = false;
    
    /**
     *
     */
    protected Logger log = Logger.getLogger(this.getClass().getName());
    
    /**
     *
     */
    public CriticalDensityHistogram() {
    }
    
    /**
     *
     */
    public void setToDebug() {
        debug = true;
    }
    
    /**
     * using histograms of inverse sqrt of distance transform, find the center 
     * of the first peak and return it, else return 0
     * (0 as a critical density should result in an infinite critical separation
     * so no clusters).
     * @param values densities
     * @return 
     */
    public float findCriticalDensity(float[] values) {
        
        if (values == null || values.length < 10) {
            throw new IllegalArgumentException("values length must be 10 or more");
        }
        
        /*
        the goal of this method is to form a histogram comparable to the
        GeneralizedExtremeValue function to find the critical density
        (which is near the peak).
        
        TODO: This method needs improvements, especially for small numbers.
        */
        
        float[] vErrors = Errors.populateYErrorsBySqrt(values);

        int nb = 40;
        if (nb > values.length) {
            nb = values.length/4;
            if (nb == 0) {
                nb = 1;
            }
        }
        
        float xl = MiscMath0.findMax(values);
               
        HistogramHolder hist = Histogram.createSimpleHistogram(
            0, xl, nb, values, vErrors);
        
        int len = hist.getXHist().length;
        
        int yFirstPeakIdx = Histogram.findFirstPeakIndex(hist);
                
        int yMaxIdx = MiscMath0.findYMaxIndex(hist.getYHist());
        
        float xMax = 0;
        
        boolean p1Corr = false;
        
        while (yMaxIdx < 2 && values.length > 10) {
            values = Arrays.copyOf(values, values.length);
            Arrays.sort(values);
            // trim away t half bin
            int idx = Arrays.binarySearch(values, hist.getXHist()[len/2]);
            if (idx < 0) {
                idx = -1*(idx + 1);
            }
            values = Arrays.copyOf(values, idx);
            vErrors = Errors.populateYErrorsBySqrt(values);
            
            xl = values[values.length - 1];
            
            float binSize = hist.getXHist()[0]/2.f;
            
            nb = Math.round(xl/binSize);
            if (nb < 10) {
                nb = 10;
            }
            
            hist = Histogram.createSimpleHistogram(
               0, xl, nb, values, vErrors);
       
            len = hist.getXHist().length;
        
            yFirstPeakIdx = Histogram.findFirstPeakIndex(hist);
                
            yMaxIdx = MiscMath0.findYMaxIndex(hist.getYHist());
                  
            xMax = 2.0f * hist.getXHist()[yMaxIdx];
           
            p1Corr = true;
        }

        if (debug) {
            
            String outFileSuffix = "_cluster_";
            try {
                hist.plotHistogram("clstr", outFileSuffix);
            } catch (IOException ex) {
                Logger.getLogger(CriticalDensityHistogram.class.getName()).log(Level.SEVERE, null, ex);
            }
        
            System.out.println("1stPeakIdx=" + yFirstPeakIdx +
                 " yMaxIdx=" + yMaxIdx + " out of " + len
                + " x[01stPeak]=" + hist.getXHist()[yFirstPeakIdx] +
                " x[yMaxIdx]=" + hist.getXHist()[yMaxIdx] + 
                " p1Corr=" + p1Corr);
        }
        
        //System.out.println("y1=" + yFirstPeakIdx + " ymx=" +
        //    yMaxIdx + " len=" + len);
        
        if (!p1Corr && yMaxIdx > yFirstPeakIdx && (yMaxIdx > (len/2))) {
            
            nb = 10;
            hist = Histogram.createSimpleHistogram(
                0, xl, nb, values, vErrors);
            
            if (hist == null) {
                throw new IllegalStateException("error in algorithm");
            }

            if (debug) {
                String outFileSuffix = "_cluster_2_";
                try {
                    hist.plotHistogram("clstr", outFileSuffix);
                } catch (IOException ex) {
                    Logger.getLogger(CriticalDensityHistogram.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        
            yFirstPeakIdx = Histogram.findFirstPeakIndex(hist);
            
            yMaxIdx = Histogram.findFirstMinimaFollowingPeak(hist, yFirstPeakIdx);

            xMax = 1.1f * hist.getXHist()[yMaxIdx];
            
        } else if (!p1Corr) {
                    
            // calculate the y quartiles above zero
            float[] quartiles = calcXQuartilesAboveZero(hist);

            if (debug) {
                System.out.println("quartiles=" + Arrays.toString(quartiles));
            }
            
            xl = Math.max(quartiles[0], quartiles[1]);

            nb /= 2;
            if (nb == 0) {
                nb = 1;
            }

            // make another histogram w/ x range being the 2nd quartile
            hist = Histogram.createSimpleHistogram(
                0, xl, nb, values, vErrors);
        
            if (debug) {
                String outFileSuffix = "_cluster_2_";
                try {
                    hist.plotHistogram("clstr", outFileSuffix);
                } catch (IOException ex) {
                    Logger.getLogger(CriticalDensityHistogram.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
            
            yMaxIdx = MiscMath0.findYMaxIndex(hist.getYHist());
        
            xMax = 1.1f * hist.getXHist()[yMaxIdx];
        }
        
        if (yMaxIdx == -1) {
            return 0;
        }
        
        return xMax;
    }

    private float[] calcXQuartilesAboveZero(HistogramHolder hist) {

        // making a cumulative array of y.
        
        int n = hist.getXHist().length;
        double[] ys = new double[n];
        int count = 0;
        ys[0] = hist.getYHist()[0];
        for (int i = 1; i < n; ++i) {
            ys[i] = hist.getYHist()[i] + ys[i - 1];
        }
        
        double yTot = ys[n - 1];
        
        // where y cumulative is yTot/2
        int medianIdx = Arrays.binarySearch(ys, yTot/2);
        if (medianIdx < 0) {
            // idx = -*idx2 - 1
            medianIdx = -1*(medianIdx + 1);
        }
        if (medianIdx > (n - 1)) {
            medianIdx = n - 1;
        }
        
        // where y curmulative is yTot/4
        int q12Idx = Arrays.binarySearch(ys, yTot/2);
        if (q12Idx < 0) {
            // idx = -*idx2 - 1
            q12Idx = -1*(q12Idx + 1);
        }
        if (q12Idx > (n - 1)) {
            q12Idx = n - 1;
        }
        
        // where y curmulative is 3*yTot/4
        int q34Idx = Arrays.binarySearch(ys, 3.*yTot/4.);
        if (q34Idx < 0) {
            // idx = -*idx2 - 1
            q34Idx = -1*(q34Idx + 1);
        }
        if (q34Idx > (n - 1)) {
            q34Idx = n - 1;
        }
        
        float[] xs = hist.getXHist();        
        
        return new float[]{xs[q12Idx], xs[medianIdx], xs[q34Idx], xs[n - 1]};
    }

}
