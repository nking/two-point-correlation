package com.climbwithyourfeet.clustering;

import algorithms.YFastTrie;
import algorithms.imageProcessing.DistanceTransform;
import algorithms.misc.MinMaxPeakFinder;
import algorithms.misc.MiscMath0;
import algorithms.util.PixelHelper;
import gnu.trove.iterator.TIntFloatIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.TIntFloatMap;
import gnu.trove.map.hash.TIntFloatHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.util.Arrays;

/**
 *
 * @author nichole
 */
public class KDEStatsHelper {
        
    public TIntFloatMap calculateProbabilities(DensityHolder dh, 
        TIntSet pixIdxs, int width, int height) {
        
//TODO: need to replace the surface density calculation

        int[][] dt = calculateInvTransform(pixIdxs, width, height);
        
        TIntFloatMap pixSurfaceDens = calculatePixelSurfaceDensities(pixIdxs, width, dt);
            
        TIntFloatMap pixPs = extractProbabilities(dh, pixSurfaceDens);
        
        return pixPs;
    }

    private int[][] calculateInvTransform(TIntSet pixIdxs, int width, 
        int height) {
        
        TIntSet pixInv = new TIntHashSet();
        for (int i = 0; i < (width * height); ++i) {
            if (!pixIdxs.contains(i)) {
                pixInv.add(i);
            }
        }
        
        DistanceTransform distTrans = new DistanceTransform();
        int[][] dt = distTrans.applyMeijsterEtAl(pixInv, width, height);
        
        return dt;
    }

    private TIntFloatMap calculatePixelSurfaceDensities(TIntSet pixIdxs, int width, 
        int[][] dt) {
        
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        // key = pixIdx, value = density
        TIntFloatMap densMap = new TIntFloatHashMap();
        
        TIntIterator iter = pixIdxs.iterator();
        while (iter.hasNext()) {
        
            int pixIdx = iter.next();
        
            ph.toPixelCoords(pixIdx, width, xy);
            
            int d = dt[xy[0]][xy[1]];
            
            if (d > 0) {
                
                float density = (float) (1. / Math.sqrt(d));
            
                densMap.put(pixIdx, density);
            }
        }
        
        return densMap; 
    }

    private TIntFloatMap extractProbabilities(DensityHolder dh, 
        TIntFloatMap pixSurfaceDens) {
        
        //the variable whose frequency was calculated, surface density:
        float[] surfDens = dh.dens;
        
        //PMF or PDF:
        float[] prob = dh.normCount;
        
        TIntFloatIterator iter = pixSurfaceDens.iterator();
        
        // find closest surface density to point, then lookup prob with index.
        
        // in the trie, wanting to only store the surface densities greater 
        //   than a minimum probability if the function is not continuous
        float minProb = MiscMath0.findMin(prob);
        
        //turning the surface densities into integers so can use a YFastTrie
        //   to retrieve successors and predecessors.
        //   max surface density = 1.0. will use factor of 63 which is 6 bits
        
        float factor = 63;
        
        YFastTrie yft = new YFastTrie(6);
        
        if (minProb < (1./prob.length)) {
            
            float[] sortedDens = Arrays.copyOf(surfDens, surfDens.length);
            Arrays.sort(surfDens);
            MinMaxPeakFinder mmpf = new MinMaxPeakFinder();
            float minMean = mmpf.calculateMeanOfSmallest(sortedDens, 0.04f);
        
            for (int i = 0; i < surfDens.length; ++i) {
                float sd = surfDens[i];
                if (sd > minMean) {
                    int sdInt = (int)Math.round(sd * factor);
                    yft.add(sdInt);
                }
            }
        } else {
            for (int i = 0; i < surfDens.length; ++i) {
                float sd = surfDens[i];
                int sdInt = (int)Math.round(sd * factor);
                yft.add(sdInt);
            }         
        }
        
        
        TIntFloatMap pMap = new TIntFloatHashMap();
        
        for (int i = 0; i < pixSurfaceDens.size(); ++i) {
            iter.advance();
            int pixIdx = iter.key();
            float sd = iter.value();
            int sdInt = (int) Math.round(sd * factor);
        
            int v = yft.find(sdInt);
            if (v > -1) {
    
                float vF = (float)v/factor;
                int vIdx = Arrays.binarySearch(surfDens, vF);
                if (vIdx < 0) {
                    vIdx = -1*(vIdx + 1);
                }
                pMap.put(pixIdx, 1.f - prob[vIdx]);
                
                continue;
            }
            
            int pred = yft.predecessor(sdInt);
            
            int succ = yft.successor(sdInt);
            
            if (pred > -1 && succ < 0) {
                
                float predF = (float)pred/factor;
                int predIdx = Arrays.binarySearch(surfDens, predF);
                if (predIdx < 0) {
                    predIdx = -1*(predIdx + 1);
                }
                pMap.put(pixIdx, prob[predIdx]);
            
            } else if (pred < 0 && succ > -1) {
            
                float succF = (float)succ/factor;
                int succIdx = Arrays.binarySearch(surfDens, succF);
                if (succIdx < 0) {
                    succIdx = -1*(succIdx + 1);
                }
                pMap.put(pixIdx, prob[succIdx]);
            
            } else {
                
                // interpolate between them
                float predF = (float)pred/factor;
                
                float succF = (float)succ/factor;
            
                /*
                ratio = (predF-succF)/(pred-succ)
                
                sd - predF
                ---------- = ratio
                ?  - predP
                
                ? = ((sd - predF)/ratio) + predP
                */
                
                int predIdx = Arrays.binarySearch(surfDens, predF);
                if (predIdx < 0) {
                    predIdx = -1*(predIdx + 1);
                }
                float predProb = prob[predIdx];
                
                int succIdx = Arrays.binarySearch(surfDens, succF);
                if (succIdx < 0) {
                    succIdx = -1*(succIdx + 1);
                }
                float succProb = prob[succIdx];
                
                float ratio = (predF - succF)/(predProb - succProb);
                
                float p = ((sd - predF)/ratio) + predF;
                
                pMap.put(pixIdx, p);
            } 
        }
       
        return pMap;
    }
    
}
