package com.climbwithyourfeet.clustering;

/**
 *
 * @author nichole
 */
public interface ICriticalDensity {
    
    public void setToDebug();
    
    public DensityHolder findCriticalDensity(float[] values);

    public boolean isSparse();
}
