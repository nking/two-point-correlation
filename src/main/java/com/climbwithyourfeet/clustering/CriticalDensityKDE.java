package com.climbwithyourfeet.clustering;

import algorithms.misc.MinMaxPeakFinder;
import algorithms.misc.MiscMath0;
import algorithms.signalProcessing.ATrousWaveletTransform1D;
import algorithms.util.OneDFloatArray;
import algorithms.util.PolygonAndPointPlotter;
import gnu.trove.list.TFloatList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.map.TFloatIntMap;
import gnu.trove.map.hash.TFloatIntHashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * uses kernel density smoothing via wavelet transforms to create an alternative
 * to histograms for finding the first peak and hence the critical density.
 * 
 * @author nichole
 */
public class CriticalDensityKDE implements ICriticalDensity {
    
    private boolean debug = false;
    
    /**
     *
     */
    protected Logger log = Logger.getLogger(this.getClass().getName());
    
    /**
     *
     */
    public CriticalDensityKDE() {
    }
    
    /**
     *
     */
    public void setToDebug() {
        debug = true;
    }
    
    /**
     * uses kernel density smoothing via wavelet transforms to create an alternative
      to histograms for finding the first peak and hence the critical density.
 
     * @param values densities 
     * @return 
     */
    public float findCriticalDensity(float[] values) {
        
        if (values == null || values.length < 10) {
            throw new IllegalArgumentException("values length must be 10 or more");
        }
        
        Arrays.sort(values);
        
        ATrousWaveletTransform1D wave = new ATrousWaveletTransform1D();
                       
        List<OneDFloatArray> outputTransformed = new 
            ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outputCoeff = new 
            ArrayList<OneDFloatArray>();
        
        wave.calculateWithB3SplineScalingFunction(values, outputTransformed, 
            outputCoeff);
        
        // TODO: determine which transform is the best to use.
        // determine whether should apply wavelet transform once more.
        /*
        one criteria could be that when the freqMap below only contains one 
           unique value count that is 50 to 90 percent or something above the adjacent
           and that is at unique value=1,
           another round of wavelet transformation is needed.
        */
        
        // calculate frequency of transformed values.
        // NOTE can follow this block with further "binning" by a small amount
        //  by assigning adjcent within tolerance to the local peaks.
        float[] smoothed = outputTransformed.get(outputTransformed.size() - 1).a;
        TFloatIntMap freqMap = new TFloatIntHashMap();
        TFloatList unique = new TFloatArrayList();
        for (float v : smoothed) {
            int c = freqMap.get(v);
            // NOTE: trove map default returns 0 when key is not in map.
            if (c == 0) {
                unique.add(v);
            }
            c++;
            freqMap.put(v, c);
        }
        
        float freqMax = Float.NEGATIVE_INFINITY;
        float[] freq = new float[unique.size()];
        for (int i = 0; i < unique.size(); ++i) {
            freq[i] = freqMap.get(unique.get(i));
            if (freq[i] > freqMax) {
                freqMax = freq[i];
            }
        }
        
        // find maxima of values in freqMap
        MinMaxPeakFinder mmpf = new MinMaxPeakFinder();
        int[] indexes = mmpf.findPeaks(freq);
        //System.out.println("freq=" + Arrays.toString(freq));
        //System.out.println("indexes=" + Arrays.toString(indexes));
        //for (int idx : indexes) {
        //    System.out.println("peak=" + freq[idx] + " " + unique.get(idx));
        //}
        
        //System.out.println("transformed=" + Arrays.toString(smoothed));
        
        if (debug) {
            
            String ts = Long.toString(System.currentTimeMillis());
            ts = ts.substring(ts.length() - 9, ts.length() - 1);
            
            try {
                float[] x = new float[values.length];
                for (int i = 0; i < x.length; ++i) {
                    x[i] = i;
                }

                float yMax = MiscMath0.findMax(values);

                PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0.f, x.length, 0.f, 1.2f * yMax,
                    x, values, x, values,
                    "input");
                plotter.addPlot(0.f, x.length, 0.f, 1.2f * yMax,
                    x, smoothed,
                    x, smoothed,
                    "transformed");

                System.out.println(plotter.writeFile("transformed_" + ts));

                // write the freq curve
                x = unique.toArray(new float[unique.size()]);
                plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0.f, 1.2f * x[x.length - 1], 
                    0.f, 1.2f * freqMax,
                    x, freq, x, freq,
                    "freq curve");

                System.out.println(plotter.writeFile("freq_" + ts));
                
            } catch (IOException e) {
                log.severe(e.getMessage());
            }
        }
        
        // for histogram, crit dens = 1.1 * density of first peak
        float peak = unique.get(indexes[0]);
        
        return peak;
    }

}
