package com.climbwithyourfeet.clustering;

import algorithms.misc.MinMaxPeakFinder;
import algorithms.misc.MiscMath0;
import algorithms.signalProcessing.ATrousWaveletTransform1D;
import algorithms.util.OneDFloatArray;
import algorithms.util.PolygonAndPointPlotter;
import gnu.trove.list.TFloatList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.map.TFloatFloatMap;
import gnu.trove.map.TFloatIntMap;
import gnu.trove.map.hash.TFloatFloatHashMap;
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
public class CriticalSurfDensKDE extends AbstractCriticalSurfDens {
    
    
    /**
     *
     */
    protected Logger log = Logger.getLogger(this.getClass().getName());
    
    
    /**
     *
     */
    public CriticalSurfDensKDE() {
    }
    
    /**
      uses kernel density smoothing via wavelet transforms to create an alternative
      to histograms for finding the first peak and hence the critical 
      surface density.
 
      @param values densities 
      @return 
     */
    public DensityHolder findCriticalDensity(float[] values) {
        
        if (values == null || values.length < 10) {
            throw new IllegalArgumentException("values length must be 10 or more");
        }
        
        
        // the discrete unique values in rr.unique are not necessarily evenly
        // spaced, though they are ordered by surface density.
        // rr.unique, rr.freq are the density curve version of a histogram
        // and the points are discrete.
        // The true density function, however, is continous from the
        // critical surface density (not yet found) up to the surface density of 1.0.
        // 
        // To smooth the curve (rr.unique, rr.freq), one could either
        //    apply a kernel on adjacent points in the arrays
        //    (which is a nearest neighbor approach)
        //    or one could resample (rr.unique, rr.freq) into a finer
        //    evenly spaced by surface densities and apply the atrous wavelet on 
        //    the evenly sampled rr.freq array to smooth it.
        //    The first approach, that of applying the kernel over adjacent array
        //    points is better for the task of finding the first peak
        //    as the critical density, so that is what is used here.
        //    
        // After the critical surface density is found, 
        // the smoothed curve (rr.unique, rr.freq) is a discrete sampling of
        // of a yet uncharacterized continuous PDF for this specific 
        // problem of pair-wise clustering.
        // To create the continous PDF, knowing that the critical surface density
        // represents a limit between 2 states, clustered and not clustered,
        // one could either create a PDF as a uniform distribution from the
        // critical point to the last point, 1.0, or
        // one could create a PDF as a positively sloped ramp between the
        // critical surface density point
        // and the point at surface density 1.0 and the same decreasing ramp
        // from critical point to 0.
        // The appeal of the later is that the surface densities below the
        // critical value have small non-negligible clustering probabilities,
        // and that is partially because the critical density has errors in
        // its determination for the background.
        
        // REVISING EVERYTHING BELOW -----
        Arrays.sort(values);
        
        ATrousWaveletTransform1D wave = new ATrousWaveletTransform1D();
        
        /*
        TODO: revisit this for extreme case such as:
            consider inefficiently stored data that has large
            x,y space between points within clusters and lower density
            than that in the regions outside of clusters.
            the current fixed values for some of the logic need revision.
        
        looking at which transform has the best representation of the first peak
        as the critical density.
        
        starting from highest index transformations to smallest:
            if there's more than one peak and its
                frequency is not 1.0
                then
                   if 1st peak sigma > 5 o5 10 ish
                       return that
                   else
                      calc a peak weighted average of the first
                         peaks until a peak has sigma > 5 or so
            else if there's one peak and it's freq is 1.0
            then retreat up the list until that is not true,
            and at that point take idx/2 as the next index
        
        also, when calculating weighted average, the sum continues to next
        frequency if the spacing is small (< 0.05)
        */
                    
        List<OneDFloatArray> outputTransformed = null;
        List<OneDFloatArray> outputCoeff = null;
        
        W r = new W();
        
        outputTransformed = new ArrayList<OneDFloatArray>();
        outputCoeff = new ArrayList<OneDFloatArray>();

        wave.calculateWithB3SplineScalingFunction(values, outputTransformed,
            outputCoeff);

        populate(r, outputTransformed.get(outputTransformed.size() - 1).a,
            outputTransformed.get(0).a);

        System.out.println(" r.indexes.length=" + r.indexes.length);                    
        
        String ts = Long.toString(System.currentTimeMillis());
        
        //System.out.println("transformed=" + Arrays.toString(smoothed));
        if (debug) {

            try {
                float[] x = new float[values.length];
                for (int i = 0; i < x.length; ++i) {
                    x[i] = i;
                }

                float yMax = MiscMath0.findMax(r.freq);

                /*
                PolygonAndPointPlotter plotter0 = new PolygonAndPointPlotter();
                plotter0.addPlot(0.f, x.length, 
                    0.f, 1.2f * MiscMath0.findMax(values),
                    x, values, x, values,
                    "input");
               
                plotter0.addPlot(0.f, x.length, 
                    0.f, 1.2f * MiscMath0.findMax(r.smoothed),
                    x, r.smoothed,
                    x, r.smoothed,
                    "transformed");
                
                System.out.println(plotter0.writeFile("transformed_" + ts));
                */

                // write the freq curve
                x = r.unique.toArray(new float[r.unique.size()]);
                PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0.f, 1.2f * x[x.length - 1],
                    0.f, 1.2f * yMax,
                    x, r.freq, x, r.freq,
                    "freq curve");

                System.out.println(plotter.writeFile("freq_" + ts));

            } catch (IOException e) {
                log.severe(e.getMessage());
            }
        }
       
