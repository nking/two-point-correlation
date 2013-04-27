package algorithms.misc;

import algorithms.util.Errors;
import algorithms.util.PolygonAndPointPlotter;
import algorithms.util.ResourceFinder;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Logger;
import static junit.framework.Assert.assertTrue;
import junit.framework.TestCase;

/**
 * @author nichole
 */
public class HistogramTest extends TestCase {

    protected Logger log = Logger.getLogger(this.getClass().getSimpleName());

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     * Test of createHistogram method, of class Histogram.
     */
    public void testCreateHistogram_4args() {

        log.info("testCreateHistogram_4args");

        float[] aa = new float[]{1, 2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 5};
        int nBins = 5;

        float[] xHist = new float[nBins];
        int[] yHist = new int[nBins];

        Histogram.createHistogram(aa, nBins, xHist, yHist);

        for (int i = 0; i < yHist.length; i++) {
            assertTrue(yHist[i] == (i + 1));
            assertTrue(xHist[i] == (i + 1 + 0.5));
        }

        aa = new float[]{0, 1, 1, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 4};
        xHist = new float[nBins];
        yHist = new int[nBins];

        Histogram.createHistogram(aa, nBins, xHist, yHist);

        for (int i = 0; i < yHist.length; i++) {
            assertTrue(yHist[i] == (i + 1));
            assertTrue(xHist[i] == (i + 0.5));
        }

        aa = new float[]{0.1f, 0.2f, 0.2f, 0.3f, 0.3f, 0.3f, 0.4f, 0.4f, 0.4f, 0.4f, 0.5f, 0.5f, 0.5f, 0.5f, 0.5f};

        xHist = new float[nBins];
        yHist = new int[nBins];

        Histogram.createHistogram(aa, nBins, xHist, yHist);

        for (int i = 0; i < yHist.length; i++) {
            float expected = (i + 1);
            float found = yHist[i];
            assertTrue(expected == found);

            expected = (float) ((i + 1 + 0.5)*0.1f);
            found = xHist[i];
            assertTrue( Math.abs(expected - found) < 0.01);
        }

        aa = new float[]{
            100, 100, 100, 100, 100,
            200, 200, 200, 200, 200, 200,
            300, 300, 300, 300, 300, 300, 300,
            400, 400, 400, 400, 400, 400, 400, 400,
            500, 500, 500, 500, 500, 500, 500, 500, 500,
        };

        float[] aae = new float[aa.length];
        for (int i = 0; i < aa.length; i++) {
            aae[i] = 0.1f*(float)Math.sqrt(aa[i]);
        }

        xHist = new float[nBins];
        yHist = new int[nBins];

        Histogram.createHistogram(aa, nBins, xHist, yHist);
        for (int i = 0; i < yHist.length; i++) {
            float expected = (i + 5);
            float found = yHist[i];
            assertTrue(expected == found);

            expected = (float) ((i + 1 + 0.5)*100.f);
            found = xHist[i];
            assertTrue( Math.abs(expected - found) < 0.01);
        }

        float[] xHistErrorsOutput = new float[xHist.length];
        float[] yHistErrorsOutput = new float[xHist.length];

        Histogram.calulateHistogramBinErrors(xHist, yHist, aa, aae, xHistErrorsOutput, yHistErrorsOutput);
        for (int i = 0; i < yHist.length; i++) {
            float yh = yHist[i];
            float xh = xHist[i];
            float xhe = xHistErrorsOutput[i];
            float yhe = yHistErrorsOutput[i];
            //TODO:  add assert of rough expected values
            assertTrue(xhe < xh);
            assertTrue(yhe < yh);
        }

        HistogramHolder hist = Histogram.createHistogramForSkewedData(nBins, aa, aae, false);
        //assertTrue(hist.xHist.length == nBins);
    }

