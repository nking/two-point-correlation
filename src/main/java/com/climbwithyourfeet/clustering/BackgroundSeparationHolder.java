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
    
        float[] threeS;
        float[] threeSCounts;
        float[] threeSErrors;
        int approxH;
    
    (2) the original axes data are:
        
        int xScale is the amount to divide the x axis by to get the scaled axis.
        int yScale is similar, but for y axis.
    
        float[] bckGndSep is the x separation and y separation in the
           original reference frame. they are used to define the
           critical separation for association of 2 points.
    */
    
    /*
    <pre>
    creating a PDF with x axis being point pairwise separation and 
    the function being 3 non-uniform regions as inclined lines:
       (1) x-axis: 0 to c.s.
       (2) x-axis: c.s. to first x with count of 0 (or effectively zero)
       (3) x-axis: first x w/ zero count to last x
    
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
   
     <pre>
     it's relationship to this.threeS[1] is:
         xd = (bckGndSep[0]/scales[0])
         xd *= xd;
         yd = (bckGndSep[1]/scales[1])
         yd *= yd;
         
         this.threeS[1] should == sqrt(xd + yd)
     </pre>
    */
    protected float[] bckGndSep = null;
   
    /**
     * These are the 3 discrete points of the x-points of the PDF between which
     * continuous lines are interpreted.
     * 
     * The three points are:
     * threeS[0] is 0;
     * threeS[1] is the representative background separation point. 
     *    The threshold factor should be multiplied by it upon use.
     * threeS[2] is a separation value larger than threeS[1] and is the
     * last with a count or density above approximately 0.  The threshold factor
     * should be multiplied by it upon use.
     */
    protected float[] threeS;
    
    /**
     * These are the normalized counts of the 3 discrete points in threeS.
     * They are the y-points of the PDF between which continuous lines are 
     * interpreted.
     */
    protected float[] threeSCounts;
    
    /**
     * the errors for the points in (threeSs, threeSCounts)
     */
    protected float[] threeSErrors;
    
    /**
     * the effective bandwidth a point
     */
    public int approxH = 1;
    
    public void setTheThreeSeparations(float[] s) {
        if (s.length != 3) {
            throw new IllegalArgumentException(
                " the length should be 3.  see variable documentation");
        }
        threeS = Arrays.copyOf(s, s.length);
    }
    
    public void setTheThreeErrors(float[] errs) {
        if (errs.length != 3) {
            throw new IllegalArgumentException(
                " the length should be 3.  see variable documentation");
        }
        threeSErrors = Arrays.copyOf(errs, errs.length);
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
     * <pre>
     * it's relationship to this.threeS[1] is:
         xd = (xBackgroundSeparation/scales[0])
         xd *= xd;
         yd = (ybckGndSep[1]/scales[1])
         yd *= yd;
         
         this.threeS[1] should == sqrt(xd + yd)
     * </pre>
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
     * <pre>
     * it's relationship to this.threeS[1] is:
         xd = (xBackgroundSeparation/scales[0])
         xd *= xd;
         yd = (ybckGndSep[1]/scales[1])
         yd *= yd;
         
         this.threeS[1] should == sqrt(xd + yd)
     * </pre>
     * 
     * @return 
     */
    public float getYBackgroundSeparation() {
        return bckGndSep[1];
    }
    
    public void setAndNormalizeCounts(float[] counts) {
    
        if (counts.length != 3) {
            throw new IllegalArgumentException(
                " the length of counts should be 3.  see variable documentation");
        }
        
        //using area normalization
        double area  = 0;
        for (int i = 0; i < counts.length - 1; i++) {
            float yTerm = counts[i + 1] + counts[i];
            float xLen = threeS[i + 1] - threeS[i];
            if (xLen < 0) {
                xLen *= -1;
            }
            area += (yTerm * xLen);
        }
        area *= 0.5;
        double area1 = 0.5 * ((threeS[2] - threeS[1]) * counts[1]);
        double area0 = area - area1;
        
        double area0Norm = area0/area;
        double area1Norm = area1/area;
        
        /*
        cn0
               cn1
                   cn2
        0      v1  v2
        
        v1*cn1 + (cn0-cn1)*v1*0.5 = area0Norm
        
        (v2-v1)*cn1*0.5 = area1Norm
        cn1 = area1Norm / ((v2-v1)*0.5)
        
        v1*cn1 + (cn0-cn1)*v1*0.5 = area0Norm
        (cn0-cn1) = (area0Norm - v1*cn1)/(v1*0.5)
        cn0 = cn1 + (area0Norm - v1*cn1)/(v1*0.5)
        */
        
        threeSCounts = new float[3];
        threeSCounts[1] = (float)(area1Norm / ((threeS[2] - threeS[1])*0.5));
        threeSCounts[0] = (float)((area0Norm - threeS[1]*threeSCounts[1])/
            (threeS[1] * 0.5));
        
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
        
        // chessboard distance
        int sep0 = xSep0 + ySep0;
        
        return calcProbability(sep0);
    }
    
    protected void calcProbabilityAndError(int xSeparation, int ySeparation,
        float[] output) {
        
        int xSep0 = xSeparation/scales[0];
        int ySep0 = ySeparation/scales[1];
        
        // chessboard distance
        int sep0 = xSep0 + ySep0;
        
        calcProbabilityAndError(sep0, output);
    }
    
    private float calcProbability(int separation) {
        
        float dist = separation;// * thresholdFactor;
        
        int idx0, idx1;
        if (separation < threeSCounts[0]) {
            return 0;
        } else if (separation < threeSCounts[1]) {
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
        
        float p = threeSCounts[idx1] + r * (dist - threeS[idx1]);
        
        return p;
    }
    
    private float calcCtoSD(int idx0, int idx1) {
        
        //r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        float r = (threeSCounts[idx0] - threeSCounts[idx1])/
            (threeS[idx0] - threeS[idx1]);
        
        return r;
    }
    
    protected void calcProbabilityAndError(int separation, float[] output) {
        
        float dist = separation;// * thresholdFactor;
        
        int idx0, idx1;
        if (dist < threeSCounts[0]) {
            Arrays.fill(output, 0);
            return;
        } else if (dist < threeSCounts[1]) {
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
        
        float p = threeSCounts[idx1] + r * (dist - threeS[idx1]);
        
        //using the same slopes for errors.
        float pErr = threeSErrors[idx1] + r * (dist - threeS[idx1]);
    
        output[0] = p;
        output[1] = pErr;
    }
    
    float calcError(float prob, float separation, float xerr, float approxH) {

        //TODO: need to revisit this and compare to other methods of 
        // determining point-wise error
        
        //sigma^2  =  xError^2*(Y^2)  +  yError^2*(X^2)
        
        float xerrsq = xerr * xerr;
        float t1 = xerrsq * (prob * prob);

        float count = prob;
        float t2 = count * separation * separation;
        t2 /= (approxH * approxH);

        float pErr = (float)Math.sqrt(t1 + t2);

        /*
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
            + " h=" + approxH
            + " sqrt(t1)=" + Math.sqrt(t1) +
            " sqrt(t2_=" + Math.sqrt(t2));
        */
        return pErr;
    }

}
