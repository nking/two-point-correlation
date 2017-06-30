package com.climbwithyourfeet.clustering;

/**
 *
 * @author nichole
 */
public interface ICriticalSurfDens {
    
    public void setToDebug();
    
    public DensityHolder findCriticalDensity(
        SurfDensExtractor.SurfaceDensityScaled sds);

    public boolean isSparse();
}
