package com.climbwithyourfeet.clustering;

import algorithms.util.OneDFloatArray;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 *
 * @author nichole
 */
public class KDEDensityHolder extends DensityHolder {
 
    /**
     * the residuals between the retained kernel transformation
     * and the first untransformed data.
     */
    public float[] surfDensDiff = null;
 
    /**
     * the effective bandwidth of the retained kernel transformation
     */
    public int approxH;
}
