package com.climbwithyourfeet.clustering;

import algorithms.connected.*;
import algorithms.misc.MinMaxPeakFinder;
import algorithms.misc.Misc0;
import algorithms.misc.MiscMath0;
import algorithms.misc.MiscSorter;
import algorithms.search.NearestNeighbor2DLong;
import algorithms.signalProcessing.Interp;
import algorithms.signalProcessing.MedianTransform1D;
import algorithms.util.OneDFloatArray;
import algorithms.util.PairInt;
import algorithms.util.PixelHelper;
import algorithms.util.PolygonAndPointPlotter;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.TFloatList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.TLongIntMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.map.hash.TLongIntHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.TLongSet;
import gnu.trove.set.hash.TIntHashSet;
import gnu.trove.set.hash.TLongHashSet;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.imageio.ImageIO;

/**
 *
 * @author nichole
 */
public class PairwiseSeparations {
    
    private boolean debug = false;
    
    private final Logger log = Logger.getLogger(this.getClass().getName());
    
    public void setToDebug() {
        debug = true;
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
        
        int n0 = pixelIdxs.size();
        n0 += (int)Math.ceil(Math.log(n0)/Math.log(2));
        
        int n1 = width * height;
        
        if (n0 < n1) {
            return extractWithNN2D(pixelIdxs, width, height);
        } else {
            return extractWithDistTrans(pixelIdxs, width, height);
        }
    }
    
    protected BackgroundSeparationHolder extractWithNN2D(TLongSet pixelIdxs, 
        int width, int height) {
        
        // randomly sample void and find NN pixelIdxs
        // then store that separation in value counts map
        //  assume the repeated random draws are almost always unique
        
        TIntIntMap valueCounts = new TIntIntHashMap();
        
        NearestNeighbor2DLong nn2d = new NearestNeighbor2DLong(pixelIdxs, 
            width, height);
        
        long len = width * height;
        
        // For random draws, considering 2 different approaches:
        // (1) random draw of integer within range width*height
        // (2) use of Low Discrepancy Sequences (requires moving the code to shared library)
        
        Random rand = Misc0.getSecureRandom();
        long seed = System.currentTimeMillis();
        System.out.println("SEED=" + seed);
        rand.setSeed(seed);
        
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        final int[] dx4 = new int[]{-1,  0, 1, 0};
        final int[] dy4 = new int[]{ 0, -1, 0, 1};
        
        int nDraws = pixelIdxs.size();
        
        long rFactor = (long)Math.ceil(Long.MAX_VALUE/len);
 
        for (int i = 0; i < nDraws; ++i) {
            long pixIdx1 = rand.nextLong()/rFactor;
            if (!pixelIdxs.contains(pixIdx1)) {
                ph.toPixelCoords(pixIdx1, width, xy);
                
                if (xy[0] == 0 || xy[1] == 0 || (xy[0] == (width - 1)) ||
                    (xy[1] == (height - 1))) {
                    continue;
                }
                
                Set<PairInt> nearest = nn2d.findClosest(xy[0], xy[1]);
                if (nearest != null && nearest.size() > 0) {
                    PairInt p2 = nearest.iterator().next();
                    int dx = xy[0] - p2.getX();
                    int dy = xy[1] - p2.getY();
                    int d = (int)Math.round(Math.sqrt(dx*dx + dy*dy));
                    
                    // check the 4 neighbors and if they are all lower
                    // (possibly == too), store this separation
                    boolean allAreLower = true;
                    
                    for (int k = 0; k < dx4.length; ++k) {
                        int x3 = xy[0] + dx4[k];
                        int y3 = xy[1] + dy4[k];
                        if (x3 < 0 || y3 < 0 || x3 >= width || y3 >= width) {
                            continue;
                        }
                        long pixIdx3 = ph.toPixelIndex(x3, y3, width);
                        if (pixelIdxs.contains(pixIdx3)) {
                            continue;
                        }
                        Set<PairInt> nearest3 = nn2d.findClosest(x3, y3);
                        if (nearest3 == null || nearest3.size() > 0) {
                            continue;
                        }
                        PairInt p3 = nearest3.iterator().next();
                        int dx3 = x3 - p3.getX();
                        int dy3 = y3 - p3.getY();
                        int d3 = (int) Math.round(Math.sqrt(dx3 * dx3 + dy3 * dy3));

                        if (d3 > d) {
                            allAreLower = false;
                            break;
                        }
                    }
                    
                    if (allAreLower) {
                        if (valueCounts.containsKey(d)) {
                            valueCounts.put(d, valueCounts.size() + 1);
                        } else {
                            valueCounts.put(d, 1);
                        }
                    }
                }
            }
        }
        
        long ts = System.currentTimeMillis();
        
        BackgroundSeparationHolder h = findBackgroundOfRandom(valueCounts, ts);
        
        return h;
    }
    
