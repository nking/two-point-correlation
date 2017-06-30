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
                            
        setProbabilities(dh, pixSurfaceDens, outputPointProbMap, 
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

    private void setProbabilities(KDEDensityHolder dh, 
        TIntFloatMap pixSurfaceDens, TIntFloatMap outputPointProbMap, 
        TIntFloatMap outputPointProbErrMap) {

        float[] pp = new float[2];
        
        TIntFloatIterator iter = pixSurfaceDens.iterator();
        for (int i = 0; i < pixSurfaceDens.size(); ++i) {
            iter.advance();
            
            int pixIdx = iter.key();
            float sd = iter.value();
            
            dh.calcProbabilityAndError(sd, pp);
            
            outputPointProbMap.put(pixIdx, pp[0]);
            
            outputPointProbErrMap.put(pixIdx, pp[1]);
        }
        
    }
    
}
