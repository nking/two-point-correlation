package com.climbwithyourfeet.clustering;

import java.util.Arrays;

/**
 *
 * @author nichole
 */
public abstract class AbstractCriticalDensity implements ICriticalDensity {
    
    protected boolean debug = false;
    protected boolean isSparse = true;

    public AbstractCriticalDensity() {
    }

    /**
     *
     */
    public void setToDebug() {
        debug = true;
    }

    public boolean isSparse() {
        return isSparse;
    }
   
    protected void doSparseEstimate(float[] a) {
        // 0 : 0.25
        // 0 : 0.5
        // 0 : 0.75
        // 0 : 1
        // 0.25 : 0.5
        
        int n = a.length;
        float[] sums = new float[5];    
        for (int i = 0; i < n; ++i) {
            if (i < ((float)n/4.f)) {
                sums[0] += a[i];
            }
            if (i < ((float)n/2.f)) {
                sums[1] += a[i];
                if (i >= ((float)n/4.f)) {
                    sums[4] += a[i];
                }
            } 
            if (i < ((float)n*3.f/4.f)) {
                sums[2] += a[i];
            }
            sums[3] += a[i];
        }
        
        // convert to fractions
        float norm = sums[3];
        for (int i = 0; i < sums.length; ++i) {
            sums[i] /= norm;
        }
        
        if (debug) {
            System.out.println("qs=" + Arrays.toString(sums));
        }
        
        if (sums[2] < 0.3) {
            isSparse = false;
        } else {
            isSparse = true;
        }
    }
    
}
