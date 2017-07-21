package com.climbwithyourfeet.clustering;

import algorithms.misc.MinMaxPeakFinder;
import algorithms.misc.Misc0;
import algorithms.misc.MiscMath0;
import algorithms.misc.MiscSorter;
import algorithms.search.KDTree;
import algorithms.search.KNearestNeighbors;
import algorithms.search.NearestNeighbor2DLong;
import algorithms.signalProcessing.Interp;
import algorithms.signalProcessing.MedianTransform1D;
import algorithms.util.ObjectSpaceEstimator;
import algorithms.util.OneDFloatArray;
import algorithms.util.PairFloat;
import algorithms.util.PixelHelper;
import algorithms.util.PolygonAndPointPlotter;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.TFloatList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TLongHashSet;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author nichole
 */
public class PairwiseSeparations {
    
    private boolean debug = false;
    
    private final Logger log = Logger.getLogger(this.getClass().getName());
    
    protected double eps = 1.e-17;
    
    public void setToDebug() {
        debug = true;
    }
    
    private int weightedPeak(OneDFloatArray values, OneDFloatArray counts,
        float maxV) {
        
        double total = 0;
        for (int i = 0; i < counts.a.length; ++i) {
            if (values.a[i] > maxV) {
                break;
            }
            total += counts.a[i]*counts.a[i];
        }
        
        double avg = 0;
        for (int i = 0; i < counts.a.length; ++i) {
            if (values.a[i] > maxV) {
                break;
            }
            avg += (counts.a[i]*counts.a[i]/total) * values.a[i];
        }
                
        int idx = Arrays.binarySearch(values.a, (int)Math.round(avg));
        
        if (idx < 0) {
            idx *= -1;
            idx--;
        }
        if (idx >= values.a.length) {
            idx = values.a.length - 1;
        }
        
        return idx;
    }

    private boolean isMonotonicallyDecreasing(float[] v, float[] c, float maxV) {
        int prev = Math.round(c[0]);
        for (int i = 1; i < c.length; ++i) {
            if (v[i] > (0.9*maxV)) {
                break;
            }
            int ci = Math.round(c[i]);
            if (ci > prev) {
                return false;
            }
            prev = ci;
        }
        return true;
    }
    
    private int[] indexesWithinFractionOfPeak(OneDFloatArray c, 
            int peakIdx, float fractionOfPeak) {
        float hp = fractionOfPeak*c.a[peakIdx];
        int[] idxs = new int[]{peakIdx, peakIdx};
        for (int i = peakIdx - 1; i >= peakIdx; --i) {
            float ci = c.a[i];
            if (ci <= hp) {
                break;
            }
            idxs[0] = i;
        }
        for (int i = peakIdx + 1; i < c.a.length; ++i) {
            float ci = c.a[i];
            if (ci <= hp) {
                break;
            }
            idxs[1] = i;
        }
        return idxs;
    }

    private int weightedPeakAround(OneDFloatArray v, OneDFloatArray c, 
        int peakIdx, float fracOfPeak) {
        
        int[] idxs = indexesWithinFractionOfPeak(c, peakIdx, fracOfPeak);
    
        // weighted averge between and including indexes
        double total = 0;
        for (int i = idxs[0]; i <= idxs[1]; ++i) {
            total += c.a[i];
        }
        
        double avg = 0;
        for (int i = 0; i < c.a.length; ++i) {
            avg += (c.a[i]/total) * v.a[i];
        }
                
        int idx = Arrays.binarySearch(v.a, (int)Math.round(avg));
        
        if (idx < 0) {
            idx *= -1;
            idx--;
        }
        // scan forward to earliest sequential same value
        for (int i = idx - 1; i > -1; --i) {
            if (Math.abs(v.a[i] - avg) < 1.e-17) {
                idx = i;
            } else {
                break;
            }
        }
        if (idx >= v.a.length) {
            idx = v.a.length - 1;
        }
        
        return idx;
    }

    public static class ScaledPoints {
        public TLongSet pixelIdxs;
        int width;
        int height;
        int xScale;
        int yScale;
    }
    
    public BackgroundSeparationHolder extract(TLongSet pixelIdxs, int width, 
        int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException(
                "pixelIdxs.size must be 12 or more");
        }
        
