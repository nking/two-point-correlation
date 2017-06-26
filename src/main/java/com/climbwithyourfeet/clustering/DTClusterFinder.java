package com.climbwithyourfeet.clustering;

import gnu.trove.set.TIntSet;
import java.util.List;
import java.util.logging.Logger;

/**
 * main class to cluster finder whose logic is based upon distance transform,
 * density threshold, and a signal to noise argument of ~ 2.5 as a factor.
 * 
 * The density threshold can be estimated or provided.
 * 
 * If estimated, the default is to find the leading edge of the first peak 
 * of the surface densities with knowledge that the surface densities 
 * represent a generalized extreme value curve (due to the nature of the
 * background points being poisson).
 * The found peak has a relationship to the background density.
 * 
 * The methods to estimate density are
 * <pre>
 * (1) histogram (default)
 * (2) kernel density estimator using wavelet transform
 * (3) k-nearest neighbors
 * </pre>
 * 
 * NOTE: if automatic calculation of critical density is used by default,
 * the threshold used when applying the critical density to the points
 * to find clusters is either the default for sparse points or if
 * the density curve appears to be dense, the higher threshold of 5 is used.
 * 
 * The runtime complexity:
 * TODO: add details.  
 * first the dist trans uses O(N_pixels), then the
 * complexity uses the number of density points, 
 * then the number of original data points.
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
    
    public static enum CRIT_DENS_METHOD {
        PROVIDED, HISTOGRAM, KDE, KNN
    }
    
    private STATE state = null;
    
    private CRIT_DENS_METHOD critDensMethod = CRIT_DENS_METHOD.HISTOGRAM;
    
    public static float denseThreshholdFactor = 5f;
    
    public static float defaultThreshholdFactor = 2.5f;
    
    protected boolean userSetThreshold = false;
   
    private float threshholdFactor = defaultThreshholdFactor;
    
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
     * set to override the default use of histograms in estimating the
     * critical density, to an alternative method of Kernel Density 
     * Estimator or k-Nearest Neighbors, or provided by you.
     * @param cdm
     */
    public void setCriticalDensityMethod(CRIT_DENS_METHOD cdm) {
        
        if (state.compareTo(STATE.HAVE_CLUSTER_DENSITY) > -1) {
            throw new IllegalStateException("cannot set method after critical "
                + " density has already been estimated");
        }
        
        if (cdm == null) {
            throw new IllegalArgumentException("cdm cannot be null");
        }
        
        this.critDensMethod = cdm;
    }

    /**
     *
     */
    public void calculateCriticalDensity() {
        
        if (state.compareTo(STATE.HAVE_CLUSTER_DENSITY) > -1) {
            return;
        }
        
        if (critDensMethod.ordinal() == CRIT_DENS_METHOD.PROVIDED.ordinal()) {
            throw new IllegalStateException("the critical density has been "
                + "set so cannot calculate it too.");
        }
        
        ICriticalDensity densSolver = null;
        
        if (critDensMethod.equals(CRIT_DENS_METHOD.KDE)) {
            
            densSolver = new CriticalDensityKDE();
        
        } else if (critDensMethod.equals(CRIT_DENS_METHOD.KNN)) {
            
            throw new UnsupportedOperationException("not yet implemented");
            
        } else {
            
            assert(critDensMethod.equals(CRIT_DENS_METHOD.HISTOGRAM));
     
            densSolver = new CriticalDensityHistogram();
        }
        
        DensityExtractor densExtr = new DensityExtractor();
                
        if (debug) {
            //densExtr.setToDebug();
            densSolver.setToDebug();
        }
        
        float[] densities = densExtr.extractSufaceDensity(points, width, height);
        
        this.critDens = densSolver.findCriticalDensity(densities);  
        
        if (!userSetThreshold) {
            if (!densSolver.isSparse()) {
                threshholdFactor = denseThreshholdFactor;
            }
        }
        
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
        
        this.critDensMethod = CRIT_DENS_METHOD.PROVIDED;
        
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