        /*
        {//DEBUG
            
            W r0 = new W();

            populate(r0, outputTransformed.get(0).a);

            //System.out.println("transformed=" + Arrays.toString(smoothed));
            
            try {
                float yMax = MiscMath0.findMax(r0.freq);

                PolygonAndPointPlotter plotter0 = new PolygonAndPointPlotter();

                // write the freq curve
                float[] x = r0.unique.toArray(new float[r0.unique.size()]);
                plotter0.addPlot(0.f, 1.2f * x[x.length - 1],
                    0.f, 1.2f * yMax,
                    x, r0.freq, x, r0.freq,
                    "freq curve0");

                System.out.println(plotter0.writeFile("freq_0_" + ts));

            } catch (IOException e) {
                log.severe(e.getMessage());
            }
        }
        */
        
        // 1 = found single peak at freq=1, 2=jumped to half index
        int idxH = 0;
        
        int lastIdx = -1;
        
        for (int i0 = outputTransformed.size() - 1; i0 > -1; --i0) {
            
            lastIdx = i0;
            
            System.out.println("I0=" + lastIdx);
            
            populate(r, outputTransformed.get(i0).a,
                outputTransformed.get(0).a);
            
            if (idxH == 1) {
                assert(r.indexes.length > 0);
                if (r.indexes.length == 1) {
                    continue;
                }
                if (i0 < 2) {
                    continue;
                }
                // else, jump to index half of this
                // TODO: this may need improvements
                i0 = i0/2;
                idxH = 2;
                continue;
            }

            if (r.indexes.length == 0) {
                continue;
            } else if (r.indexes.length == 1) {
                // set a flag and continue
                idxH = 1;
                continue;
            }
            
            //TODO: sl may need adjustments:
            float sl = 9;// 10 percentish
            float sigma1 = r.freq[r.indexes[0]]/r.meanLow;
            
            if (sigma1 <= sl) {
                
                // weighted mean of peaks from 0 until a peak has s/n > sl 
                int idx = 0;
                float tot = 0;
                for (int ii = 0; ii < r.indexes.length; ++ii) {
                    int peakIdx = r.indexes[ii];
                    idx = ii;
                    tot += r.freq[peakIdx];
                    if ((r.freq[peakIdx]/r.meanLow) > sl) {
                        break;
                    }
                }
       
                // keep adding frequencies if the spacing to the next is close:
                if (idx > 0 && r.indexes.length > (idx + 1)) {
                    float diff = r.unique.get(r.indexes[idx + 1]) - 
                        r.unique.get(r.indexes[idx]);
                    if (diff < 0.05f) {
                        for (int ii = idx+1; ii < r.indexes.length; ++ii) {
                            if ((r.unique.get(r.indexes[ii]) 
                                - r.unique.get(r.indexes[ii - 1])) 
                                >= 0.05f) {
                                break;
                            }
                            idx = ii;
                            tot += r.freq[r.indexes[ii]];
                        }
                    }
                }
                
                float weightedMean = 0;
                for (int ii = 0; ii <= idx; ++ii) {
                    int peakIdx = r.indexes[ii];
                    float weight = r.freq[peakIdx]/tot;
                    weightedMean += weight * r.unique.get(peakIdx);
                }
                System.out.println("nPeaks=" + r.indexes.length);
                System.out.println("weighted critDens=" + weightedMean);
                doSparseEstimate(r.freq);
                
                KDEDensityHolder dh = (KDEDensityHolder) createDensityHolder(weightedMean,
                    r.unique, r.freq);
                dh.surfDensDiff = r.surfDensDiff;
                dh.approxH = (1 + lastIdx)*2;
                return dh;
            }
            System.out.println("nPeaks=" + r.indexes.length);
            doSparseEstimate(r.freq);
            KDEDensityHolder dh = (KDEDensityHolder) createDensityHolder(
                r.unique.get(r.indexes[0]), r.unique, r.freq);
            dh.surfDensDiff = r.surfDensDiff;
            dh.approxH = (1 + lastIdx)*2;
            return dh;
        }
        
