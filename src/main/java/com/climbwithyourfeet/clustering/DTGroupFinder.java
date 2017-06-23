package com.climbwithyourfeet.clustering;

import algorithms.util.PixelHelper;
import algorithms.disjointSets.DisjointSet2Helper;
import algorithms.disjointSets.DisjointSet2Node;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.List;
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
    protected float threshholdFactor = 2.5f;

    /**
     *
     */
    protected float critDensity = 0;

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
     * given the critical density and having the threshold factor, find the
     * groups of points within a critical distance of one another.
     * runtime complexity is O(N_points * lg2(N_points)).
     * @param criticalDensity
     * @param pixIdxs
     * @return the groups found using critical density
     */
    public List<TIntSet> calculateGroups(float criticalDensity, TIntSet pixIdxs) {

        PixelHelper ph = new PixelHelper();

        initMap(pixIdxs);

        this.critDensity = criticalDensity;

        float thrsh = criticalDensity * threshholdFactor;

        if (critDensity == 1) {
            // fudge to result in critical separation of 1
            thrsh = 2.f;
        }

        findGroups(thrsh, pixIdxs);

        prune();

        return groupList;
    }

    /**
     * find groups within points using the threshold to calculate the critical
     * separation, then groups are connected points closer to one another than
     * the critical separation.
     * @param thrsh
     * @param points
     */
    private void findGroups(float thrsh, TIntSet pixIdxs) {

        if (pixIdxs.isEmpty()) {
            return;
        }

        // association of 2 points for separation <= critSeparation
        float critSep = 2.f/thrsh;

        if (critSep < 1) {
            // each point is already a group
            return;
        }

        log.info("critSep=" + critSep);

        /*
        because the critical separation may be > 1, cannot use the simplest
        DFS with neighbor search by 1 pixel.

        can either sort by x and y then scan around each point by distance
        of critical separation,
        or use DFS traversal and a nearest neigbhors algoirthm to find neighbors
        within critical separation.
        */

        //TODO: need to port the nearest neighbors code here too.
        //    paused to make a shared library for projects

        /*
                int diffX = xy[0] - vX;
                int diffY = xy[1] - vY;
                double sep = Math.sqrt(diffX*diffX + diffY*diffY);

System.out.format("(%d,%d) (%d,%d) %d,%d, sep=%.2f (crit=%.2f)\n",
    xy[0], xy[1], vX, vY, diffX, diffY, sep, critSep);

                if (sep > critSep) {
                    continue;
                }

                processPair(uIndex, vIndex);
            */

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
}