        long maxC = (long)width * height;
        int maxW = 1 + (int) Math.ceil(Math.log(maxC) / Math.log(2));
        long nn2dMemory = NearestNeighbor2DLong.estimateSizeOnHeap(
            pixelIdxs.size(), maxW)/(1024*1024);
        long kdtreeMemory = 
            KDTree.estimateSizeOnHeap(pixelIdxs.size())/(1024*1024);
        long totalMemory = Runtime.getRuntime().totalMemory();
        MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        long heapUsage = mbean.getHeapMemoryUsage().getUsed();
        long avail = (totalMemory - heapUsage)/(1024*1024);
        long distTransMemory = 
            ((long)width*ObjectSpaceEstimator.getArrayReferenceSize()
            /(1024*1024))
            + ((((long)width * height)/(1024*1024)) 
            * ObjectSpaceEstimator.estimateIntSize());
        
        boolean useDT = distTransMemory < (0.75*avail);
        System.out.println("MB avail=" + avail 
            + " nn2dMem=" + nn2dMemory 
            + " kdtreeMem=" + kdtreeMemory
            + " distTransMem=" + distTransMemory
            + " distTransMemory < 0.75*avail=" + useDT
        );
        
        return extractWithNN2D(pixelIdxs, width, height);
    }
    
    /**
     * NOTE: this method is in progress and not ready for use yet
     * 
     * @param pixelIdxs
     * @param width
     * @param height
     * @return 
     */
    protected BackgroundSeparationHolder extractWithNN2D(TLongSet pixelIdxs, 
        int width, int height) {
        
        KNearestNeighbors knn = new KNearestNeighbors(pixelIdxs, width, height);
        
        //KDTree nn2d = new KDTree(pixelIdxs, width, height);
         
        //NearestNeighbor2DLong nn2d = new NearestNeighbor2DLong(pixelIdxs, 
        //    width, height);
        
        PixelHelper ph = new PixelHelper();
        
        int x, y;
        long pixIdx1;
         
        /*
        NOTE: this method is in progress.
        random sampling of void points to find maxima in separation from nearest
        pixelIdx is not working as well as the distance transform method.
        
        may include full sample of pixelsIdxs and their NN2D and contrast it
        with the random void sample to find the threshold separation.
        */
        
        int[] xy = new int[2];
        
        TIntIntMap pointValueCounts = new TIntIntHashMap();
        TLongIterator iter = pixelIdxs.iterator();
        while (iter.hasNext()) {
            pixIdx1 = iter.next();
            ph.toPixelCoords(pixIdx1, width, xy);
            
            assert(xy[0] > -1);
            assert(xy[1] > -1);
            
            List<PairFloat> nearest = knn.findNearest(2, xy[0], xy[1]);
            //KDTreeNode node = nn2d.findNearestNeighborNotEquals(xy[0], xy[1]);
            //if (node == null) {
            //    continue;
            //}
            if (nearest == null || nearest.isEmpty()) {
                continue;
            }
            PairFloat node = null;
            for (PairFloat p0 : nearest) {
                if ((Math.abs(p0.getX() - xy[0]) < eps) 
                    && (Math.abs(p0.getY() - xy[1]) < eps)) {
                    continue;
                }
                node = p0;
                break;
            }
            assert(node != null);
            
            float dx = xy[0] - node.getX();
            float dy = xy[1] - node.getY();
            int d = (int) Math.round(Math.sqrt(dx * dx + dy * dy));

            // default map value is 0 is no entry
            int c = pointValueCounts.get(d);
            pointValueCounts.put(d, c + 1);
        }
        
        if (pointValueCounts.size() == 1) {
            int m2 = pointValueCounts.keySet().iterator().next();
            
            System.out.println("m2=" + m2);

            BackgroundSeparationHolder h = new BackgroundSeparationHolder();

            h.setXYBackgroundSeparations(m2, m2);

            //TODO: revise this:
            h.setThePDF(new float[]{0, m2, 3*m2}, new float[]{0.5f, 0.5f, 0});

            return h;
        }
        
        // sort the values, make regular spacings, and use pyramidal smoothing
        // plotting each tansformed result
        
        List<OneDFloatArray> outTransC = new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outCoeffC = new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outTransV = new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outCoeffV = new ArrayList<OneDFloatArray>();
    
        float maxV = resampleAndSmooth(pointValueCounts, 
            outTransC, outCoeffC, outTransV, outCoeffV);
        
        // ------ 
        
        int k = 2;//4;
        
        TIntIntMap voidValueCounts = new TIntIntHashMap();
        
        long len = (long)width * height;
        
        // For random draws, considering 2 different approaches:
        // (1) random draw of integer within range width*height
        // (2) use of Low Discrepancy Sequences (requires moving the code to shared library)
        
        Random rand = Misc0.getSecureRandom();
        long seed = System.currentTimeMillis();
        //seed = 1500602940797L;
        System.out.println("SEED=" + seed);
        rand.setSeed(seed);
        
        //TODO: once have a fixed manner of determining the background 
        //  separation below, consider what number of nDraws can determine it
        //  to a percent of error or percent of success...
        //  depends upon the density of the data and the unknown
        //  separation of clusters. quartiles may be helpful for this. 
        
        int nDraws = pixelIdxs.size();
        //int nDraws = pixelIdxs.size()/5;
        System.out.println("pix.size=" + pixelIdxs.size() + " nDraws=" + nDraws);
        
        for (int i = 0; i < nDraws; ++i) {
        
            do {
                // not including boundary points
                x = rand.nextInt(width - 2) + 1;
                y = rand.nextInt(height - 2) + 1;
                pixIdx1 = ph.toPixelIndex(x, y, width);
            } while (pixelIdxs.contains(pixIdx1));
             
            List<PairFloat> nearest = knn.findNearest(k, x, y);
            
            if (nearest != null) {
                
                for (PairFloat p : nearest) {
                    
                    if ((Math.abs(p.getX() - x) < eps)
                        && (Math.abs(p.getY() - y) < eps)) {
                        continue;
                    }
                    
                    float dx = x - p.getX();
                    float dy = y - p.getY();
                    int d = (int)Math.round(Math.sqrt(dx*dx + dy*dy));

                    //DEBUG: temporarily excluding points larger than
                    // 3*maxV
                    if (d < 3*maxV) {
                        
                        //System.out.println("void p=" + p + " d=" + d);
                    
                        // default for no_entry is 0
                        int c = voidValueCounts.get(d);
                        voidValueCounts.put(d, c + 1);
                        
                        assert(voidValueCounts.containsKey(d));
                    }
                }
            }
        }
        
        long ts = System.currentTimeMillis();
        
        List<OneDFloatArray> outTransC_void = new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outCoeffC_void = new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outTransV_void = new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outCoeffV_void = new ArrayList<OneDFloatArray>();
    
        float maxV_void = resampleAndSmooth(voidValueCounts, 
            outTransC_void, outCoeffC_void, outTransV_void, outCoeffV_void);
             
        // overplot the point sep w/ void separation
        int idxP0 = findArrayWithNPoints(100, outTransC);
        int idxV0 = findArrayWithNPoints(100, outTransC_void);
        if (debug) {
            try {
                /*
                (float minX, float maxX, float minY, float maxY, 
                float[] xPoints, float[] yPoints, 
                float[] xPolygon, float[] yPolygon, String plotLabel)
                */
                float[] xp = outTransV.get(idxP0).a;
                float[] yp = outTransC.get(idxP0).a;
                float[] xv = outTransV_void.get(idxV0).a;
                float[] yv = outTransC_void.get(idxV0).a;
                float maxCP = MiscMath0.findMax(yp);
                float maxCV = MiscMath0.findMax(yv);
                float maxC = Math.max(maxCP, maxCV);
                PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0, width/3, 0, maxC, 
                   xp, yp, xp, yp, "pt sep");
                plotter.addPlot(0, width/3, 0, maxC, 
                   xv, yv, xv, yv, "void sep");
                plotter.writeFile("_pts_and_void_" + System.currentTimeMillis());
            } catch (Exception e) {
            }
        }
        
        int peakIdx;
        int voidIdx;
        if (isMonotonicallyDecreasing(outTransV_void.get(0).a,
            outTransC_void.get(0).a, maxV)) {
            voidIdx = 0;
            peakIdx = MiscMath0.findYMaxIndex(outTransC_void.get(voidIdx).a);
        } else {
            // calc m2 as the background separation
            // for the curves smoothed to about 15 to 20 points.
            // calc m2 as the weighted peak
            int nP = 100;
            voidIdx = findArrayWithNPoints(nP, outTransC_void);
            
            MinMaxPeakFinder mmpf = new MinMaxPeakFinder();
            float avgMin = mmpf.calculateMeanOfSmallest(
                outTransC_void.get(voidIdx).a, 0.03f);
            
            int[] peakIdxs = mmpf.findPeaks(
                outTransC_void.get(voidIdx).a, 2.5f, avgMin);
            
            if (peakIdxs == null || peakIdxs.length == 0) {
                
                peakIdx = 0;
                
            } else {
                
                peakIdx = peakIdxs[0];

  //TODO: needs refinement here.
  //      for curves with large numbers and 
  //      large number spacing, may need
  //      to use a wieghted peak then
  //      average that wtih peakIdx
                
                //peakIdx = weightedPeakAround(outTransV_void.get(voidIdx),
                //    outTransC_void.get(voidIdx), peakIdx, 0.99f);

                int n = outTransV_void.get(voidIdx).a.length;
                System.out.println("voidIdx=" + voidIdx + " out of " +
                    outTransV_void.size()
                    + " peakIdx=" + peakIdx + " l=" + 
                    (outTransV_void.get(voidIdx).a.length - 1));
                System.out.println(" values=" + 
                    Arrays.toString(outTransV_void.get(voidIdx).a));
                System.out.println(" counts=" + 
                    Arrays.toString(outTransC_void.get(voidIdx).a));

                float fraction = (float)peakIdx/(float)n;
                float fraction2 = outTransV_void.get(voidIdx).a[peakIdx] / maxV;
                float fraction3 = outTransV_void.get(voidIdx).a[peakIdx] / 
                    outTransV_void.get(voidIdx)
                    .a[outTransV_void.get(voidIdx).a.length - 1];

                System.out.println("fraction=" + fraction 
                    + " fraction2=" + fraction2
                    + " fraction3=" + fraction3
                );
            }
        }
        
        int pdfIdx = findArrayWithGTNPoints(2, outTransC);
        
        int m2 = (int)outTransV_void.get(voidIdx).a[peakIdx];
        if (m2 < 1) {
            m2 = 1;
        }
        System.out.println("m2=" + m2);
         
        BackgroundSeparationHolder h = new BackgroundSeparationHolder();
                
        h.setXYBackgroundSeparations(m2, m2);
        
        h.setThePDF(outTransV.get(pdfIdx).a, outTransC.get(pdfIdx).a);
        
        return h;
    }
   
    public ScaledPoints scaleThePoints(TLongSet pixelIdxs, int width, int height) {
        
        if (pixelIdxs.size() < 12) {
            throw new IllegalArgumentException(
                "pixelIdxs.size must be 12 or more");
        }
                
        ScaleFinder sf = new ScaleFinder();
        
        int[] xyScales = sf.find(pixelIdxs, width, height);
        
        TLongSet pixelIdxs2;
        int width2, height2;
        if (xyScales[0] <= 1 && xyScales[1] <= 1) {
            pixelIdxs2 = new TLongHashSet(pixelIdxs);
            width2 = width;
            height2 = height;
        } else if (xyScales[0] <= 1 && xyScales[1] > 1) {
            pixelIdxs2 = new TLongHashSet(pixelIdxs.size());
            width2 = width;
            height2 = (int)Math.ceil((float)height/(float)xyScales[1]);            
        } else if (xyScales[0] > 1 && xyScales[1] <= 1) {
            pixelIdxs2 = new TLongHashSet(pixelIdxs.size());
            width2 = (int)Math.ceil((float)width/(float)xyScales[0]);
            height2 = height;
        } else {
            // scale both axes
            pixelIdxs2 = new TLongHashSet(pixelIdxs.size());
            width2 = (int)Math.ceil((float)width/(float)xyScales[0]);
            height2 = (int)Math.ceil((float)height/(float)xyScales[1]);
        }
        
        if (pixelIdxs2.isEmpty()) {
            
            PixelHelper ph = new PixelHelper();
            int[] xy = new int[2];
            
            TLongIterator iter = pixelIdxs.iterator();
            while (iter.hasNext()) {
                
                long pixIdx = iter.next();
                
                ph.toPixelCoords(pixIdx, width, xy);
                
                if (xyScales[0] > 1) {
                    xy[0] /= xyScales[0];
                }
                if (xyScales[1] > 1) {
                    xy[1] /= xyScales[1];
                }
                
                long pixIdx2 = ph.toPixelIndex(xy[0], xy[1], width2);
                
                pixelIdxs2.add(pixIdx2);
            }
        }
        
        ScaledPoints sp = new ScaledPoints();
        sp.pixelIdxs = pixelIdxs2;
        sp.width = width2;
        sp.height = height2;
        sp.xScale = xyScales[0];
        sp.yScale = xyScales[1];
    
        return sp;
    }
    
    private int findPeakIfSingleProfile(float[] counts) {

        int yMaxIdx = MiscMath0.findYMaxIndex(counts);
        float yMax = counts[yMaxIdx];
        
        // increasing towards peak and decreasing after?
        boolean sp = true;
        float prev = counts[0];
        for (int i = 1; i < counts.length; ++i) {
            float v = counts[i];
            if (i < yMaxIdx) {
                if (v < prev || v > yMax) {
                    sp = false;
                    break;
                }
            } else if (i > yMaxIdx) {
                if (v > prev || v > yMax) {
                    sp = false;
                    break;
                }
            }
            prev = counts[i];
        }
        if (sp) {
            return yMaxIdx;
        }
        
        return -1;
    }
    
    private void integerResampling(int[] values, float[] counts,
        TIntList outputValues, TFloatList outputCounts) {
        
        int sStart = values[0];
        int sEnd = values[values.length - 1];
        
        int n = sEnd - sStart + 1;
        
        Interp interp = new Interp();
        float[] input = new float[2];
        
        for (int i = 0; i < values.length - 1; ++i) {
            int a0 = values[i];
            int a1 = values[i + 1];
            if (a1 == (a0 + 1)) {
                outputValues.add(a0);
                outputCounts.add(counts[i]);
                continue;
            }
            input[0] = counts[i];
            input[1] = counts[i + 1];
            int n2 = a1 - a0 + 1;
            float[] output = interp.linearInterp(input, n2, 0, Integer.MAX_VALUE);
            for (int j = 0; j < n2; ++j) {
                outputValues.add(j + a0);
                outputCounts.add(Math.round(output[j]));
            }
        }        
    }
    
    // result[0] is the array transformed values
    // result[1] is the array of transformed counts
    private float[][] smooth(int[] maximaValues, float[] maximaCounts) {

        MedianTransform1D mt = new MedianTransform1D();
        
        List<OneDFloatArray> outTrans = new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outCoeff = new ArrayList<OneDFloatArray>();
        
        //using pyramidal means need to rescale maximaValues too
        List<OneDFloatArray> outTransV = new ArrayList<OneDFloatArray>();
        List<OneDFloatArray> outCoeffV = new ArrayList<OneDFloatArray>();

        float[] input = maximaCounts;
        float[] inputV = new float[maximaValues.length];
        for (int i = 0; i < maximaValues.length; ++i) {
            inputV[i] = maximaValues[i];
        }
        
        while (true) {
            
            mt.multiscalePyramidalMedianTransform2(input, outTrans, outCoeff);
            mt.multiscalePyramidalMedianTransform2(inputV, outTransV, outCoeffV);

            // search for the first transformed which has only one pea in it
            for (int i = 0; i < outTrans.size(); ++i) {
                OneDFloatArray tr = outTrans.get(i);
                OneDFloatArray trV = outTransV.get(i);
                assert(tr.a.length == trV.a.length);
                
                int peakIdx = findPeakIfSingleProfile(tr.a);
                if (peakIdx > -1) {
                    float[][] a = new float[2][];
                    a[0] = trV.a;
                    a[1] = tr.a;
                    return a;
                }
            }
            
            input = outTrans.get(outTrans.size() - 1).a;
            inputV = outTransV.get(outTransV.size() - 1).a;
            assert(input.length > 3);
            outTrans.clear();
            outCoeff.clear();
            outTransV.clear();
            outCoeffV.clear();
        }
    }

    private float resampleAndSmooth(TIntIntMap valueCounts, 
        List<OneDFloatArray> outTransC, List<OneDFloatArray> outCoeffC,
        List<OneDFloatArray> outTransV, List<OneDFloatArray> outCoeffV) {

        int n = valueCounts.size();
        
        System.out.println("freq map key size=" + n);

        TIntList outV = new TIntArrayList();
        TFloatList outC = new TFloatArrayList();
        integerResampling(valueCounts, outV, outC);
        
        float[] values = new float[outC.size()];
        for (int i = 0; i < outV.size(); ++i) {
            values[i] = outV.get(i);
        }
        float[] counts = outC.toArray(new float[outC.size()]);
        
        MedianTransform1D mt = new MedianTransform1D();
        mt.multiscalePyramidalMedianTransform2(counts, outTransC, outCoeffC);
        mt.multiscalePyramidalMedianTransform2(values, outTransV, outCoeffV);

        assert(outTransC.size() == outTransV.size());
        
        if (debug) {   
            try {
                
                //DEBUG

                PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();
                for (int i = 0; i < outTransC.size(); ++i) {
                    float[] v = outTransV.get(i).a;
                    if (v.length <= 1) {
                        continue;
                    }
                    float[] c = outTransC.get(i).a;
                    assert(v.length == c.length);
                    float maxC = MiscMath0.findMax(c);
                    plotter.addPlot(0, v[v.length - 1], 0, maxC, 
                        v, c, v, c, " " + v.length);
                }
                plotter.writeFile("point_pairs_" + System.currentTimeMillis());

            } catch (IOException ex) {
                Logger.getLogger(PairwiseSeparations.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
            
        return values[values.length - 1];
        
    }

    private void integerResampling(TIntIntMap valueCounts, TIntList outV, 
        TFloatList outC) {
                
        // sort pointValueCounts by value
        int[] values = new int[valueCounts.size()];
        int[] counts = new int[values.length];
        
        TIntIntIterator iter = valueCounts.iterator();
        
        for (int i = 0; i < valueCounts.size(); ++i) {
            iter.advance();
            counts[i] = iter.value();
            // distances:
            values[i] = iter.key();
        }
        MiscSorter.sortBy1stArg(values, counts);
        
        System.out.println("freq map smallest key=" + values[0]);
        
        Interp interp = new Interp();
        float[] input = new float[2];
        
        for (int i = 0; i < values.length - 1; ++i) {
            int a0 = values[i];
            int a1 = values[i + 1];
            if (a1 == (a0 + 1)) {
                outV.add(a0);
                outC.add(counts[i]);
                continue;
            }
            input[0] = counts[i];
            input[1] = counts[i + 1];
            int n2 = a1 - a0 + 1;
            
            float[] output = interp.linearInterp(input, n2, 0, Integer.MAX_VALUE);
            
            for (int j = 0; j < n2; ++j) {
                outV.add(j + a0);
                outC.add(Math.round(output[j]));
            }
        }        
    }

    
    private int findArrayWithGTNPoints(int nP, List<OneDFloatArray> arrays) {
        for (int i = arrays.size() - 1; i > -1; --i) {
            int lenI = arrays.get(i).a.length;
            if (lenI > nP) {
                if (i > 0) {
                    return i;
                }
            }
        }
        return arrays.size() - 1;
    }

    private int findArrayWithNPoints(int nP, List<OneDFloatArray> arrays) {
        int idx = 0;
        int diff = Math.abs(arrays.get(0).a.length - nP);
        for (int i = 1; i < arrays.size(); ++i) {
            int lenI = arrays.get(i).a.length;
            int diffI = Math.abs(lenI - nP);
            if (diffI < diff) {
                idx = i;
                diff = diffI;
            }
        }
        return idx;
    }

}
