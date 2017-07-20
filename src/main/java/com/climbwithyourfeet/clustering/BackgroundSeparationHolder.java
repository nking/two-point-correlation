package com.climbwithyourfeet.clustering;

import java.util.Arrays;

/**
 *
 * @author nichole
 */
public class BackgroundSeparationHolder {
 
    /*
    There are two states of data present here.
    
    (1) the PDF is in the scaled axes reference frame
        (if no scaling is performed the scaled is the original data.)
    
        those variables are:
    
        float[] dists;
        float[] counts;
        float[] errs;
    
    (2) the original axes data are:
        
        int[] scales is the x and y scales to divide the original axes by to
            result in smaller scaled axes.
    
        float[] bckGndSep is the x separation and y separation in the
           original reference frame. they are used to define the
           critical separation for association of 2 points.
    */
    
    /*
    <pre>
    creating a PDF with x axis being point pairwise separation and y axis being
    normalized counts of those separations.
    
            |                   
     counts *                   |
            | \                 |
            |  *                |
            |  |\               |
            ---|-*--------------|-
              c.s.        
              pairwise separations
    
    </pre>
    */
    
    /**
     * The PDF was created using the scaled axes.
     * scales holds the scale factors for that and they can be used on 
     * variables in the original reference frame (= not scaled) to transform
     * to the reference frame of the PDF.
     * They are each "1" by default.
     * The first dimension is for the x axis and the 2nd is for the y axis.
     */
    protected int[] scales = new int[]{1, 1};
    
    /**
      The x separation and y separation in the original reference frame that 
      are used to define the critical separation for association of 2 points
    */
    protected float[] bckGndSep = null;
   
    /**
     * the x axis of the PDF
     */
    protected float[] dists;
    
    /**
     * These are the normalized counts of the dists
     */
    protected float[] counts;
    
    /**
     * the errors for the points in (threeSs, threeSCounts)
     */
    protected float[] errs;
      
    public void setThePDF(float[] v, float[] c) {
        if (v.length != c.length) {
            throw new IllegalArgumentException(" the arrays must be same length");
        }
        dists = Arrays.copyOf(v, v.length);
        counts = Arrays.copyOf(c, c.length);
        setAndNormalizeCounts(counts);
    }
    
    public void setXYScales(int xScale, int yScale) {
        this.scales[0] = xScale;
        this.scales[1] = yScale;
    }
    
    public void setXYBackgroundSeparations(int xSep, int ySep) {
        if (bckGndSep == null) {
            bckGndSep = new float[2];
        }
        bckGndSep[0] = xSep;
        bckGndSep[1] = ySep;
    }
        
    /**
     * this is in the original x axis reference frame and represents a 
     * pairwise distance representative of background, that is non-clustered,
     * point spacing.
     * 
       <pre>
        bckGndSep[0]/scales[0] puts the field into the reference frame of the PDF
       </pre>
     * 
     * @return 
     */
    public float getXBackgroundSeparation() {
        return bckGndSep[0];
    }
    
    /**
     * this is in the original y axis reference frame and represents a 
     * pairwise distance representative of background, that is non-clustered,
     * point spacing.
     * 
     * @return 
     */
    public float getYBackgroundSeparation() {
        return bckGndSep[1];
    }
    
    /**
     * normalize the counts and set them internally.  has side effect
     * of calculating the errors too.
     * @param counts 
     */
    protected void setAndNormalizeCounts(float[] counts) {
    
        if (counts.length != 3) {
            throw new IllegalArgumentException(
                " the length of counts should be 3.  see variable documentation");
        }
        
        errs = new float[counts.length];
        
        double totalC  = 0;
        for (int i = 0; i < counts.length; i++) {
            totalC += counts[i];
            
            // assuming sqrt of n errors
            errs[i] = (float)Math.sqrt(counts[i]);
        }
        float f = 1.f;
        for (int i = 0; i < counts.length; i++) {
            counts[i] /= totalC;
            errs[i] /= totalC;
        }
    }
    
    /**
     * given x and y separations of a pixel (presumably from its nearest 
     * neighbor), use xScale and yScale to transform those separations into
     * the reference frame of the PDF which was constructed from 
     * scaled axes (where scaling by default was "1" unless calculation was
     * requested and a different scale found).
     * 
     * @param xSeparation
     * @param ySeparation
     * @return 
     */
    public float calcProbability(int xSeparation, int ySeparation) {
        
        int xSep0 = xSeparation/scales[0];
        int ySep0 = ySeparation/scales[1];
        
        int sep0 = Math.round(xSep0*xSep0 + ySep0*ySep0);
        
        return calcProbability(sep0);
    }
    
