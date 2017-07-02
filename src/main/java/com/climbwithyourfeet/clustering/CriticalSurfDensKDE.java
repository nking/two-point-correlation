package com.climbwithyourfeet.clustering;

import algorithms.misc.MinMaxPeakFinder;
import algorithms.misc.MiscMath0;
import algorithms.signalProcessing.ATrousWaveletTransform1D;
import algorithms.signalProcessing.MedianTransform1D;
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
 
      @param sds scaled surface densities 
      @return 
     */
    public KDEDensityHolder findCriticalDensity_Atrous(
        SurfDensExtractor.SurfaceDensityScaled sds) {
        
        float[] values = sds.values;
        if (values == null || values.length < 10) {
            throw new IllegalArgumentException("values length must be 10 or more");
        }
        
        //TODO:
        //  paused here.  haven't finished scale changes for sds
        
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
        // and the point at surface density 1.0 and a decreasing ramp
        // from critical point to 0.
        // The appeal of the later is that the surface densities below the
        // critical value have small non-negligible clustering probabilities,
        // and that is partially because the critical density has errors in
        // its determination for the background.
        
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
        float res = 0.05f;
        // TODO: when the surface densities are truly canonicalized
        //can adjust this
        //if (sds.xyScaleFactor > 1) {
            // no 1/sqrt(2) factor because using chessboard
        //    res /= sds.xyScaleFactor;
        //}
        
        Arrays.sort(values);
        
        ATrousWaveletTransform1D wave = new ATrousWaveletTransform1D();
        
        List<OneDFloatArray> outputTransformed = 
            new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outputCoeff = 
            new ArrayList<OneDFloatArray>();

        wave.calculateWithB3SplineScalingFunction(values, outputTransformed,
            outputCoeff);

        W r = new W();

        populate(r, outputTransformed.get(outputTransformed.size() - 1).a,
            outputTransformed.get(0).a);

        String ts = Long.toString(System.currentTimeMillis());
        if (debug) {
            //plotSurfaceDensities(values, ts);
            //plotCurves(outputTransformed, outputCoeff, 
            //    0, outputCoeff.size() - 1, ts);
            plotCurve(r, ts);
        }
        
        // 1 = found single peak at freq=1, 2=jumped to half index
        int idxH = 0;
        
        int lastIdx = -1;
        
        for (int i0 = outputTransformed.size() - 1; i0 > -1; --i0) {
            
            lastIdx = i0;
            
            System.out.println("I0=" + lastIdx);
            
            populate(r, outputTransformed.get(i0).a, outputTransformed.get(0).a);
            
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
                    if (diff < res) {
                        for (int ii = idx+1; ii < r.indexes.length; ++ii) {
                            if ((r.unique.get(r.indexes[ii]) 
                                - r.unique.get(r.indexes[ii - 1])) 
                                >= res) {
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
                dh.approxH = (1 + lastIdx)*2;
                calcAndStorePDFPoints(r, dh);
                correctForScale(dh, sds);           
                return dh;
            }
            System.out.println("nPeaks=" + r.indexes.length);
            doSparseEstimate(r.freq);
            KDEDensityHolder dh = (KDEDensityHolder) createDensityHolder(
                r.unique.get(r.indexes[0]), r.unique, r.freq);
            dh.approxH = (1 + lastIdx)*2;
            calcAndStorePDFPoints(r, dh);
            correctForScale(dh, sds);
            return dh;
        }
        
        // for histogram, crit dens = 1.1 * density of first peak
        float peak = r.unique.get(r.indexes[0]);
        
        System.out.println("* critDens=" + peak);
        doSparseEstimate(r.freq);
        KDEDensityHolder dh = (KDEDensityHolder) createDensityHolder(peak, r.unique, r.freq);
        dh.approxH = (1 + lastIdx)*2;
        calcAndStorePDFPoints(r, dh);
        correctForScale(dh, sds);
        return dh;
    }
    
    public KDEDensityHolder findCriticalDensity(
        SurfDensExtractor.SurfaceDensityScaled sds) {
        
        float[] values = sds.values;
        if (values == null || values.length < 10) {
            throw new IllegalArgumentException("values length must be 10 or more");
        }
        
        //TODO:
        //  paused here.  haven't finished scale changes for sds
        
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
        // and the point at surface density 1.0 and a decreasing ramp
        // from critical point to 0.
        // The appeal of the later is that the surface densities below the
        // critical value have small non-negligible clustering probabilities,
        // and that is partially because the critical density has errors in
        // its determination for the background.
        
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
        float res = 0.05f;
        // TODO: when the surface densities are truly canonicalized
        //can adjust this
        //if (sds.xyScaleFactor > 1) {
            // no 1/sqrt(2) factor because using chessboard
        //    res /= sds.xyScaleFactor;
        //}
        
        Arrays.sort(values);
        
        MedianTransform1D wave = new MedianTransform1D();
        
        List<OneDFloatArray> outputTransformed = 
            new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outputCoeff = 
            new ArrayList<OneDFloatArray>();

        wave.multiscalePyramidalMedianTransform2(values, outputTransformed,
            outputCoeff);
        
        outputTransformed.remove(outputTransformed.size() - 1);
        
        assert(outputTransformed.size() == outputCoeff.size());

        W r = new W();
        
        String ts = Long.toString(System.currentTimeMillis());
        if (debug) {
            //plotSurfaceDensities(values, ts);
            plotCurves(outputTransformed, outputCoeff, 
                0, outputCoeff.size() - 1, ts);
        }
        
        int lastIdx = -1;
        
        // w/ median transform, the last is usually over-smoothed.
        // so starting about 1/3 from highest index
        //int end = Math.round(0.667f*outputCoeff.size());
        int end = Math.round(0.5f*outputCoeff.size());
        if (end < 1) {
            if (outputCoeff.size() > 1) {
                end = outputCoeff.size() - 2;
            } else {
                end = outputCoeff.size() - 1;
            }
        }
                
        for (int i0 = end; i0 > -1; --i0) {
                        
            System.out.println("I0=" + lastIdx);
            
            // returns false is no maxima were found
            boolean acc = populate2(r, outputTransformed.get(i0).a, 
                outputCoeff.get(i0).a);
            
            if (!acc) {
                continue;
            }
            
            lastIdx = i0;
            
            if (debug) {
                plotCurve(r, "_" + ts + "_" + i0);
            }
                  
            if (r.indexes.length == 1 && 
                (r.indexes[r.indexes.length - 1] == r.unique.size())) {
                continue;
            }
            
       //TODO: sl may need adjustments:
            float sl = r.sigma;//9;
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
                    if (diff < res) {
                        for (int ii = idx+1; ii < r.indexes.length; ++ii) {
                            if ((r.unique.get(r.indexes[ii]) 
                                - r.unique.get(r.indexes[ii - 1])) 
                                >= res) {
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
                
                KDEDensityHolder dh = (KDEDensityHolder) createDensityHolder(
                    weightedMean, r.unique, r.freq);
                dh.approxH = (lastIdx > -1)
                    ? (outputTransformed.get(0).a.length
                    / outputTransformed.get(lastIdx).a.length) : 1;
                calcAndStorePDFPoints(r, dh);
                correctForScale(dh, sds); 
                System.out.println("chose index=" + lastIdx + 
                    " adjusted critDens=" + dh.critDens + " scl=" + sds.xyScaleFactor);
                return dh;
            }
            System.out.println("nPeaks=" + r.indexes.length);
            doSparseEstimate(r.freq);
            System.out.println("critDens=" + r.unique.get(r.indexes[0]));
            KDEDensityHolder dh = (KDEDensityHolder) createDensityHolder(
                r.unique.get(r.indexes[0]), r.unique, r.freq);
            dh.approxH = (lastIdx > -1)
                ? (outputTransformed.get(0).a.length
                / outputTransformed.get(lastIdx).a.length) : 1;
            calcAndStorePDFPoints(r, dh);
            correctForScale(dh, sds);
            System.out.println("chose index=" + lastIdx + 
                " adjusted critDens=" + dh.critDens + " scl=" + sds.xyScaleFactor);
            return dh;
        }
        
        // for histogram, crit dens = 1.1 * density of first peak
        float peak = r.unique.get(r.indexes[0]);
        
        System.out.println("* critDens=" + peak);
        doSparseEstimate(r.freq);
        
        KDEDensityHolder dh = (KDEDensityHolder) createDensityHolder(
            peak, r.unique, r.freq);
        dh.approxH = (lastIdx > -1)
            ? (outputTransformed.get(0).a.length
            / outputTransformed.get(lastIdx).a.length) : 1;
        calcAndStorePDFPoints(r, dh);
        correctForScale(dh, sds);
        System.out.println("chose index=" + lastIdx + 
            " adjusted critDens=" + dh.critDens + " scl=" + sds.xyScaleFactor);
        return dh;
    }
    
    private void populate(W r, float[] values, float[] values0) {
        
        //TODO: this is not entirely correct yet
        //   due to numerical resolution.
        //   still working on canonicalizing the
        //   surface densities without losing
        //   structural details.
        //   also considering a pyramidal transform
        
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
        System.out.println("indexes=" + Arrays.toString(r.indexes));
        for (int idx : r.indexes) {
            System.out.println("  peak=" + r.freq[idx] + " " 
                + r.unique.get(idx));
        }
            
    }

    private boolean populate2(W r, float[] values, float[] valuesDiff) {
        
        assert(values.length == valuesDiff.length);
        
        TFloatIntMap freqMap = new TFloatIntHashMap();
        TFloatList unique = new TFloatArrayList();
        
        // key = freq, value = added wavelet coefficients
        TFloatFloatMap densCoeffMap = new TFloatFloatHashMap();
        
        for (int i = 0; i < values.length; ++i) {
            
            float v = values[i];
            float coeff = valuesDiff[i];
            coeff = Math.abs(coeff);
            
            int c = freqMap.get(v);
            // NOTE: trove map default returns 0 when key is not in map.
            if (c == 0) {
                unique.add(v);
            }
            c++;
            freqMap.put(v, c);
            
            //NOTE: trove default returns 0 when key is not in map
            float coeffSum = densCoeffMap.get(v) + coeff;
            densCoeffMap.put(v, coeffSum);
        }

        float freqMax = Float.NEGATIVE_INFINITY;
        float[] freq = new float[unique.size()];
        float[] surfDensDiff = new float[unique.size()];
        for (int i = 0; i < unique.size(); ++i) {
            freq[i] = freqMap.get(unique.get(i));
            surfDensDiff[i] = densCoeffMap.get(unique.get(i));
            if (freq[i] > freqMax) {
                freqMax = freq[i];
            }
        }
        
        // find maxima of values in freqMap
        float sigma = 2.5f;
        MinMaxPeakFinder mmpf = new MinMaxPeakFinder();
        float meanLow = mmpf.calculateMeanOfSmallest(freq, 0.03f);
        int[] indexes = mmpf.findPeaks(freq, meanLow, 2.5f);
        System.out.println("thresh=" + meanLow * sigma);
        //System.out.println("freq=" + Arrays.toString(freq));
        System.out.println("indexes=" + Arrays.toString(indexes));
        if (indexes == null || indexes.length == 0) {
            return false;
        }
        if (Math.abs(unique.get(unique.size() - 1) - 1.0) >= 0.001) {
            return false;
        }
        for (int idx : indexes) {
            System.out.println("  peak=" + freq[idx] + " " + unique.get(idx));
        }
        
        r.smoothed = values;
        r.freqMap = freqMap;
        r.unique = unique;
        r.freq = freq;
        r.surfDensDiff = surfDensDiff;
        r.sigma = sigma;
        r.meanLow = meanLow;
        r.indexes = indexes;
        
        return true;
    }
    
    @Override
    protected DensityHolder constructDH() {
        return new KDEDensityHolder();
    }
    
    /*    
    private void populate0(W0 r, float[] values) {
                
        r.freqMap = new TFloatIntHashMap();
        r.unique = new TFloatArrayList();

        for (int i = 0; i < values.length; ++i) {
            
            float v = values[i];
            
            int c = r.freqMap.get(v);
            // NOTE: trove map default returns 0 when key is not in map.
            if (c == 0) {
                r.unique.add(v);
            }
            c++;
            r.freqMap.put(v, c);
        }
        
        r.unique.sort();
        
        r.freq = new float[r.unique.size()];
        for (int i = 0; i < r.unique.size(); ++i) {
            float v = r.unique.get(i);
            r.freq[i] = r.freqMap.get(v);
        }
    }

     public DensityHolder calculatePDF(float[] values) {
        
        W0 rr = new W0();
        populate0(rr, values);
        
        ATrousWaveletTransform1D wave = new ATrousWaveletTransform1D();
        
        List<OneDFloatArray> outputTransformed = 
            new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outputCoeff = 
            new ArrayList<OneDFloatArray>();

        wave.calculateWithB3SplineScalingFunction(rr.freq, outputTransformed,
            outputCoeff);

        String ts = Long.toString(System.currentTimeMillis());
        if (debug) {
            plotCurves(rr, outputTransformed, outputCoeff, 
                0, outputCoeff.size() - 1, ts);
        }
        
    }
   
    private void plotCurves(W0 rr, List<OneDFloatArray> transformed,
        List<OneDFloatArray> coeff, int i0, int i1, String ts) {

        TFloatList surfDens = rr.unique;
        
        int n = surfDens.size();
        
        try {
            PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();
            
            float[] x = surfDens.toArray(new float[n]);
            float xMin = 0;
            float xMax = 1.1f;

            for (int i = i0; i <= i1; ++i) {
                float[] y = transformed.get(i).a;
                float yMax = MiscMath0.findMax(y);
                
                plotter.addPlot(xMin, xMax,
                    0.f, 1.2f * yMax,
                    x, y, x, y,
                    "transformed");
            }
            
            for (int i = i0; i <= i1; ++i) {
                float[] y = coeff.get(i).a;
                float yMin = MiscMath0.findMin(y);
                float yMax = MiscMath0.findMax(y);
                
                plotter.addPlot(xMin, xMax,
                    yMin, 1.2f * yMax,
                    x, y, x, y,
                    "coeff");
            }

            System.out.println(plotter.writeFile("transformed_" + ts));

        } catch (IOException e) {
            log.severe(e.getMessage());
        }
    }
    
    private static class W0 {
        public TFloatIntMap freqMap = null;
        public TFloatList unique = null;
        public float[] freq = null;
    }
    */
   
    private void plotCurves(List<OneDFloatArray> transformed,
        List<OneDFloatArray> coeff, int i0, int i1, String ts) {
                
        try {
            
            for (int i = i0; i <= i1; ++i) {
                
                int n = transformed.get(i).a.length;
                int n2 = coeff.get(i).a.length;
                assert(n == n2);
                float[] x = new float[n];
                for (int ii = 0; ii < x.length; ++ii) {
                    x[ii] = ii;
                }
                float xMin = 0;
                float xMax = x.length;
                
                float[] y = transformed.get(i).a;
                float yMax = MiscMath0.findMax(y);
            
                PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();
            
                plotter.addPlot(xMin, xMax,
                    0.f, 1.2f * yMax,
                    x, y, x, y,
                    "transformed");
                
                y = coeff.get(i).a;
                float yMin = MiscMath0.findMin(y);
                yMax = 1.2f*MiscMath0.findMax(y);
                
                plotter.addPlot(xMin, xMax,
                    yMin, yMax,
                    x, y, x, y,
                    "coeff");
            
                System.out.println(
                    plotter.writeFile("transformed_" + ts + "_" + i));
            }

        } catch (IOException e) {
            log.severe(e.getMessage());
        }
    }
    
    private void plotCurve(W r, String ts) {
                
        try {
            PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();
            
            int n = r.unique.size();
            float[] x = r.unique.toArray(new float[n]);
            float xMin = 0;
            float xMax = 1.2f*MiscMath0.findMax(x);

            float[] y = r.freq;
            float yMax = MiscMath0.findMax(y);

            plotter.addPlot(xMin, xMax,
                0.f, 1.2f * yMax,
                x, y, x, y,
                "density curve");

            System.out.println(plotter.writeFile("dens_curve_" + ts));

        } catch (IOException e) {
            log.severe(e.getMessage());
        }
    }
    
    private void plotSurfaceDensities(float[] values, String ts) {

        int n = values.length;
        
        try {
            
            float[] x = new float[values.length];
            for (int i = 0; i < x.length; ++i) {
                x[i] = i;
            }
                
            PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();
            
            plotter.addPlot(0.f, x.length,
                0.f, 1.2f * MiscMath0.findMax(values),
                x, values, x, values,
                "input");

            System.out.println(plotter.writeFile("sd_data_" + ts));

        } catch (IOException e) {
            log.severe(e.getMessage());
        }            
    }
    
    private void calcAndStorePDFPoints(W r, KDEDensityHolder dh) {
//TODO: fix, cannot assume last freq is 1?      
        int firstNZIdx = 0;
        for (int i = 0; i < r.unique.size(); ++i) {
            float dens = r.unique.get(i);
            if (dens >= dh.critDens) {
                break;
            }
            firstNZIdx = i;
            if (dens >= r.meanLow) {
                break;
            }
        }
        int critSurfDensIdx = 0;
        for (int i = 0; i < r.unique.size(); ++i) {
            float dens = r.unique.get(i);
            if (dens == dh.critDens) {
                critSurfDensIdx = i;
                break;
            } else if (dens > dh.critDens) {
                break;
            }
            critSurfDensIdx = i;
        }
        
        int lastIdx = r.unique.size() - 1;
        
        assert(Math.abs(r.unique.get(lastIdx) - 1.0) < 0.001);
       
        dh.threeSDs = new float[]{
            r.unique.get(firstNZIdx),
            r.unique.get(critSurfDensIdx),
            r.unique.get(lastIdx)
        };
        
        float[] threeSDCounts = new float[]{
            r.freq[firstNZIdx],
            r.freq[critSurfDensIdx],
            r.freq[lastIdx]
        };
        dh.setAndNormalizeCounts(threeSDCounts);
        
        // calculate the errors for the 3 points
        float[] errors = new float[] {
            calcError(r, firstNZIdx, dh.threeSDCounts[0], dh.approxH),
            calcError(r, critSurfDensIdx, dh.threeSDCounts[1], dh.approxH),
            calcError(r, lastIdx, dh.threeSDCounts[2], dh.approxH)
        };
        dh.setTheThreeErrors(errors);
    }
    
    private float calcError(W r, int idx, float prob, float approxH) {

        //TODO: need to revisit this and compare to other methods of determining
        //    point-wise error
        
        //sigma^2  =  xError^2*(Y^2)  +  yError^2*(X^2)
        
        float xerrsq = r.surfDensDiff[idx];
        xerrsq *= xerrsq;

        float count = prob;
        float surfDens = r.unique.get(idx);

        float t1 = xerrsq * (prob * prob);
        float t2 = count * surfDens * surfDens;
        t2 /= (approxH * approxH);

        float pErr = (float)Math.sqrt(t1 + t2);

        /*
        consider MISE:
            integral of E((p_smothed(x) - p(x)^2)dx
        
            E[a] = integral_-inf_to_inf of (a * f(a) * a)
                where f is the PDF
        */
        
        /*
        System.out.println(
            " sd=" + surfDens
            + " p=" + prob 
            + " pErr=" + pErr 
            + " count=" + count
            + " h=" + approxH
            + " sqrt(t1)=" + Math.sqrt(t1) +
            " sqrt(t2_=" + Math.sqrt(t2));
        */
        return pErr;
    }

    private void correctForScale(KDEDensityHolder dh, 
        SurfDensExtractor.SurfaceDensityScaled sds) {

       float f = sds.xyScaleFactor;
        
        /*
        dh.approxH;
        dh.critDens;
        dh.dens;
        dh.threeSDs;
        dh.threeSDErrors;
        
        distances get multiplied by xyScaleFactor
        
        sds get multiplied by 1/(xyScaleFactor)
        
        x,y get multiplied by xyScaleFactor
        */
        dh.approxH *= f;
        dh.critDens /= f;
        for (int i = 0; i < dh.dens.length; ++i) {
            dh.dens[i] /= f;
        }
        dh.threeSDs[0] /= f;
        dh.threeSDs[1] /= f;
        
        // TODO: this could be improved:  
        //   the 2nd component of error is larger and is a multiple of
        //   the surface density, so making a factor change here
        dh.threeSDErrors[0] /= f;
        dh.threeSDErrors[1] /= f;
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
