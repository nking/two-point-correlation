package com.climbwithyourfeet.clustering;

import algorithms.util.PixelHelper;
import algorithms.disjointSets.DisjointSet2Helper;
import algorithms.disjointSets.DisjointSet2Node;
import thirdparty.edu.princeton.cs.algs4.QuadTree;
import thirdparty.edu.princeton.cs.algs4.Interval;
import thirdparty.edu.princeton.cs.algs4.Interval2D;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.iterator.TLongObjectIterator;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 *
 * @author nichole
 */
public class GroupFinder {

    private DisjointSet2Helper disjointSetHelper = null;

    // key = pixIdx, value = disjoint set node with key pixIdx
    private TLongObjectMap<DisjointSet2Node<Long>> pixNodes = null;

    protected boolean use4Neighbors = false;

    /**
     * a list to hold each group as an item of pixel indexes
     */
    protected List<TLongSet> groupList = null;

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

    public GroupFinder(int imageWidth, int imageHeight) {

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
    public List<TLongSet> calculateGroupsUsingSepartion(
        float criticalSeparationX, float criticalSeparationY, TLongSet pixIdxs) {

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
    private void findGroups(float critSepX, float critSepY, TLongSet pixIdxs) {

        if (pixIdxs.isEmpty()) {
            return;
        }

        log.info("critSepX=" + critSepX + " critSepY=" + critSepY);
                
        findGroupsWithNN2D(pixIdxs, critSepX, critSepY);
    }

    private void processPair(Long uPoint, Long vPoint) {

        DisjointSet2Node<Long> uNode = pixNodes.get(uPoint);
        DisjointSet2Node<Long> uParentNode = disjointSetHelper.findSet(uNode);
        assert(uParentNode != null);

        //int uGroupId = uParentNode.getMember().intValue();

        DisjointSet2Node<Long> vNode = pixNodes.get(vPoint);
        DisjointSet2Node<Long> vParentNode = disjointSetHelper.findSet(vNode);
        assert(vParentNode != null);

        //int vGroupId = vParentNode.getMember().intValue();

        DisjointSet2Node<Long> merged =
            disjointSetHelper.union(uParentNode, vParentNode);
    }

    private void prune() {

        // key = repr node index, value = set of pixels w/ repr
        TLongObjectMap<TLongSet> map = new TLongObjectHashMap<TLongSet>();

        TLongObjectIterator<DisjointSet2Node<Long>> iter =
            pixNodes.iterator();
        for (int i = 0; i < pixNodes.size(); ++i) {

            iter.advance();

            long pixIdx = iter.key();
            DisjointSet2Node<Long> node = iter.value();

            DisjointSet2Node<Long> repr = disjointSetHelper.findSet(node);

            long reprIdx = repr.getMember().longValue();

            TLongSet set = map.get(reprIdx);
            if (set == null) {
                set = new TLongHashSet();
                map.put(reprIdx, set);
            }
            set.add(pixIdx);
        }

        log.finest("number of groups before prune=" + map.size());

        // rewrite the above into a list
        List<TLongSet> groups = new ArrayList<TLongSet>();

        TLongObjectIterator<TLongSet> iter2 = map.iterator();
        for (int i = 0; i < map.size(); ++i) {
            
            iter2.advance();

            TLongSet idxs = iter2.value();

            if (idxs.size() >= minimumNumberInCluster) {
                groups.add(idxs);
            }
        }

        this.groupList = groups;

        log.finest("number of groups after prune=" + groups.size());
    }

    private void initMap(TLongSet pixIdxs) {

        System.out.println("initMap for " + pixIdxs.size());
        
        pixNodes = new TLongObjectHashMap<DisjointSet2Node<Long>>();

        disjointSetHelper = new DisjointSet2Helper();

        TLongIterator iter = pixIdxs.iterator();

        //long totalMemory = Runtime.getRuntime().totalMemory();
        //MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        //long heapUsage = mbean.getHeapMemoryUsage().getUsed();
        //long avail = totalMemory - heapUsage;
        //System.out.println("mem avail=" + avail + " used=" + heapUsage);

        while (iter.hasNext()) {

            long pixIdx = iter.next();

            DisjointSet2Node<Long> pNode =
                disjointSetHelper.makeSet(
                    new DisjointSet2Node<Long>(Long.valueOf(pixIdx)));

            pixNodes.put(pixIdx, pNode);
            
            //System.out.println("mem avail=" + avail + " used=" + heapUsage);
        }
    }

    private void findGroupsWithNN2D(TLongSet pixIdxs, float critSepX, 
        float critSepY) {
        
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        QuadTree<Integer, Long> centroidQT = new QuadTree<Integer, Long>();
        TLongIterator iter = pixIdxs.iterator();
        while (iter.hasNext()) {
            long uIdx = iter.next();            
            ph.toPixelCoords(uIdx, imgWidth, xy);
            centroidQT.insert(xy[0], xy[1], Long.valueOf(uIdx)); 
        }
        
        iter = pixIdxs.iterator();
        while (iter.hasNext()) {
            
            long uIdx = iter.next();
            
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

            List<Long> pixIndexes = centroidQT.query2D(rect);
            if (pixIndexes == null || pixIndexes.size() < 2) {
                continue;
            }

            for (Long pixIndex : pixIndexes) {
                long vPix = pixIndex.longValue();
                //ph.toPixelCoords(vPix, imgWidth, xy);
                //int vX = xy[0];
                //int vY = xy[1];
                
                processPair(uIdx, pixIndex);
            }
        }    
    }
}