    protected BackgroundSeparationHolder extractWithDistTrans(
        TLongSet pixelIdxs, int width, int height) {
        
        // these are the non-point distances to the points        
        int[][] dt = DistanceTransformUtil.transform(pixelIdxs, width, height);
        
        for (int i = 0; i < dt.length; ++i) {
            for (int j = 0; j < dt[i].length; ++j) {
                int d = dt[i][j];
                if (d > 0) {
                    if (d > 2) {
                        dt[i][j] = (int)Math.round(Math.sqrt(d));
                    }
                }
            }
        }
        
        //printDT(dt);
        
        /*
        within dt 
           want to find the local maxima in which the surrounding points are
           all smaller in value.
        
           need to use a connected value group finder to gather the adjacent
              pixels w/ same values > 0
        
           need to make an adjacency map of those groups.
        
           then will find which groups, that is values, are the maxima.
        
           the critical separation is the smallest of the maxima, but will
               want to look at the counts of that where possible.
        
        caveats:
           single group of points in center will have no maxima found as 
               described unless there are gaps in points in the cluster.
               if there are gaps,
                  that will be found.
               else the background separation will be found to be 1
        */
               
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
            
        // need to look at amount of memory
        // to decide between these two.
        // use the later if many many objects and not enough
        // memory
        IConnectedValuesGroupFinder finder = null;
        if (false) {
            finder = new ConnectedValuesGroupFinder();
        } else {
            finder = new ConnectedValuesGroupFinder2();
        }
        finder.setMinimumNumberInCluster(1);
        TIntSet exclude = new TIntHashSet();
        exclude.add(0);
        finder.setValuesToExclude(exclude);
        List<TLongSet> valueGroups = finder.findGroups(dt);
        finder = null;
     
        System.out.println("number of connected same value distants=" +
            valueGroups.size());
        
        long ts = System.currentTimeMillis();
        
        if (debug) {
            float[] x = new float[valueGroups.size()];
            float[] y = new float[x.length];
            for (int i = 0; i < valueGroups.size(); ++i) {
                TLongSet group = valueGroups.get(i);
                long tPix = group.iterator().next();
                ph.toPixelCoords(tPix, width, xy);
                int v = dt[xy[0]][xy[1]];
                x[i] = v;
                y[i] = group.size();
            }
            float xMax = MiscMath0.findMax(x);
            float yMin = MiscMath0.findMin(y);
            float yMax = MiscMath0.findMax(y);
            PolygonAndPointPlotter plotter;
            try {
                plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0, xMax, yMin, yMax, x, y, x, y,
                    "separation");
                plotter.writeFile("_separation_" + ts);
            } catch (IOException ex) {
                Logger.getLogger(PairwiseSeparations.class.getName()).log(Level.SEVERE, null, ex);
            }
            Runtime.getRuntime().gc();
        }
        
        // key = index of values Group, value = adjacent indexes of valuesGroup
        TIntObjectMap<TIntSet> adjMap = createAdjacencyMap(
            valueGroups, width, height);