    /**
     * Test of createHistogram method, of class Histogram.
     */
    public void testCreateHistogram_6args() {

        log.info("testCreateHistogram_6args");

        //  2   3   4   5   6
        //    0   1   2   3   4
        float[] aa = new float[]{2, 2, 3, 3, 3, 4, 4, 4, 4, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6};
        int nBins = 5;

        float[] xHist = new float[nBins];
        int[] yHist = new int[nBins];

        Histogram.createHistogram(aa, nBins, 2, 6, xHist, yHist);

        for (int i = 0; i < yHist.length; i++) {
            assertTrue(yHist[i] == (i + 2));
            assertTrue(xHist[i] == (i + 2 + 0.5));
        }

        nBins = 4;
        xHist = new float[nBins];
        yHist = new int[nBins];
        Histogram.createHistogram(aa, nBins, 2, 5, xHist, yHist);

        for (int i = 0; i < yHist.length; i++) {
            assertTrue(yHist[i] == (i + 2));
            assertTrue(xHist[i] == (i + 2 + 0.5));
        }
    }

    /**
     * Test of createHistogram method, of class Histogram.
     */
    public void testCreateHistogram_3args() {

        log.info("testCreateHistogram_3args");

        float[] aa = new float[]{
            2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6,
            7, 7, 7, 7, 7, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 10, 10, 10, 10, 10, 11, 11, 11, 11, 11,
            12, 13, 14, 15
        };
        float[] aae = Errors.populateYErrorsBySqrt(aa);

        int nBins = 12;

        HistogramHolder result = Histogram.createHistogramForSkewedData(nBins, aa, aae, false);

        assertTrue(result.getXHist().length >= 6);
    }

    /**
     * Test of calulateHistogramBinErrors method, of class Histogram.
     */
    public void testCalulateHistogramBinErrors() {

        log.info("calulateHistogramBinErrors");

        float[] values = new float[]{
            200, 200, 200, 200, 200, 200, 200, 200, 200, 200, 200, 200, 200, 200,
            300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300, 300,
            400, 400, 400, 400, 400, 400, 400, 400, 400, 400, 400, 400, 400,
            500, 500, 500, 500, 500, 500, 500, 500, 500, 500, 500,
            600, 600, 600, 600, 600, 600, 600, 600, 600, 600,
            700, 700, 700, 700, 700, 700, 700, 700, 700,
            800, 800, 800, 800, 800, 800, 800, 800,
            900, 900, 900, 900, 900, 900, 900,
            1000, 1000, 1000, 1000, 1000,
            1100, 1100, 1100, 1100, 1100,
            1200, 1200, 1200, 1200, 1200,
            1300, 1300, 1300, 1300, 1300,
            1400, 1400, 1400, 1400, 1400,
            1500, 1500, 1500, 1500, 1500
        };
        float[] valueErrors = new float[values.length];
        for (int i = 0; i < values.length; i++) {
            valueErrors[i] = values[i]/10.0f;
        }

        HistogramHolder hist = Histogram.createHistogramForSkewedData(values.length,
            values, valueErrors, false);

        for (int i = 0; i < hist.xHist.length; i++) {
            float xh = hist.xHist[i];
            float yh = hist.yHist[i];
            float xhe = hist.xErrors[i];
            float yhe = hist.yErrors[i];
            assertTrue(xhe < xh);
            //assertTrue(yhe < yh);
        }
    }

    protected float[] a = null;
    protected float[] ae = null;
    protected int nValues = 0;

