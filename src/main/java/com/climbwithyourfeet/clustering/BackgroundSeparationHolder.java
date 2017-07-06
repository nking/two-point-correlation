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
      
    public void setTheThreeSeparations(float[] s) {
        if (s.length != 3) {
            throw new IllegalArgumentException(
                " the length should be 3.  see variable documentation");
        }
        threeS = Arrays.copyOf(s, s.length);
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
    
    /**
     * normalize the counts and set them internally.  has side effect
     * of calculating the errors too.
     * @param counts 
     */
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
        
        where cn2 is approx 0
                
        (v2-v1)*cn1*0.5 = area1Norm
        cn1 = area1Norm / ((v2-v1)*0.5)
        
        v1*cn1 + (cn0-cn1)*v1*0.5 = area0Norm
        (cn0-cn1) = (area0Norm - v1*cn1)/(v1*0.5)
        cn0 = cn1 + (area0Norm - v1*cn1)/(v1*0.5)
        */
        
        threeSCounts = new float[3];
        
        if (threeS[2] == threeS[1] && threeS[1] == threeS[0]) {
            //TODO: revisit this
            threeSCounts[0] = 0.333f;
            threeSCounts[1] = 0.333f;
            threeSCounts[2] = 0.333f;
        } else if (threeS[2] == threeS[1]) {
          
            /*
            cn0
                   cn1
                     
            0      v1
            */
            double areaR = threeS[1] * counts[1];
            double areaT = 0.5f * threeS[1] * Math.abs(counts[0] - counts[1]);
            // areaR_Norm/area = areaR/(areaR + areaT)
            double areaR_Norm = area * areaR/(areaR + areaT);
            // v1*cn1 = areaR_Norm
            // cn1 = areaR_Norm/v1
            threeSCounts[1] = (float)areaR_Norm/threeS[1];
            
            double areaT_Norm = area * areaR/(areaR + areaT);
            if (counts[0] > counts[1]) {
                //(cn0-cn1)*v1 = areaT_Norm
                threeSCounts[0] = threeSCounts[1] + (float)(areaT_Norm/threeS[1]);
            } else {
                //(cn1-cn0)*v1 = areaT_Norm
                // (cn1-cn0) = areaT_Norm/v1
                // cn0 = cn1 - (areaT_Norm/v1)
                threeSCounts[0] = threeSCounts[1] - (float)(areaT_Norm/threeS[1]);
            }
            
            // 2 and 1 share same value
            threeSCounts[1] /= 2.f;
            threeSCounts[2] = threeSCounts[1];
            
        } else if (threeS[1] == threeS[0]) {
            /*
            cn0
                  
                   cn2
            0      v2
            
            where cn2 is approx 0
            
            v2 * cn0 = area
            */
            threeSCounts[0] = (float)(area)/threeS[2];
            
            // 1 and 0 share same value
            threeSCounts[0] /= 2.f;
            threeSCounts[1] = threeSCounts[0];
            
        } else {
            threeSCounts[1] = (float)(area1Norm / ((threeS[2] - threeS[1])*0.5));
            threeSCounts[0] = threeSCounts[1] + 
                (float)((area0Norm - threeS[1]*threeSCounts[1])/(threeS[1] * 0.5));
        }
        
        /*
        for errors, will approximate them as sqrt(counts):
          v = counts
          e = sqrt(counts)
          vn = normalized counts
          r = vn/counts
          en = r * e
        */
        threeSErrors = new float[3];
        for (int i = 0; i < 3; ++i) {
            if (counts[i] == 0) {
                threeSErrors[i] = Float.POSITIVE_INFINITY;
                continue;
            }
            float e = (float)Math.sqrt(counts[i]);
            float r = threeSCounts[i]/counts[i];
            threeSErrors[i] = r * e;
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
        
        int idx0, idx1;
        
        if (threeS[0] == threeS[1] && threeS[0] == threeS[2]) {
            return threeSCounts[1];
        } else if (threeS[0] == threeS[1]) {
            idx0 = 1;
            idx1 = 2;
        } else if (threeS[1] == threeS[2]) {
            idx0 = 0;
            idx1 = 1;
        } else {        
            if (separation > threeS[2]) {
                return 0;
            } else if (separation < threeS[1]) {
                idx0 = 0;
                idx1 = 1;
            } else {
                idx0 = 1;
                idx1 = 2;
            }
        }
        
        /*
        p(x) - p(idx1)     p(idx0) - p(idx1)
        ---------------- = -----------------
        sd(x) - sd(idx1)   sd(idx0) - sd(idx1)
        
        r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        p(x) = p(idx1) + r * (sd(x) - sd(idx1))
        */
        
        float r = calcCtoSD(idx0, idx1);
        
        float p = threeSCounts[idx1] + r * (separation - threeS[idx1]);
        
        return p;
    }
    
    private float calcCtoSD(int idx0, int idx1) {
        
        //r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        float r = (threeSCounts[idx0] - threeSCounts[idx1])/
            (threeS[idx0] - threeS[idx1]);
        
        return r;
    }
    
    private float calcEtoSD(int idx0, int idx1) {
        
        //r = (pE(idx0) - pE(idx1))/(sd(idx0) - sd(idx1))
        
        float r = (threeSErrors[idx0] - threeSErrors[idx1])/
            (threeS[idx0] - threeS[idx1]);
        
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
                
        int idx0, idx1;
        
        if (threeS[0] == threeS[1] && threeS[0] == threeS[2]) {
            output[0] = threeSCounts[1];
            output[1] = threeSErrors[1];
            return;
        } else if (threeS[0] == threeS[1]) {
            idx0 = 1;
            idx1 = 2;
        } else if (threeS[1] == threeS[2]) {
            idx0 = 0;
            idx1 = 1;
        } else {        
            if (separation > threeS[2]) {
                Arrays.fill(output, 0);
                return;
            } else if (separation < threeS[1]) {
                idx0 = 0;
                idx1 = 1;
            } else {
                idx0 = 1;
                idx1 = 2;
            }
        }
                
        /*
        p(x) - p(idx1)     p(idx0) - p(idx1)
        ---------------- = -----------------
        sd(x) - sd(idx1)   sd(idx0) - sd(idx1)
        
        r = (p(idx0) - p(idx1))/(sd(idx0) - sd(idx1))
        
        p(x) = p(idx1) + r * (sd(x) - sd(idx1))
        */
        
        float sDiff = separation - threeS[idx1];
                    
        float r = calcCtoSD(idx0, idx1);
        
        float p = threeSCounts[idx1] + r * sDiff;
        
        float rE = calcEtoSD(idx0, idx1);
        
        //using the same slopes for errors.
        float pErr = threeSErrors[idx1] + rE * sDiff;
    
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