        // TODO: below here consider if aggregation of values within a tolerance
        // of similar values is needed.  The tolerance should depend upon the 
        // range of separations and on the value to be aggregated.
        //  ...such a correction can be done with kernel smoothing using 
        //     a kernel bandwidth fixed in the distance axis.
        //     if implemented with fast wavelet transforms, 
        //     the results are instead a K-Nearest Neighbors smoothing 
        //     unless the input is resampled to fixed spacing.

        // search for valueGroups which has value larger than all adj neighbors
        // key = valueGroups index, value = dt value for the group
        TIntIntMap groupMaximaIdxs = new TIntIntHashMap();
        
        // key = value, value = count
        TIntIntMap valueCounts = new TIntIntHashMap();
        
        int minMaxima = Integer.MAX_VALUE;
        
        for (int i = 0; i < valueGroups.size(); ++i) {
            
            if (!adjMap.containsKey(i)) {
                continue;
            }
            
            TLongSet group = valueGroups.get(i);
            
            long tPix = group.iterator().next();
            ph.toPixelCoords(tPix, width, xy);
            int v = dt[xy[0]][xy[1]];
            
            if (v == 0) {
                continue;
            }
            
            // if this is a boundary pixel and v > 1, skip it
            if (v > 1 && (xy[0] == 0 || xy[1] == 0 || (xy[0] == (width - 1)) ||
                (xy[1] == (height - 1)))) {
                continue;
            }
            
            boolean allAreLower = true;
            
            TIntSet adj = adjMap.get(i);
            
            TIntIterator iter2 = adj.iterator();
            while (iter2.hasNext()) {
                int aIdx = iter2.next();
                TLongSet group2 = valueGroups.get(aIdx);
                long tPix2 = group2.iterator().next();
                ph.toPixelCoords(tPix2, width, xy);
                int v2 = dt[xy[0]][xy[1]];
            
                if (v2 > v) {
                    allAreLower = false;
                    break;
                }
            }
            
            if (allAreLower) {
                groupMaximaIdxs.put(i, v);
                int c = valueCounts.get(v);
                valueCounts.put(v, c + group.size());
                
                if (v < minMaxima) {
                    minMaxima = v;
                }
            }
        }
        
        valueGroups = null;
        adjMap = null;
        
        System.out.println("number of void distance maxima=" + valueCounts.size());
        
        BackgroundSeparationHolder h = findBackgroundOfMaxima(valueCounts, ts);
        
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
    
    private TIntObjectMap<TIntSet> createAdjacencyMap(
        List<TLongSet> groupList, int width, int height) {
        
        TLongIntMap pixGroupMap = new TLongIntHashMap();
        for (int i = 0; i < groupList.size(); ++i) {
            TLongSet group = groupList.get(i);
            TLongIterator iter = group.iterator();
            while (iter.hasNext()) {
                long pixIdx = iter.next();
                assert(!pixGroupMap.containsKey(pixIdx));
                pixGroupMap.put(pixIdx, i);
            }
        }
        
        PixelHelper ph = new PixelHelper();
        int[] dx8 = new int[]{-1, -1,  0,  1, 1, 1, 0, -1};
        int[] dy8 = new int[]{ 0, -1, -1, -1, 0, 1, 1,  1};
        
        int[] xy = new int[2];
        
        int nBSLen = groupList.size();
        
        System.out.println("nBSLen=" + nBSLen);
        
        // key = index of group list, value = set of adjacent group indexes
        TIntObjectMap<TIntSet> adjMap = new TIntObjectHashMap<TIntSet>();
        
        for (int i = 0; i < groupList.size(); ++i) {
            TLongSet group = groupList.get(i);
            TLongIterator iter = group.iterator();
            TIntSet adj = adjMap.get(i);
            
            while (iter.hasNext()) {
                long pixIdx = iter.next();
                ph.toPixelCoords(pixIdx, width, xy);
                
                for (int k = 0; k < dx8.length; ++k) {
                    int vX = xy[0] + dx8[k];
                    int vY = xy[1] + dy8[k];
                    if (vX < 0 || vY < 0 || vX >= width || vY >= height) {
                        continue;
                    }
                    long pixIdx2 = ph.toPixelIndex(vX, vY, width);
                    
                    if (!pixGroupMap.containsKey(pixIdx2)) {
                        continue;
                    }
                    
                    int j = pixGroupMap.get(pixIdx2);
                    
                    if (i == j) {
                        continue;
                    }
                    
                    if (adj == null) {
                        adj = new TIntHashSet();
                        adjMap.put(i, adj);
                    }
                    adj.add(j);
                }
            }
        }
        
        return adjMap;
    }
    
