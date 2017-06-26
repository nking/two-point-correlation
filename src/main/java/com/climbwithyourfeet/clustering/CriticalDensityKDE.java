package com.climbwithyourfeet.clustering;

import algorithms.misc.MinMaxPeakFinder;
import algorithms.misc.MiscMath0;
import algorithms.signalProcessing.ATrousWaveletTransform1D;
import algorithms.util.OneDFloatArray;
import algorithms.util.PolygonAndPointPlotter;
import gnu.trove.list.TFloatList;
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
        
        /*
        looking at which transform has the best representation of the first peak
        as the critical density.
        
        working from highest index transformations to smallest:
            if there's more than one peak and its
                frequency is not 1.0
                then
                   if 1st peak sigma > 5 o5 10?
                       return that
                   else
                      calc a peak weighted average of the first
                         peaks until a peak has sigma > 5 or so
            else if there's one peak and it's freq is 1.0
            then retreat up the list until that is not true,
            and at that point take ceil(idx/2) as the starting
            point to proceed forward, looking at sigma using same
            logic as above.
        
        caveat is the total number of peaks.
        nPeaks < 6 or 7
        */
        
        // check that the numbers have been transformed so that the largest
        //  index holds less than 6 or 7 or so peaks
                       
        List<OneDFloatArray> outputTransformed = null;
        List<OneDFloatArray> outputCoeff = null;
        
        W r = new W();
        
        int nIter = 0;
        int nIterMax = 3;
        /*
        NOTE: this first block is to handle multiple invocation of the
        wavelet transform if needed.
        It's not yet tested and might not be necessary since some of the
        close spacing is handled below.
        will revisit this soon.
        */
        do {
            float[] tmp;
            if (nIter == 0) {
                tmp = values;
            } else {
                tmp = outputTransformed.get(outputTransformed.size() - 1).a;
            }
            outputTransformed = new ArrayList<OneDFloatArray>();
            outputCoeff = new ArrayList<OneDFloatArray>();

            wave.calculateWithB3SplineScalingFunction(tmp, outputTransformed,
                outputCoeff);
            
            populate(r, outputTransformed.get(outputTransformed.size() - 1).a);
        
            System.out.println("nIter=" + nIter
                + " r.indexes.length=" + r.indexes.length);
            nIter++;
            
        } while (r.indexes.length > 9 && (nIter < nIterMax));
                
        // 1 = found single peak at freq=1, 2=jumped to half index
        int idxH = 0;
        
        for (int i0 = outputTransformed.size() - 1; i0 > -1; --i0) {
            
            System.out.println("I0=" + i0);
            
            populate(r, outputTransformed.get(i0).a);
            
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
                
                if (idx > 0 && r.indexes.length > (idx + 1)) {
                    float diff = 
                        r.unique.get(r.indexes[idx + 1]) - 
                        r.unique.get(r.indexes[idx]);
                    if (diff < 0.05f) {
                        // need a weighted sum, but the idx+1 peak frequency is close
                        //   to the idx peak frequency
                        //   so include the successive peaks until spacing increases
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
                System.out.println("weighted=" + weightedMean);
                return weightedMean;
            }
            System.out.println("nPeaks=" + r.indexes.length);
            return r.unique.get(r.indexes[0]);
        }
        
        //System.out.println("transformed=" + Arrays.toString(smoothed));
        
        /*if (debug) {
            
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
                    x, r.smoothed,
                    x, r.smoothed,
                    "transformed");

                System.out.println(plotter.writeFile("transformed_" + ts));

                // write the freq curve
                x = unique.toArray(new float[unique.size()]);
                plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0.f, 1.2f * x[x.length - 1], 
                    0.f, 1.2f * freqMax,
                    x, r.freq, x, r.freq,
                    "freq curve");

                System.out.println(plotter.writeFile("freq_" + ts));
                
            } catch (IOException e) {
                log.severe(e.getMessage());
            }
        }*/
        
        // for histogram, crit dens = 1.1 * density of first peak
        float peak = r.unique.get(r.indexes[0]);
        
        return peak;
    }

    private void populate(W r, float[] values) {
        
        r.smoothed = values;
        r.freqMap = new TFloatIntHashMap();
        r.unique = new TFloatArrayList();

        for (float v : r.smoothed) {
            int c = r.freqMap.get(v);
            // NOTE: trove map default returns 0 when key is not in map.
            if (c == 0) {
                r.unique.add(v);
            }
            c++;
            r.freqMap.put(v, c);
        }

        float freqMax = Float.NEGATIVE_INFINITY;
        r.freq = new float[r.unique.size()];
        for (int i = 0; i < r.unique.size(); ++i) {
            r.freq[i] = r.freqMap.get(r.unique.get(i));
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

    private static class W {
        public float[] smoothed = null;
        public TFloatIntMap freqMap = null;
        public TFloatList unique = null;
        public float[] freq = null;
        public int[] indexes = null;
        public float sigma = 2.5f;
        public float meanLow;
    }
}
