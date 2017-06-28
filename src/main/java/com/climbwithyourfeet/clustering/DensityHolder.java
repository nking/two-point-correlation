package com.climbwithyourfeet.clustering;

/**
 * contains the surface densities and their frequencies and a representation
 * of the PMF or PDF as the arrays (dens, normCount).
 * 
 * @author nichole
 */
public class DensityHolder {
    
    public float critDens = Float.POSITIVE_INFINITY;
    
    /**
     * surface densities
     */
    public float[] dens = null;
    
    /**
     * frequency of the surface densities
     */
    public float[] count = null;
    
    /**
     * the count, normalized to sum to 1.
     */
    public float[] normCount = null;
    
}
