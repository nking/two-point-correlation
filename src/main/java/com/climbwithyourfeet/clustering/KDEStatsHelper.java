package com.climbwithyourfeet.clustering;

import algorithms.YFastTrie;
import algorithms.misc.MinMaxPeakFinder;
import algorithms.misc.MiscMath0;
import algorithms.search.NearestNeighbor2D;
import algorithms.util.OneDFloatArray;
import algorithms.util.PairInt;
import algorithms.util.PixelHelper;
import gnu.trove.iterator.TIntFloatIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.TIntFloatMap;
import gnu.trove.map.hash.TIntFloatHashMap;
import gnu.trove.set.TIntSet;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 *
 * @author nichole
 */
public class KDEStatsHelper {
        
    public void calculateProbabilities(KDEDensityHolder dh, TIntSet pixIdxs, 
        int width, int height,
        TIntFloatMap outputPointProbMap,
        TIntFloatMap outputPointProbErrMap) {
                
        TIntFloatMap pixSurfaceDens = calculatePixelSurfaceDensities(pixIdxs, 
            width, height);
                            
        extractProbabilities(dh, pixSurfaceDens, outputPointProbMap, 
            outputPointProbErrMap);        
    }

    private TIntFloatMap calculatePixelSurfaceDensities(TIntSet pixIdxs, 
        int width, int height) {
        
        /*
        the pixel surface density could be estimated in many ways.
        since the surface density estimate is pairwise, can make a pairwise
        estimate for each pixIdxs using its nearest 2D xy neighbor.        
        */
        
        NearestNeighbor2D nn = new NearestNeighbor2D(pixIdxs, width, height);
        
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        // key = pixIdx, value = density
        TIntFloatMap densMap = new TIntFloatHashMap();
        
        TIntIterator iter = pixIdxs.iterator();
        while (iter.hasNext()) {
        
            int pixIdx = iter.next();
        
            ph.toPixelCoords(pixIdx, width, xy);
            
            Set<PairInt> nearest = nn.findClosestNotEqual(xy[0], xy[1]);
            
            if (nearest != null && nearest.size() > 0) {
                
                PairInt p1 = nearest.iterator().next();
                
                //chess board distance d
                int d = Math.abs(p1.getX() - xy[0]) + Math.abs(p1.getY() - xy[1]);
                
                float density = (float)(1. / Math.sqrt(d));
                            
                densMap.put(pixIdx, density);
            }
        }
        
        return densMap; 
    }

    private void extractProbabilities(KDEDensityHolder dh, 
        TIntFloatMap pixSurfaceDens,
        TIntFloatMap outputProbMap,
        TIntFloatMap outputProbErrMap) {
            
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
                outputProbMap.put(pixIdx, prob[vIdx]);
                
                float probErr = calcError(dh, vIdx, prob[vIdx]);
                
                outputProbErrMap.put(pixIdx, probErr);
                
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
                outputProbMap.put(pixIdx, prob[predIdx]);
            
                float probErr = calcError(dh, predIdx, prob[predIdx]);
                
                outputProbErrMap.put(pixIdx, probErr);
                
            } else if (pred < 0 && succ > -1) {
            
                float succF = (float)succ/factor;
                int succIdx = Arrays.binarySearch(surfDens, succF);
                if (succIdx < 0) {
                    succIdx = -1*(succIdx + 1);
                }
                outputProbMap.put(pixIdx, prob[succIdx]);
            
                float probErr = calcError(dh, succIdx, prob[succIdx]);
                
                outputProbErrMap.put(pixIdx, probErr);
                
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
                
                outputProbMap.put(pixIdx, p);
            
                float probErrP = calcError(dh, predIdx, prob[predIdx]);
                float probErrS = calcError(dh, succIdx, prob[succIdx]);
                
                /*
                pErr - probErrS    probErrP - probErrS
                ---------------  = -------------------
                p    - probS       probP - probS
                
                pErr = probErrS + (probErrP - probErrS)*(p - probS)/(probP - probS
                */
                
                float pErr = probErrS + 
                    ((probErrP - probErrS)*(p - succProb)/(predProb - succProb));
                
                outputProbErrMap.put(pixIdx, pErr);
                
            } 
        }       
    }        
        
    private float calcError(KDEDensityHolder dh, int idx, float prob) {

        //TODO: need to revisit this and compare to other methods of determining
        //    point-wise error
        
        //sigma^2  =  xError^2*(Y^2)  +  yError^2*(X^2)
        
        float xerrsq = dh.surfDensDiff[idx];
        xerrsq *= xerrsq;

        float count = dh.normCount[idx];
        float surfDens = dh.dens[idx];

        float t1 = xerrsq * (prob * prob);
        float t2 = count * surfDens * surfDens;
        t2 /= (dh.approxH * dh.approxH);

        float pErr = (float)Math.sqrt(t1 + t2);

        //System.out.println("p=" + prob 
        //    + " h=" + dh.approxH
        //    + " sqrt(t1)=" + Math.sqrt(t1) +
        //    " sqrt(t2_=" + Math.sqrt(t2));
        
        return pErr;
    }
    
}
