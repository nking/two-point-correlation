package com.climbwithyourfeet.clustering;

import algorithms.util.PairInt;
import algorithms.util.PixelHelper;
import gnu.trove.set.TIntSet;
import gnu.trove.set.TLongSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;

/**
 *
 * @author nichole
 */
public class DTGroupFinderTest extends TestCase {
    
    /**
     *
     * @throws Exception
     */
    public void testCalculateGroups() throws Exception {
        
        Set<PairInt> points = getData0();
        
        PixelHelper ph = new PixelHelper();
        
        TLongSet pixIdxs = ph.convert(points, 8);
        
        DTGroupFinder finder = new DTGroupFinder(8, 8);
                
        float critSep = 2.f;
        
        List<TLongSet> groups = 
            finder.calculateGroupsUsingSepartion(critSep, critSep, 
                pixIdxs);
        
        assertTrue(groups.size() == 1);
        
        TLongSet g0p = groups.get(0);
        Set<PairInt> g0 = ph.convert(g0p, 8);
        
        assertTrue(g0.size() == 6);
        
        assertTrue(points.contains(new PairInt(1, 2)));
        assertTrue(points.contains(new PairInt(2, 2)));
        assertTrue(points.contains(new PairInt(3, 2)));
        assertTrue(points.contains(new PairInt(2, 3)));
        assertTrue(points.contains(new PairInt(2, 5)));
        assertTrue(points.contains(new PairInt(3, 6)));
    }

    private Set<PairInt> getData0() {
        
        /*
         7                      @
         6          @
         5       @
         4          
         3       @
         2    @  @  @
         1
         0
           0  1  2  3  4  5  6  7
        
        */
        
        Set<PairInt> points = new HashSet<PairInt>();
        points.add(new PairInt(1, 2));
        points.add(new PairInt(2, 2));
        points.add(new PairInt(3, 2));
        points.add(new PairInt(2, 3));
        points.add(new PairInt(2, 5));
        points.add(new PairInt(3, 6));
        points.add(new PairInt(7, 7));
        
        return points;
    }
}
