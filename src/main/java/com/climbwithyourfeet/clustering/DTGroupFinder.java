package com.climbwithyourfeet.clustering;

import algorithms.util.PixelHelper;
import algorithms.disjointSets.DisjointSet2Helper;
import algorithms.disjointSets.DisjointSet2Node;
import algorithms.search.NearestNeighbor2D;
import algorithms.util.PairInt;
import algorithms.util.PairIntArray;
import thirdparty.edu.princeton.cs.algs4.QuadTree;
import thirdparty.edu.princeton.cs.algs4.Interval;
import thirdparty.edu.princeton.cs.algs4.Interval2D;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

/**
 *
 * @author nichole
 */
public class DTGroupFinder {

    private DisjointSet2Helper disjointSetHelper = null;

    // key = pixIdx, value = disjoint set node with key pixIdx
    private TIntObjectMap<DisjointSet2Node<Integer>> pixNodes = null;

    protected boolean use4Neighbors = false;

    /**
     * a list to hold each group as an item of pixel indexes
     */
    protected List<TIntSet> groupList = null;

    protected int minimumNumberInCluster = 3;

    private final int imgWidth;
    private final int imgHeight;
    /**
     *
     */
    protected boolean debug = false;

    /**
     *
     */
    protected float threshholdFactor = 1.0f;

    protected Logger log = Logger.getLogger(this.getClass().getName());

    public DTGroupFinder(int imageWidth, int imageHeight) {

        imgWidth = imageWidth;

        imgHeight = imageHeight;
    }

    /**
     *
     * @param n
     */
    public void setMinimumNumberInCluster(int n) {
        this.minimumNumberInCluster = n;
    }

    /**
     *
     * @param setDebugToTrue
     */
    public void setDebug(boolean setDebugToTrue) {
        this.debug = setDebugToTrue;
    }

    /**
     *
     * @param factor
     */
    public void setThreshholdFactor(float factor) {
        this.threshholdFactor = factor;
    }

    /**
     *
     * @return threshold factor for critical density
     */
    public float getThreshholdFactor() {
        return this.threshholdFactor;
    }

    /**
     * given the pairwise separation of background points
     * and having the threshold factor, find the
     * groups of points within a critical distance of one another.
     * runtime complexity is 
     * 
     * @param criticalSeparationX
     * @param criticalSeparationY
     * @param pixIdxs
     * @return the groups found using critical density
     */
    public List<TIntSet> calculateGroupsUsingSepartion(
        float criticalSeparationX, float criticalSeparationY, TIntSet pixIdxs) {

        initMap(pixIdxs);
        
        float critSepX = criticalSeparationX/threshholdFactor;
        float critSepY = criticalSeparationY/threshholdFactor;

        findGroups(critSepX, critSepY, pixIdxs);
        
        prune();

        return groupList;
    }
    
    /**
     * find groups within points using the threshold to calculate the critical
     * separation, then groups are connected points closer to one another than
     * the critical separation.
     * @param points
     */
    private void findGroups(float critSepX, float critSepY, TIntSet pixIdxs) {

        if (pixIdxs.isEmpty()) {
            return;
        }

        log.info("critSepX=" + critSepX + " critSepY=" + critSepY);
                
        findGroupsWithNN2D(pixIdxs, critSepX, critSepY);
    }

    private void processPair(Integer uPoint, Integer vPoint) {

        DisjointSet2Node<Integer> uNode = pixNodes.get(uPoint);
        DisjointSet2Node<Integer> uParentNode
            = disjointSetHelper.findSet(uNode);
        assert(uParentNode != null);

        int uGroupId = uParentNode.getMember().intValue();

        DisjointSet2Node<Integer> vNode = pixNodes.get(vPoint);
        DisjointSet2Node<Integer> vParentNode
            = disjointSetHelper.findSet(vNode);
        assert(vParentNode != null);

        int vGroupId = vParentNode.getMember().intValue();

        DisjointSet2Node<Integer> merged =
            disjointSetHelper.union(uParentNode, vParentNode);

        pixNodes.put(uGroupId, merged);

        pixNodes.put(vGroupId, merged);
    }