    protected void readTestFile(String fileName) throws Exception {

        String filePath = ResourceFinder.findFileInTestResources(fileName);
        FileReader reader = null;
        BufferedReader in = null;

        try {
            int count = 0;
            reader = new FileReader(new File(filePath));
            in = new BufferedReader(reader);

            String line = in.readLine();
            while (line != null) {
                if (count == 0) {
                    nValues = Integer.valueOf(line);
                    a = new float[nValues];
                    ae = new float[nValues];
                } else {
                    String[] values = line.split("\t");
                    a[count-1] = Float.valueOf(values[0]);
                    ae[count - 1] = Float.valueOf(values[1]);
                }
                line = in.readLine();
                count++;
            }
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Test of testCreateHistogram_0 method, of class Histogram.
     */
    public void testCreateHistogram_0() throws Exception {

        log.info("testCreateHistogram_0");

        PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();

        String[] files = new String[]{
            "density_dense_001.txt", "density_moderate_001.txt", "density_sparse_001.txt",
            "density_dense_002.txt", "density_moderate_002.txt", "density_sparse_002.txt",
            "density_dense_003.txt", "density_moderate_003.txt", "density_sparse_003.txt"};

        int count = 0;
        for (String fileName : files) {

            readTestFile(fileName);

            int n = 20;

            HistogramHolder hist = Histogram.createHistogramForSkewedData(n, a, ae, false);

            plotter.addPlot(hist.getXHist(), hist.getYHistFloat(), new float[n], new float[n], Integer.toString(count) + "b");
            plotter.writeFile3();

            for (int i = 0; i < hist.getXHist().length; i++) {
                float x = hist.getXHist()[i];
                float xe = hist.getXErrors()[i];
                float y = hist.getYHist()[i];
                float ye = hist.getYErrors()[i];
                int z = 1;
            }

            count++;
        }
    }

    public void testReadWriteExternal() throws Exception {

        float[] aa = new float[]{
            2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6,
            7, 7, 7, 7, 7, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 10, 10, 10, 10, 10, 11, 11, 11, 11, 11,
            12, 13, 14, 15
        };
        float[] aae = Errors.populateYErrorsBySqrt(aa);

        int nBins = 12;

        HistogramHolder histogram = Histogram.createHistogramForSkewedData(nBins, aa, aae, false);


        PipedOutputStream pipedOut = null;
        PipedInputStream pipedIn = null;

        ObjectOutputStream oos = null;

        try {

            pipedIn = new PipedInputStream(1024);
            pipedOut = new PipedOutputStream(pipedIn);


            final CountDownLatch writeLatch = new CountDownLatch(1);
            final CountDownLatch doneLatch = new CountDownLatch(1);

            Reader reader = new Reader(writeLatch, doneLatch, pipedIn);
            reader.start();

            oos = new ObjectOutputStream(pipedOut);

            histogram.writeExternal(oos);

            pipedOut.close();
            pipedOut = null;
            writeLatch.countDown();

            doneLatch.await();

            HistogramHolder rHistogram = reader.getHistogramHolder();

            assertTrue( Arrays.equals(histogram.xHist, rHistogram.xHist));

            assertTrue( Arrays.equals(histogram.yHist, rHistogram.yHist));

            assertTrue( Arrays.equals(histogram.yHistFloat, rHistogram.yHistFloat));

            assertTrue( Arrays.equals(histogram.xErrors, rHistogram.xErrors));

            assertTrue( Arrays.equals(histogram.yErrors, rHistogram.yErrors));

        } finally {

            if (oos != null) {
                oos.close();
            }
            if (pipedOut != null) {
                pipedOut.close();
            }
            if (pipedIn != null) {
                pipedIn.close();
            }
        }

    }

    private class Reader extends Thread {

        private final CountDownLatch writeLatch;
        private final CountDownLatch doneLatch;

        protected HistogramHolder histogram = null;

        protected final PipedInputStream pipedIn;

        Reader(CountDownLatch writeLatch, CountDownLatch doneLatch, PipedInputStream pipedInputStream) {
            this.writeLatch = writeLatch;
            this.doneLatch = doneLatch;
            this.pipedIn = pipedInputStream;
        }
        @Override
        public void run() {

            ObjectInputStream in = null;

            try {
                writeLatch.await();

                in = new ObjectInputStream(pipedIn);

                histogram = new HistogramHolder();

                histogram.readExternal(in);

            } catch (Exception e) {
                fail(e.getMessage());
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {}
                }
                doneLatch.countDown();
            }
        }

        public HistogramHolder getHistogramHolder() {
            return histogram;
        }
    }

}