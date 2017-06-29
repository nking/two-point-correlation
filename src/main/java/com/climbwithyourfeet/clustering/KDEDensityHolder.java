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
 
    public List<OneDFloatArray> waveletCoeffs = null;
 
    /**
     * copy the coefficient arrays to lastIdx, inclusive.
     * These are made available for later use in deriving errors.
     * @param coeffs
     * @param lastIdx
     */
    public void copyInCoefficients(List<OneDFloatArray> coeffs, int lastIdx) {
        
        waveletCoeffs = new ArrayList<OneDFloatArray>(coeffs.size() - lastIdx);
        
        for (int i = 0; i < lastIdx; ++i) {
            OneDFloatArray a = coeffs.get(i);
            OneDFloatArray b = new OneDFloatArray(Arrays.copyOf(a.a, a.a.length));
            waveletCoeffs.add(b);
        }
        
    }
    
}
