package algorithms.util;

import algorithms.misc.HistogramHolder;
import algorithms.misc.MiscMath0;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;
import java.lang.management.MemoryMXBean;
import java.lang.management.ManagementFactory;

/**
 *
 * @author nichole
 */
public class Util {
    
    /**
     *
     * @param hist
     * @param label
     * @param outputFileSuffix
     * @return
     * @throws IOException
     */
    public static String plotHistogram(HistogramHolder hist,
        String label, String outputFileSuffix) throws IOException {
                
        float[] xh = hist.getXHist();
        float[] yh = hist.getYHistFloat();
        
        float yMin = MiscMath0.findMin(yh);
        int yMaxIdx = MiscMath0.findYMaxIndex(yh);
        if (yMaxIdx == -1) {
            return null;
        }
        float yMax = yh[yMaxIdx];
        
        float xMin = MiscMath0.findMin(xh);
        float xMax = MiscMath0.findMax(xh);        
                
        PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();

        plotter.addPlot(
            xMin, xMax, yMin, yMax,
            xh, yh, xh, yh, label);

        return plotter.writeFile(outputFileSuffix);
    }
    
    /**
     *
     * @param hist
     * @param label
     * @param outputFileSuffix
     * @return
     * @throws IOException
     */
    public String plotLogHistogram(HistogramHolder hist,
        String label, String outputFileSuffix) throws IOException {
                
        float[] xh = hist.getXHist();
        float[] yh = hist.getYHistFloat();
        
        float[] yLogH = new float[yh.length];
        for (int i = 0; i < yh.length; ++i) {
            yLogH[i] = (float)Math.log(yh[i]/Math.log(10));
        }
        
        float yMin = MiscMath0.findMin(yLogH);
        int yMaxIdx = MiscMath0.findYMaxIndex(yLogH);
        float yMax = yLogH[yMaxIdx];
        
        float xMin = MiscMath0.findMin(xh);
        float xMax = MiscMath0.findMax(xh);
                        
        PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();

        plotter.addPlot(
            xMin, xMax, yMin, yMax,
            xh, yLogH, xh, yLogH, label);

        return plotter.writeFile(outputFileSuffix);
    }
     
    /**
    get the memory available to the heap as the total memory available to the JVM
    minus the amount of heap used.  
    Note that it does not know if the amount
    of memory available to the program from the OS is less than the JVM expects
    from command line flags
     * @return 
    */
    public static long getAvailableHeapMemory() {

        MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        long heapUsage = mbean.getHeapMemoryUsage().getUsed();
        long totMemory = Runtime.getRuntime().totalMemory();

        long totAvail = totMemory - heapUsage;

        return totAvail;
    }
    
    public static void writeExternal(ObjectOutput out, HistogramHolder h) 
        throws IOException {

        if (h == null || h.getXHist() == null) {
            return;
        }

        out.writeInt(h.getXHist().length);

        for (int i = 0; i < h.getXHist().length; i++) {

            out.writeFloat(h.getXHist()[i]);
            out.writeInt(h.getYHist()[i]);
            out.writeFloat(h.getYHistFloat()[i]);

            out.flush();
        }
        
        if (h.getXErrors() == null) {
            out.writeInt(0);
        } else {
            out.writeInt(h.getXErrors().length);
        
            for (int i = 0; i < h.getXErrors().length; i++) {

                out.writeFloat(h.getXErrors()[i]);
                out.writeFloat(h.getYErrors()[i]);

                out.flush();
            }
        }
    }

    public static HistogramHolder readExternal(ObjectInput in) throws IOException {

        int n = in.readInt();
        
        float[] xHist = new float[n];
        int[] yHist = new int[n];
        float[] yHistFloat = new float[n];

        for (int i = 0; i < n; i++) {
            xHist[i] = in.readFloat();
            yHist[i] = in.readInt();
            yHistFloat[i] = in.readFloat();
        }

        int ne = in.readInt();

        if (ne == 0) {
           return null;
        }

        float[] xErrors = new float[ne];
        float[] yErrors = new float[ne];

        for (int i = 0; i < ne; i++) {
            xErrors[i] = in.readFloat();
            yErrors[i] = in.readFloat();
        }
        
        HistogramHolder h = new HistogramHolder();
        h.setXHist(xHist);
        h.setYHist(yHist);
        h.setXErrors(xErrors);
        h.setYErrors(yErrors);
        h.setYHistFloat(yHistFloat);
        
        return h;
    } 
        
}