    private void writeDebugImage(double[][] dt, String fileSuffix, int width, 
        int height) throws IOException {

        BufferedImage outputImage = new BufferedImage(width, height,
            BufferedImage.TYPE_BYTE_GRAY);

        WritableRaster raster = outputImage.getRaster();

        for (int i = 0; i < dt.length; ++i) {
            for (int j = 0; j < dt[0].length; ++j) {
                int v = (int)Math.round(dt[i][j]);
                raster.setSample(i, j, 0, v);
            }
        }

        // write to an output directory.  we have user.dir from system properties
        // but no other knowledge of users's directory structure
        URL baseDirURL = this.getClass().getClassLoader().getResource(".");
        String baseDir = null;
        if (baseDirURL != null) {
            baseDir = baseDirURL.getPath();
        } else {
            baseDir = System.getProperty("user.dir");
        }
        if (baseDir == null) {
            return;
        }
        File t = new File(baseDir + "/bin");
        if (t.exists()) {
            baseDir = t.getPath();
        } else if ((new File(baseDir + "/target")).exists()) {
            baseDir = baseDir + "/target";
        }

        // no longer need to use file.separator
        String outFilePath = baseDir + "/" + fileSuffix + ".png";

        ImageIO.write(outputImage, "PNG", new File(outFilePath));

        Logger.getLogger(this.getClass().getName()).info("wrote " + outFilePath);
    }
   
