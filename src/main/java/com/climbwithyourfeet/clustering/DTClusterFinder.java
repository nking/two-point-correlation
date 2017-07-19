package com.climbwithyourfeet.clustering;

import algorithms.search.NearestNeighbor2D;
import algorithms.search.NearestNeighbor2DLong;
import gnu.trove.set.TLongSet;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

/**
 * main class to cluster finder whose logic is based upon distance transform,
 * density threshold, and a signal to noise argument of ~ 2.5 as a factor.
 * 
 * The critical separation can be estimated or provided.
 * 
 * If estimated, the default is to find the leading edge of the first peak 
 * of the maxima of point separations with knowledge that the surface densities 
 * represent a generalized extreme value curve (due to the nature of the
 * background points being poisson).
 * The found peak has a relationship to the background density.
 * 
 * The runtime complexity:
 * TODO: add details
 * 
 * @author nichole
 */
public class DTClusterFinder {
    
    /**
     * pixel indexes
     */
    private final TLongSet points;
    private final int width;
    private final int height;
    
    private BackgroundSeparationHolder sepHolder = null;
    
    private List<TLongSet> groups = null;

    private enum STATE {
        INIT, HAVE_BACKGROUND_SEPARATION, HAVE_GROUPS
    }
    
    private STATE state = null;
        
    public static float denseThreshholdFactor = 5f;
    
    public static float defaultThreshholdFactor = 2.5f;
    
    protected boolean userSetThreshold = false;
   
    private float threshholdFactor = defaultThreshholdFactor;
    
    private int minimumNumberInCluster = 3;
    
    private boolean debug = false;

    private Logger log = Logger.getLogger(this.getClass().getName());
    
    private boolean rescaleAxes = true;
    
    /**
     *
     * @param thePoints
     * @param width
     * @param height
     */
    public DTClusterFinder(TLongSet thePoints, int width, int height) {
        
        this.points = thePoints;
        this.width = width;
        this.height = height;
        
        state = STATE.INIT;
    }
    
    public void setToNotRescaleAxes() {
        rescaleAxes = false;
    }
    
    /**
     *
     */
    public void setToDebug() {
        debug = true;
    }
   
    /**
     *
     * @param factor
     */
    public void setThreshholdFactor(float factor) {
        this.threshholdFactor = factor;
        userSetThreshold = true;
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
     */
    public void calculateBackgroundSeparation() {
        
        if (state.compareTo(STATE.HAVE_BACKGROUND_SEPARATION) > -1) {
            return;
        }
        
        PairwiseSeparations ps = new PairwiseSeparations();
        if (debug) {
            ps.setToDebug();
        }
        
        /*
        TODO:
        for background density calculation, 
        need to look at data size and available memory to learn whether
        may need to partition the data and process each section, then combine
        results.
        */
            
        if (rescaleAxes) {
                        
            PairwiseSeparations.ScaledPoints sp = ps.scaleThePoints(
                points, width, height);
            
            System.out.println("scales=" + sp.xScale + " , " + sp.yScale);
        
            sepHolder = ps.extract(sp.pixelIdxs, sp.width, sp.height);
            
            sepHolder.setXYScales(sp.xScale, sp.yScale);
        
            correctForScales(sepHolder, sp);
            
        } else {
            sepHolder = ps.extract(points, width, height);
        }
         
        if (sepHolder.bckGndSep == null) {
            throw new IllegalStateException("Error in algorithm: "
                + " background separation did not get set");
        }
        
        this.state = STATE.HAVE_BACKGROUND_SEPARATION;
    }
    
    /**
     * set the pairwise distances for x and y that characterize the background
     * pairwise distances instead of calculating them.
     * The critical distance that is the maximum for association in a cluster
     * is thresholdFactor times these background separations.
     * @param xSeparation
     * @param ySeparation
     */
    public void setBackgroundSeparation(int xSeparation, int ySeparation) {
        
        if (state.compareTo(STATE.HAVE_BACKGROUND_SEPARATION) > -1) {
            throw new IllegalStateException("separation is already set");
        }
                
        this.sepHolder = new BackgroundSeparationHolder();
        
        sepHolder.setXYBackgroundSeparations(xSeparation, ySeparation);
        
        int separation = (int)Math.round(Math.sqrt(xSeparation * xSeparation 
            + ySeparation * ySeparation));
        
        sepHolder.setTheThreeSeparations(
            new float[]{0, separation, separation + 1});
        sepHolder.setAndNormalizeCounts(new float[]{100, 100, 0});
        
        this.state = STATE.HAVE_BACKGROUND_SEPARATION;
    }
    
    /**
     *
     */
    public void findClusters() {
        
        if (state.compareTo(STATE.HAVE_BACKGROUND_SEPARATION) < 0) {
            calculateBackgroundSeparation();
        } else if (state.compareTo(STATE.HAVE_GROUPS) >= 0) {
            return;
        }
        
        if (sepHolder.bckGndSep == null) {
            throw new IllegalStateException("background separation must be "
                + " calculated first");
        }
    
        System.out.println("findGroups: " + 
            " bckGndSep=" + Arrays.toString(sepHolder.bckGndSep) + 
            " thresholdFactor=" + threshholdFactor);
        
        DTGroupFinder groupFinder = new DTGroupFinder(width, height);
        
        groupFinder.setThreshholdFactor(threshholdFactor);
        
        groupFinder.setMinimumNumberInCluster(minimumNumberInCluster);
        
        groups = groupFinder.calculateGroupsUsingSepartion(
            sepHolder.getXBackgroundSeparation(), 
            sepHolder.getYBackgroundSeparation(), points);        
    }
    
    /**
     * applies the scale factors in sp to the fields sepHolder.bckGndSep
     * to put sepHolder.bckGndSep into the original axes reference frame.
     * 
     * @param sepHolder
     * @param sp 
     */
    private void correctForScales(BackgroundSeparationHolder sepHolder, 
        PairwiseSeparations.ScaledPoints sp) {
        
        if (sepHolder.bckGndSep == null) {
            throw new IllegalStateException("sepHolder.bckGndSep cannot be null");
        }
        
        sepHolder.bckGndSep[0] *= sp.xScale;
        sepHolder.bckGndSep[1] *= sp.yScale;
    }
    
    /**
     *
     * @return
     */
    public int getNumberOfClusters() {
        
        if (groups == null) {
            return 0;
        }
        
        return groups.size();
    }
    
    /**
     *
     * @param idx
     * @return
     */
    public TLongSet getCluster(int idx) {
        
        if (groups == null) {
            throw new IllegalArgumentException(
                "findClusters was not successfully invoked");
        }
        
        if ((idx < 0) || (idx > (groups.size() - 1))) {
            throw new IllegalArgumentException("idx is out of bounds");
        }
        
        return groups.get(idx);
    }
    
    public List<TLongSet> getGroups() {
        return groups;
    }
    
    /**
     *
     * @return
     */
    public BackgroundSeparationHolder getBackgroundSeparationHolder() {
        return sepHolder;
    }
}
