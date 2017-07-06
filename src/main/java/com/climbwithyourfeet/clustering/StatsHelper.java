package com.climbwithyourfeet.clustering;

import algorithms.search.NearestNeighbor2D;
import algorithms.util.PairInt;
import algorithms.util.PixelHelper;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.map.TIntFloatMap;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TIntSet;
import java.util.Set;

/**
 *
 * @author nichole
 */
public class StatsHelper {
    
    public void calculateProbabilities(BackgroundSeparationHolder dh, 
        TIntSet pixIdxs, 
        int width, int height,
        TIntFloatMap outputPointProbMap,
        TIntFloatMap outputPointProbErrMap) {
        
        TIntIntMap pixSep = calculatePixelSeparations(pixIdxs, width, height,
            dh.scales);
                            
        setProbabilities(dh, pixSep, outputPointProbMap, 
            outputPointProbErrMap);        
    }

    /**
     * given pixel indexes, scale factors for the PDF in dh, and the data widht
     * and height, calculate the separation of the nearest neighbors in the
     * scaled reference frame.
     * 
     * @param pixIdxs
     * @param width
     * @param height
     * @param scales
     * @return 
     */
    private TIntIntMap calculatePixelSeparations(TIntSet pixIdxs, 
        int width, int height, int[] scales) {
        
        NearestNeighbor2D nn = new NearestNeighbor2D(pixIdxs, width, height);
        
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        // key = pixIdx, value = separation
        TIntIntMap dMap = new TIntIntHashMap();
        
        TIntIterator iter = pixIdxs.iterator();
        while (iter.hasNext()) {
        
            int pixIdx = iter.next();
        
            ph.toPixelCoords(pixIdx, width, xy);
            
            Set<PairInt> nearest = nn.findClosestNotEqual(xy[0], xy[1]);
            
            if (nearest != null && nearest.size() > 0) {
                
                PairInt p1 = nearest.iterator().next();
                
                float dx = p1.getX() - xy[0];
                dx /= (float)scales[0];
                float dy = p1.getY() - xy[1];
                dy /= (float)scales[1];
                
                int d = (int)Math.round(Math.sqrt(dx*dx + dy*dy));
                                            
                dMap.put(pixIdx, d);
            }
        }
        
        return dMap; 
    } 

    private void setProbabilities(BackgroundSeparationHolder dh, 
        TIntIntMap pixSep, TIntFloatMap outputPointProbMap, 
        TIntFloatMap outputPointProbErrMap) {

        float[] pp = new float[2];
        
        TIntIntIterator iter = pixSep.iterator();
        for (int i = 0; i < pixSep.size(); ++i) {
            iter.advance();
            
            int pixIdx = iter.key();
            int d = iter.value();
            
            dh.calcProbabilityAndError(d, pp);
            
            outputPointProbMap.put(pixIdx, pp[0]);
            
            outputPointProbErrMap.put(pixIdx, pp[1]);
        }
    }
    
}