    private static void printDT(int[][] dt) {
        
        int w = dt.length;
        int h = dt[0].length;
        
        StringBuilder sb2 = new StringBuilder();
        for (int j = 0; j < h; ++j) {
            sb2.append("row ").append(j).append(": ");
            for (int i = 0; i < w; ++i) {
                int v = dt[i][j];
                if (v > (Integer.MAX_VALUE - 3)) {
                    sb2.append(String.format(" ---"));
                } else {
                    sb2.append(String.format(" %3d", v));
                }
            }
            sb2.append("\n");
        }
        System.out.println(sb2.toString());
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

    // result[0] is the array transformed values
    // result[1] is the array of transformed counts
    private float[][] smooth2(int[] maximaValues, float[] maximaCounts) {

        System.out.println("smooth2 input length=" + maximaValues.length);
        
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
        
        mt.multiscalePyramidalMedianTransform2(input, outTrans, outCoeff);
        mt.multiscalePyramidalMedianTransform2(inputV, outTransV, outCoeffV);

        TIntList peakIdxs = new TIntArrayList();
        float peakAvg = 0;
        
        // search for the first transformed which has only one pea in it
        for (int i = 0; i < outTrans.size(); ++i) {
            OneDFloatArray trC = outTrans.get(i);
            OneDFloatArray trV = outTransV.get(i);
            assert (trC.a.length == trV.a.length);
            
            int len = trV.a.length;
            
            int yMaxIdx = MiscMath0.findYMaxIndex(trC.a);
            
            System.out.format("len=%d  maxC=%f d=%f\n", len,
                trC.a[yMaxIdx], trV.a[yMaxIdx]);
            
            if (len >= 10 && len < 500) {
                peakAvg += trV.a[yMaxIdx];
                peakIdxs.add(i);
            } 
        }
        peakAvg /= (float)peakIdxs.size();
        System.out.println("dMin=" + peakAvg);
        if (peakIdxs.size() == 0) {
            if (outTrans.size() > 1) {
                peakIdxs.add(1);
            } else {
                peakIdxs.add(0);
            }
        }
        int dIdx = peakIdxs.get(peakIdxs.size()/2);
        float[][] a = new float[2][];
        a[0] = outTransV.get(dIdx).a;
        a[1] = outTrans.get(dIdx).a;
        return a;
    }
    
    private int averagePeak(int[] values, float[] counts) {

        float avg = 0;
        for (float c : counts) {
            avg += c;
        }
        avg /= (float)counts.length;
        
        int idx = 0;
        for (int i = 0; i < counts.length; ++i) {
            if (counts[i] < avg) {
                idx = i;
            } else if (counts[i] == avg) {
                idx = i;
                break;
            } else {
                break;
            }
        }
        return idx;
    }

    private BackgroundSeparationHolder findBackgroundOfMaxima(TIntIntMap valueCounts,
        long ts) {

        if (valueCounts.size() == 0) {
            
            // this can happen when a convex filled point set is centered
            // in the range.  there will be no maxima in the "void"
            
            BackgroundSeparationHolder h = new BackgroundSeparationHolder();
            h.setXYBackgroundSeparations(1, 1);
            h.setTheThreeSeparations(new float[]{0, 1, 2});
            h.setAndNormalizeCounts(new float[]{1, 1, 0});

            return h;
        }
        
        int[] maximaValues = new int[valueCounts.size()];
        float[] maximaCounts = new float[valueCounts.size()];
        TIntIntIterator iter = valueCounts.iterator();
        for (int i = 0; i < valueCounts.size(); ++i) {
            iter.advance();
            maximaValues[i] = iter.key();
            maximaCounts[i] = iter.value();
        }
        
        MiscSorter.sortBy1stArg(maximaValues, maximaCounts);
       
        if (debug) {
            int[] x = maximaValues;
            float[] y = maximaCounts;
            float xMax = MiscMath0.findMax(x);
            float yMin = MiscMath0.findMin(y);
            float yMax = MiscMath0.findMax(y);
            PolygonAndPointPlotter plotter;
            try {
                plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0, xMax, yMin, yMax, x, y, x, y,
                    "max sep");
            plotter.writeFile("_separation_maxima_" + ts);
            } catch (IOException ex) {
                Logger.getLogger(PairwiseSeparations.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        /*
           if the density curve of (maximaValues, maximaCounts) has more than
              one profile (peak) in it,
              need to use smoothing and choose the first convolvolution which
              produces a single profile
        
              then the best representative peak looks like the average,
              but might need to follow the else block below this
           else
              the best representative peak is found as:
                 if the peak is not the first point,
                    the average of the peak and the point before it
                 else if the peak is the first point and it is > 1,
                    half it
        */
        int peakIdx = findPeakIfSingleProfile(maximaCounts);
        
        //NOTE: this section may need revision.
        
        float reprValue, reprCounts;
        int firstZeroIdx = -1;
        if (peakIdx > -1) {
            if (peakIdx == 0) {
                if (maximaValues[0] > 1) {
                    reprValue = maximaValues[0] / 2.f;
                    reprCounts = maximaCounts[0] / 2.f;
                } else {
                    reprValue = maximaValues[0];
                    reprCounts = maximaCounts[0];
                }
                firstZeroIdx = 0;
            } else {
                assert(maximaValues.length > 1);
                reprValue = 
                    (maximaValues[peakIdx] + maximaValues[peakIdx - 1])/ 2.f;
                reprCounts = 
                    (maximaCounts[peakIdx] + maximaCounts[peakIdx - 1])/ 2.f;                 
            }
        } else {
            //kernel smoothing over evenly spaced data
            
            int n2 = maximaValues[maximaValues.length - 1] -
                maximaValues[0] + 1;
            
            System.out.println("resampling to " + n2 + " integers");
            
            TIntList outV = new TIntArrayList(n2);
            TFloatList outC = new TFloatArrayList(n2);
            integerResampling(maximaValues, maximaCounts, outV, outC);
            maximaValues = outV.toArray(new int[outV.size()]);
            maximaCounts = outC.toArray(new float[outC.size()]);
            
            float[][] smoothed = smooth(maximaValues, maximaCounts);
            
            maximaValues = new int[smoothed[0].length];
            for (int j = 0; j < maximaValues.length; ++j) {
                maximaValues[j] = Math.round(smoothed[0][j]);
            }
            maximaCounts = smoothed[1];
            
            peakIdx = averagePeak(maximaValues, maximaCounts);
            
            reprValue = maximaValues[peakIdx];
            reprCounts = maximaCounts[peakIdx];            
        }
        
        if (firstZeroIdx == -1) {
            // calculate the firstZeroIdx after peak
            float currentMinC = Float.POSITIVE_INFINITY;
            MinMaxPeakFinder finder2 = new MinMaxPeakFinder();
            float avgMin = finder2.calculateMeanOfSmallest(maximaCounts, 0.03f);
            for (int i = (peakIdx + 1); i < maximaValues.length; ++i) {
                int d = maximaValues[i];
                if (firstZeroIdx == -1) {
                    firstZeroIdx = i;
                }
                float c = maximaCounts[i];
                if (c <= avgMin) {
                    firstZeroIdx = i;
                    break;
                } else if (c < currentMinC) {
                    firstZeroIdx = i;
                    currentMinC = c;
                }
            }
            if (firstZeroIdx == -1) {
                firstZeroIdx = maximaCounts.length - 1;
            }
        }
        
        //float[] qs = MiscMath0.calcQuartiles(maximaCounts, true);
        //System.out.println("qs=" + Arrays.toString(qs));
               
        System.out.println("found background separation=" + reprValue);
        
        BackgroundSeparationHolder h = new BackgroundSeparationHolder();
                
        int m2 = (int)reprValue;
        if (m2 < 1) {
            m2 = 1;
        }
        h.setXYBackgroundSeparations(m2, m2);
        
        h.setTheThreeSeparations(new float[]{
            0, m2, 
            maximaValues[firstZeroIdx]});
        
        h.setAndNormalizeCounts(new float[]{
            reprCounts, reprCounts, 
            maximaCounts[firstZeroIdx]});
       
        return h;
    }

    private BackgroundSeparationHolder findBackgroundOfRandom(TIntIntMap valueCounts,
        long ts) {

        if (valueCounts.size() == 0) {
            
            // this can happen when a convex filled point set is centered
            // in the range.  there will be no maxima in the "void"
            
            BackgroundSeparationHolder h = new BackgroundSeparationHolder();
            h.setXYBackgroundSeparations(1, 1);
            h.setTheThreeSeparations(new float[]{0, 1, 2});
            h.setAndNormalizeCounts(new float[]{1, 1, 0});

            return h;
        }
        
        int[] maximaValues = new int[valueCounts.size()];
        float[] maximaCounts = new float[valueCounts.size()];
        TIntIntIterator iter = valueCounts.iterator();
        for (int i = 0; i < valueCounts.size(); ++i) {
            iter.advance();
            maximaValues[i] = iter.key();
            maximaCounts[i] = iter.value();
        }
        
        MiscSorter.sortBy1stArg(maximaValues, maximaCounts);
       
        if (debug) {
            int[] x = maximaValues;
            float[] y = maximaCounts;
            float xMax = MiscMath0.findMax(x);
            float yMin = MiscMath0.findMin(y);
            float yMax = MiscMath0.findMax(y);
            PolygonAndPointPlotter plotter;
            try {
                plotter = new PolygonAndPointPlotter();
                plotter.addPlot(0, xMax, yMin, yMax, x, y, x, y,
                    "max sep");
            plotter.writeFile("_separation_maxima_" + ts);
            } catch (IOException ex) {
                Logger.getLogger(PairwiseSeparations.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        
        /*
           if the density curve of (maximaValues, maximaCounts) has more than
              one profile (peak) in it,
              need to use smoothing and choose the first convolvolution which
              produces a single profile
        
              then the best representative peak looks like the average,
              but might need to follow the else block below this
           else
              the best representative peak is found as:
                 if the peak is not the first point,
                    the average of the peak and the point before it
                 else if the peak is the first point and it is > 1,
                    half it
        */
        int peakIdx = findPeakIfSingleProfile(maximaCounts);
        
        //NOTE: this section may need revision.
        
        float reprValue, reprCounts;
        int firstZeroIdx = -1;
        if (peakIdx > -1) {
            if (peakIdx == 0) {
                if (maximaValues[0] > 1) {
                    reprValue = maximaValues[0] / 2.f;
                    reprCounts = maximaCounts[0] / 2.f;
                } else {
                    reprValue = maximaValues[0];
                    reprCounts = maximaCounts[0];
                }
                firstZeroIdx = 0;
            } else {
                assert(maximaValues.length > 1);
                reprValue = 
                    (maximaValues[peakIdx] + maximaValues[peakIdx - 1])/ 2.f;
                reprCounts = 
                    (maximaCounts[peakIdx] + maximaCounts[peakIdx - 1])/ 2.f;                 
            }
        } else {
            //kernel smoothing over evenly spaced data
            
            int n2 = maximaValues[maximaValues.length - 1] -
                maximaValues[0] + 1;
            
            System.out.println("resampling to " + n2 + " integers");
            
            TIntList outV = new TIntArrayList(n2);
            TFloatList outC = new TFloatArrayList(n2);
            integerResampling(maximaValues, maximaCounts, outV, outC);
            maximaValues = outV.toArray(new int[outV.size()]);
            maximaCounts = outC.toArray(new float[outC.size()]);
            
            float[][] smoothed = smooth2(maximaValues, maximaCounts);
            
            maximaValues = new int[smoothed[0].length];
            for (int j = 0; j < maximaValues.length; ++j) {
                maximaValues[j] = Math.round(smoothed[0][j]);
            }
            maximaCounts = smoothed[1];
            
            peakIdx = averagePeak(maximaValues, maximaCounts);
            
            reprValue = maximaValues[peakIdx];
            reprCounts = maximaCounts[peakIdx];            
        }
        
        if (firstZeroIdx == -1) {
            // calculate the firstZeroIdx after peak
            float currentMinC = Float.POSITIVE_INFINITY;
            MinMaxPeakFinder finder2 = new MinMaxPeakFinder();
            float avgMin = finder2.calculateMeanOfSmallest(maximaCounts, 0.03f);
            for (int i = (peakIdx + 1); i < maximaValues.length; ++i) {
                int d = maximaValues[i];
                if (firstZeroIdx == -1) {
                    firstZeroIdx = i;
                }
                float c = maximaCounts[i];
                if (c <= avgMin) {
                    firstZeroIdx = i;
                    break;
                } else if (c < currentMinC) {
                    firstZeroIdx = i;
                    currentMinC = c;
                }
            }
            if (firstZeroIdx == -1) {
                firstZeroIdx = maximaCounts.length - 1;
            }
        }
        
        //float[] qs = MiscMath0.calcQuartiles(maximaCounts, true);
        //System.out.println("qs=" + Arrays.toString(qs));
               
        System.out.println("found background separation=" + reprValue);
        
        BackgroundSeparationHolder h = new BackgroundSeparationHolder();
                
        int m2 = (int)reprValue;
        if (m2 < 1) {
            m2 = 1;
        }
        h.setXYBackgroundSeparations(m2, m2);
        
        h.setTheThreeSeparations(new float[]{
            0, m2, 
            maximaValues[firstZeroIdx]});
        
        h.setAndNormalizeCounts(new float[]{
            reprCounts, reprCounts, 
            maximaCounts[firstZeroIdx]});
       
        return h;
    }
}
