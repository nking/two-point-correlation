package com.climbwithyourfeet.clustering;

import algorithms.search.KDTree;
import algorithms.search.KDTreeNode;
import algorithms.util.PixelHelper;
import gnu.trove.iterator.TLongIntIterator;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.TLongFloatMap;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.set.TLongSet;

/**
 *
 * @author nichole
 */
public class StatsHelper {
    
    public void calculateProbabilities(BackgroundSeparationHolder dh, 
        TLongSet pixIdxs, 
        int width, int height,
        TLongFloatMap outputPointProbMap,
        TLongFloatMap outputPointProbErrMap) {
        
        TLongIntMap pixSep = calculatePixelSeparations(pixIdxs, width, height,
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
    private TLongIntMap calculatePixelSeparations(TLongSet pixIdxs, 
        int width, int height, int[] scales) {
        
        KDTree nn = new KDTree(pixIdxs, width, height);
               
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        // key = pixIdx, value = separation
        TLongIntMap dMap = new TLongIntHashMap();
        
        TLongIterator iter = pixIdxs.iterator();
        while (iter.hasNext()) {
        
            long pixIdx = iter.next();
        
            ph.toPixelCoords(pixIdx, width, xy);
            
            KDTreeNode nearest = nn.findNearestNeighborNotEquals(xy[0], xy[1]);
            
            if (nearest != null && nearest.getX() != nearest.sentinel) {
                                
                float dx = nearest.getX() - xy[0];
                dx /= (float)scales[0];
                float dy = nearest.getY() - xy[1];
                dy /= (float)scales[1];
                
                int d = (int)Math.round(Math.sqrt(dx*dx + dy*dy));
                                            
                dMap.put(pixIdx, d);
            }
        }
        
        return dMap; 
    }

    private void setProbabilities(BackgroundSeparationHolder dh, 
        TLongIntMap pixSep, TLongFloatMap outputPointProbMap, 
        TLongFloatMap outputPointProbErrMap) {

        float[] pp = new float[2];
        
        TLongIntIterator iter = pixSep.iterator();
        for (int i = 0; i < pixSep.size(); ++i) {
            iter.advance();
            
            long pixIdx = iter.key();
            int d = iter.value();
            
            dh.calcProbabilityAndError(d, pp);
            
            outputPointProbMap.put(pixIdx, pp[0]);
            
            outputPointProbErrMap.put(pixIdx, pp[1]);
        }
    }
    
}
