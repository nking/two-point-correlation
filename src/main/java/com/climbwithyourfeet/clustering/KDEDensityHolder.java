package com.climbwithyourfeet.clustering;

import java.util.Arrays;

/**
 *
 * @author nichole
 */
public class KDEDensityHolder extends DensityHolder {
 
    /*
    <pre>
    for the KDE 
    crit surf dens calculation, three points are used to
    construct a PDF:
    
            |                   *
     counts |                   |
            |                   |
            |      *            |
            |     /|            |
            -----/-|------------|-
                   c.s.d.       1.0
              surface density
    
    the first non-zero before the critical surface density (c.s.d),
    the c.s.d., and the point at 1.0
    
    The PDF is a straight line between the 3.
    </pre>
    */
   
    /**
     * the surface densities of 3 points in the discrete density
     * curve:
     * threeSDs[0] is the first point above zero counts occurring
     * before the critical surface density,
     * threeSDs[1] is the critical surface density,
     * threeSDs[2] is the value 1.0.
     */
    protected float[] threeSDs;
    
    /**
     * the counts for the surface densities in array threeSDs.
     * These can be normalized using the setter.
     */
    protected float[] threeSDCounts;
    
    /**
     * the errors for the points in threeSDs
     */
    protected float[] threeSDErrors;
    
    /**
     * the effective bandwidth of the retained kernel transformation
     */
    public int approxH;
    
    public void setTheThreeSurfaceDensities(float[] sds) {
        if (sds.length != 3) {
            throw new IllegalArgumentException(
                " the length should be 3.  see variable documentation");
        }
        threeSDs = Arrays.copyOf(sds, sds.length);
    }
    
    public void setTheThreeErrors(float[] errs) {
        if (errs.length != 3) {
            throw new IllegalArgumentException(
                " the length should be 3.  see variable documentation");
        }
        threeSDErrors = Arrays.copyOf(errs, errs.length);
    }
    
    public void setAndNormalizeCounts(float[] counts) {
        if (counts.length != 3) {
            throw new IllegalArgumentException(
                " the length of counts should be 3.  see variable documentation");
        }
        threeSDCounts = new float[3];
        float tot = 0;
        for (int i = 0; i < counts.length; ++i) {
            tot += counts[i];
        }
        for (int i = 0; i < counts.length; ++i) {
            threeSDCounts[i] = counts[i]/tot;
        }
    }
    
    public float calcProbability(float surfaceDensity) {
        
        int idx0, idx1;
        if (surfaceDensity < threeSDCounts[0]) {
            return 0;
        } else if (surfaceDensity < threeSDCounts[1]) {
            idx0 = 0;
            idx1 = 1;
        } else {
            idx0 = 1;
            idx1 = 2;
        }
        
        /*
        p(x) - p(idx1)     p(idx0) - p(idx1)
        ---------------- = -----------------
        sd(x) - sd(idx1)   sd(idx0) - sd(idx1)
        
        r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        p(x) = p(idx1) + r * (sd(x) - sd(idx1))
        */
        
        float r = calcCtoSD(idx0, idx1);
        
        float p = threeSDCounts[idx1] + r * (surfaceDensity - threeSDs[idx1]);
        
        return p;
    }
    
    private float calcCtoSD(int idx0, int idx1) {
        
        //r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        float r = (threeSDCounts[idx0] - threeSDCounts[idx1])/
            (threeSDs[idx0] - threeSDs[idx1]);
        
        return r;
    }
    
    public void calcProbabilityAndError(float surfaceDensity,
        float[] output) {
        
        int idx0, idx1;
        if (surfaceDensity < threeSDCounts[0]) {
            Arrays.fill(output, 0);
            return;
        } else if (surfaceDensity < threeSDCounts[1]) {
            idx0 = 0;
            idx1 = 1;
        } else {
            idx0 = 1;
            idx1 = 2;
        }
        
        /*
        p(x) - p(idx1)     p(idx0) - p(idx1)
        ---------------- = -----------------
        sd(x) - sd(idx1)   sd(idx0) - sd(idx1)
        
        r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        p(x) = p(idx1) + r * (sd(x) - sd(idx1))
        */
        
        float r = calcCtoSD(idx0, idx1);
        
        float p = threeSDCounts[idx1] + r * (surfaceDensity - threeSDs[idx1]);
        
        //using the same slopes for errors.
        float pErr = threeSDErrors[idx1] + r * (surfaceDensity - threeSDs[idx1]);
    
        output[0] = p;
        output[1] = pErr;
    }
}
