package com.climbwithyourfeet.clustering;

import java.util.logging.Logger;

/**
 * uses kernel density smoothing via wavelet transforms to create an alternative
 * to histograms for finding the first peak and hence the critical density.
 * 
 * @author nichole
 */
public class CriticalDensityKDE implements ICriticalDensity {
    
    private boolean debug = false;
    
    /**
     *
     */
    protected Logger log = Logger.getLogger(this.getClass().getName());
    
    /**
     *
     */
    public CriticalDensityKDE() {
    }
    
    /**
     *
     */
    public void setToDebug() {
        debug = true;
    }
    
    /**
     * uses kernel density smoothing via wavelet transforms to create an alternative
      to histograms for finding the first peak and hence the critical density.
 
     * @param values densities 
     * @return 
     */
    public float findCriticalDensity(float[] values) {
        
        if (values == null || values.length < 10) {
            throw new IllegalArgumentException("values length must be 10 or more");
        }
        
        throw new UnsupportedOperationException("not yet implmented");
    }

}
