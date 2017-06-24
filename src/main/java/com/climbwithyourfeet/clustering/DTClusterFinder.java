package com.climbwithyourfeet.clustering;

import gnu.trove.set.TIntSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * main class to cluster finder whose logic is based upon distance transform,
 * density threshold, and a signal to noise argument of ~ 3 as a factor.
 * 
 * runtime complexity ~ O(N_pixels) + ~O(N_points * lg2(N_points))
 * 
 * @author nichole
 */
public class DTClusterFinder {
    
    /**
     * pixel indexes
     */
    private final TIntSet points;
    private final int width;
    private final int height;
    
    private float critDens = Float.POSITIVE_INFINITY;
    
    private List<TIntSet> groups = null;

    private enum STATE {
        INIT, HAVE_CLUSTER_DENSITY, HAVE_GROUPS
    }
    
    private STATE state = null;
    
    private float threshholdFactor = 2.5f;
    
    private int minimumNumberInCluster = 3;
    
    private boolean debug = false;

    private Logger log = Logger.getLogger(this.getClass().getName());
    
    /**
     *
     * @param thePoints
     * @param width
     * @param height
     */
    public DTClusterFinder(TIntSet thePoints, int width, int height) {
        
        this.points = thePoints;
        this.width = width;
        this.height = height;
        
        state = STATE.INIT;
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
    public void calculateCriticalDensity() {
        
        if (state.compareTo(STATE.HAVE_CLUSTER_DENSITY) > -1) {
            return;
        }
        
        DensityExtractor densExtr = new DensityExtractor();
        
        if (debug) {
            densExtr.setToDebug();
        }

        float[] densities = densExtr.extractSufaceDensity(points, width, height);
        
        CriticalDensitySolver densSolver = new CriticalDensitySolver();
        
       
        this.critDens = densSolver.findCriticalDensity(densities);  
        
        this.state = STATE.HAVE_CLUSTER_DENSITY;
    }
    
    /**
     *
     * @param dens
     */
    public void setCriticalDensity(float dens) {
        
        if (state.compareTo(STATE.HAVE_CLUSTER_DENSITY) > -1) {
            throw new IllegalStateException("cluster density is already set");
        }
        
        this.critDens = dens;
        
        this.state = STATE.HAVE_CLUSTER_DENSITY;
    }
    
    /**
     *
     */
    public void findClusters() {
        
        if (state.compareTo(STATE.HAVE_CLUSTER_DENSITY) < 0) {
            calculateCriticalDensity();
        } else if (state.compareTo(STATE.HAVE_GROUPS) >= 0) {
            return;
        }
        
        DTGroupFinder groupFinder = new DTGroupFinder(width, height);
        
        groupFinder.setThreshholdFactor(threshholdFactor);
        
        groupFinder.setMinimumNumberInCluster(minimumNumberInCluster);
        
        groups = groupFinder.calculateGroups(critDens, points);        
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
    public TIntSet getCluster(int idx) {
        
        if (groups == null) {
            throw new IllegalArgumentException(
                "findClusters was not successfully invoked");
        }
        
        if ((idx < 0) || (idx > (groups.size() - 1))) {
            throw new IllegalArgumentException("idx is out of bounds");
        }
        
        return groups.get(idx);
    }
    
    public List<TIntSet> getGroups() {
        return groups;
    }
    
    /**
     *
     * @return
     */
    public float getCriticalDensity() {
        return critDens;
    }
    
}