    private void prune() {

        // key = repr node index, value = set of pixels w/ repr
        TIntObjectMap<TIntSet> map = new TIntObjectHashMap<TIntSet>();

        TIntObjectIterator<DisjointSet2Node<Integer>> iter =
            pixNodes.iterator();
        for (int i = 0; i < pixNodes.size(); ++i) {

            iter.advance();

            int pixIdx = iter.key();
            DisjointSet2Node<Integer> node = iter.value();

            DisjointSet2Node<Integer> repr = disjointSetHelper.findSet(node);

            int reprIdx = repr.getMember().intValue();

            TIntSet set = map.get(reprIdx);
            if (set == null) {
                set = new TIntHashSet();
                map.put(reprIdx, set);
            }
            set.add(pixIdx);
        }

        log.finest("number of groups before prune=" + map.size());

        // rewrite the above into a list
        List<TIntSet> groups = new ArrayList<TIntSet>();

        TIntObjectIterator<TIntSet> iter2 = map.iterator();
        for (int i = 0; i < map.size(); ++i) {
            
            iter2.advance();

            TIntSet idxs = iter2.value();

            if (idxs.size() >= minimumNumberInCluster) {
                groups.add(idxs);
            }
        }

        this.groupList = groups;

        log.finest("number of groups after prune=" + groups.size());
    }

    private void initMap(TIntSet pixIdxs) {

        pixNodes = new TIntObjectHashMap<DisjointSet2Node<Integer>>();

        disjointSetHelper = new DisjointSet2Helper();

        TIntIterator iter = pixIdxs.iterator();

        while (iter.hasNext()) {

            int pixIdx = iter.next();

            DisjointSet2Node<Integer> pNode =
                disjointSetHelper.makeSet(
                    new DisjointSet2Node<Integer>(Integer.valueOf(pixIdx)));

            pixNodes.put(pixIdx, pNode);
        }
    }

    private void findGroupsWithNN2D(TIntSet pixIdxs, float critSepX, 
        float critSepY) {
        
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        QuadTree<Integer, Integer> centroidQT = new QuadTree<Integer, Integer>();
        TIntIterator iter = pixIdxs.iterator();
        while (iter.hasNext()) {
            int uIdx = iter.next();            
            ph.toPixelCoords(uIdx, imgWidth, xy);
            centroidQT.insert(xy[0], xy[1], Integer.valueOf(uIdx)); 
        }
        
        iter = pixIdxs.iterator();
        while (iter.hasNext()) {
            
            int uIdx = iter.next();
            
            ph.toPixelCoords(uIdx, imgWidth, xy);
            
            int uX = xy[0];
            int uY = xy[1];
            
            int x0 = Math.round(uX - critSepX);
            if (x0 < 0) {
                x0 = 0;
            }
            int x1 = Math.round(uX + critSepX);
            if (x1 >= imgWidth) {
                x1 = imgWidth - 1;
            }
            int y0 = Math.round(uY - critSepY);
            if (y0 < 0) {
                y0 = 0;
            }
            int y1 = Math.round(uY + critSepY);
            if (y1 >= imgHeight) {
                y1 = imgHeight - 1;
            }
            
            Interval<Integer> intX = new Interval<Integer>(x0, x1);
            Interval<Integer> intY = new Interval<Integer>(y0, y1);
            Interval2D<Integer> rect = new Interval2D<Integer>(intX, intY);

            List<Integer> pixIndexes = centroidQT.query2D(rect);
            if (pixIndexes == null || pixIndexes.size() < 2) {
                continue;
            }

            for (Integer pixIndex : pixIndexes) {
                int vPix = pixIndex.intValue();
                //ph.toPixelCoords(vPix, imgWidth, xy);
                //int vX = xy[0];
                //int vY = xy[1];
                
                processPair(uIdx, pixIndex);
            }            
        }        
    }
}