    protected void calcProbabilityAndError(int xSeparation, int ySeparation,
        float[] output) {
        
        int xSep0 = xSeparation/scales[0];
        int ySep0 = ySeparation/scales[1];
        
        int sep0 = Math.round(xSep0*xSep0 + ySep0*ySep0);
        
        calcProbabilityAndError(sep0, output);
    }
    
    /**
     * infer the probability of the given separation.
     * NOTE that the separation must be in the reference frame of the scaled
     * data.
     * @param separation in scaled reference frame
     * @return resulting probability
     */
    private float calcProbability(int separation) {
        
        int idx1 = Arrays.binarySearch(dists, dists.length);
        
        if (idx1 >= 0) {
            return counts[idx1];
        }
        
        //(-(insertion point) - 1)
        idx1 *= -1;
        idx1--;
        int idx0 = idx1 - 1;
        if (idx0 < 0) {
            return counts[0];
        }
        
        /*
        p(x) - p(idx1)     p(idx0) - p(idx1)
        ---------------- = -----------------
        sd(x) - sd(idx1)   sd(idx0) - sd(idx1)
        
        r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        p(x) = p(idx1) + r * (sd(x) - sd(idx1))
        */
        
        float r = calcCtoSD(idx0, idx1);
        
        float p = counts[idx1] + r * (separation - dists[idx1]);
        
        return p;
    }
    
    private float calcCtoSD(int idx0, int idx1) {
        
        //r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        float r = (counts[idx0] - counts[idx1])/
            (dists[idx0] - dists[idx1]);
        
        return r;
    }
    
    private float calcEtoSD(int idx0, int idx1) {
        
        //r = (pE(idx0) - pE(idx1))/(sd(idx0) - sd(idx1))
        
        float r = (errs[idx0] - errs[idx1])/(dists[idx0] - dists[idx1]);
        
        return r;
    }

    /**
     * infer the probability and error of the given separation.
     * NOTE that the separation must be in the reference frame of the scaled
     * data.
     * @param separation in scaled reference frame
     * @param output resulting probability and error
     */    
    protected void calcProbabilityAndError(int separation, float[] output) {
                
        int idx1 = Arrays.binarySearch(dists, dists.length);
        
        if (idx1 >= 0) {
            output[0] = counts[idx1];
            output[1] = errs[idx1];
            return;
        }
        
        //(-(insertion point) - 1)
        idx1 *= -1;
        idx1--;
        int idx0 = idx1 - 1;
        if (idx0 < 0) {
            output[0] = counts[0];
            output[1] = errs[0];
            return;
        }
                
        /*
        p(x) - p(idx1)     p(idx0) - p(idx1)
        ---------------- = -----------------
        sd(x) - sd(idx1)   sd(idx0) - sd(idx1)
        
        r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        p(x) = p(idx1) + r * (sd(x) - sd(idx1))
        */
        
        float sDiff = separation - dists[idx1];
                    
        float r = calcCtoSD(idx0, idx1);
        
        float p = counts[idx1] + r * sDiff;
        
        float rE = calcEtoSD(idx0, idx1);
        
        //using the same slopes for errors.
        float pErr = errs[idx1] + rE * sDiff;
    
        output[0] = p;
        output[1] = pErr;
    }
    
    float calcError(float prob, float separation, float xerr) {

        //sigma^2  =  xError^2*(Y^2)  +  yError^2*(X^2)
        
        float xerrsq = xerr * xerr;
        float t1 = xerrsq * (prob * prob);

        float count = prob;
        float t2 = count * separation * separation;

        float pErr = (float)Math.sqrt(t1 + t2);

        /*
        NOTE: if add kernel smoothing,
          consider MISE:
            integral of E((p_smoothed(x) - p(x)^2)dx
        
            E[a] = integral_-inf_to_inf of (a * f(a) * a)
                where f is the PDF
        */
        
        /*
        System.out.println(
            " sd=" + surfDens
            + " p=" + prob 
            + " pErr=" + pErr 
            + " count=" + count
            + " sqrt(t1)=" + Math.sqrt(t1) +
            " sqrt(t2_=" + Math.sqrt(t2));
        */
        return pErr;
    }

}