        // for histogram, crit dens = 1.1 * density of first peak
        float peak = r.unique.get(r.indexes[0]);
        
        System.out.println("* critDens=" + peak);
        doSparseEstimate(r.freq);
        KDEDensityHolder dh = (KDEDensityHolder) createDensityHolder(peak, r.unique, r.freq);
        dh.surfDensDiff = r.surfDensDiff;
        dh.approxH = (1 + lastIdx)*2;
        return dh;
    }
    
    private void populate(W r, float[] values, float[] values0) {
        
        assert(values.length == values0.length);
        
        r.smoothed = values;
        r.freqMap = new TFloatIntHashMap();
        r.unique = new TFloatArrayList();

        // key = freq, value = added wavelet coefficients
        TFloatFloatMap densCoeffMap = new TFloatFloatHashMap();
        
        for (int i = 0; i < r.smoothed.length; ++i) {
            
            float v = r.smoothed[i];
            float coeff = values[i] - values0[i];
            coeff = Math.abs(coeff);
            
            int c = r.freqMap.get(v);
            // NOTE: trove map default returns 0 when key is not in map.
            if (c == 0) {
                r.unique.add(v);
            }
            c++;
            r.freqMap.put(v, c);
            
            //NOTE: trove default returns 0 when key is not in map
            float coeffSum = densCoeffMap.get(v) + coeff;
            densCoeffMap.put(v, coeffSum);
        }

        float freqMax = Float.NEGATIVE_INFINITY;
        r.freq = new float[r.unique.size()];
        r.surfDensDiff = new float[r.unique.size()];
        for (int i = 0; i < r.unique.size(); ++i) {
            r.freq[i] = r.freqMap.get(r.unique.get(i));
            r.surfDensDiff[i] = densCoeffMap.get(r.unique.get(i));
            if (r.freq[i] > freqMax) {
                freqMax = r.freq[i];
            }
        }
        
        // find maxima of values in freqMap
        r.sigma = 2.5f;
        MinMaxPeakFinder mmpf = new MinMaxPeakFinder();
        r.meanLow = mmpf.calculateMeanOfSmallest(r.freq, 0.03f);
        r.indexes = mmpf.findPeaks(r.freq, r.meanLow, 2.5f);
        System.out.println("thresh=" + r.meanLow * r.sigma);
        //System.out.println("freq=" + Arrays.toString(freq));
        //System.out.println("indexes=" + Arrays.toString(indexes));
        for (int idx : r.indexes) {
            System.out.println("  peak=" + r.freq[idx] + " " 
                + r.unique.get(idx));
        }
            
    }

    @Override
    protected DensityHolder constructDH() {
        return new KDEDensityHolder();
    }

    private static class W {
        public float[] smoothed = null;
        public TFloatIntMap freqMap = null;
        public TFloatList unique = null;
        public float[] freq = null;
        public float[] surfDensDiff = null;
        public int[] indexes = null;
        public float sigma = 2.5f;
        public float meanLow;
    }
}
