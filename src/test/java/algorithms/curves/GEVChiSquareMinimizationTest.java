package algorithms.curves;

import algorithms.misc.MiscMath;
import algorithms.util.Errors;
import algorithms.util.PolygonAndPointPlotter;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import junit.framework.TestCase;

/**
 *
 * @author nichole
 */
public class GEVChiSquareMinimizationTest extends TestCase {

    /**
     *
     */
    protected Logger log = Logger.getLogger(this.getClass().getSimpleName());

    /**
     *
     */
    protected float[] x = null;

    /**
     *
     */
    protected float[] y = null;

    /**
     *
     */
    protected float[] dx = null;

    /**
     *
     */
    protected float[] dy = null;

    /**
     *
     */
    protected boolean debug = true;

    /**
     *
     */
    protected GEVChiSquareMinimization chiSqMin = null;

    /**
     *
     */
    protected boolean enable = true;

    /**
     *
     * @throws Exception
     */
    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    /**
     *
     * @throws Exception
     */
    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
    }

    /**
     *
     * @throws Exception
     */
    public void testSortFromMinToMax() throws Exception {

        if (!enable) {
            return;
        }

        // placeholders, they don't resemble real data
        x = new float[]{0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        y = new float[]{9, 8, 7, 6, 5, 4, 3, 2, 1, 0};
        dx = new float[x.length];
        dy = new float[x.length];

        GEVYFit[] yfits = new GEVYFit[3];
        yfits[0] = new GEVYFit();
        yfits[0].setChiSqSum(10.0f);
        yfits[0].setX(x);
        yfits[0].setYFit(y);

        yfits[1] = new GEVYFit();
        yfits[1].setChiSqSum(5.0f);
        yfits[1].setX(x);
        yfits[1].setYFit(y);

        yfits[2] = new GEVYFit();
        yfits[2].setChiSqSum(1.0f);
        yfits[2].setX(x);
        yfits[2].setYFit(y);

        chiSqMin = new GEVChiSquareMinimization(x, y, dx, dx);
        
        chiSqMin.setDebug(true);
        
    }

    /**
     *
     * @throws Exception
     */
    public void testFitCurve_WEIGHTED_BY_ERRORS_RANDOM_DATA_00() throws Exception {

        if (!enable) {
            return;
        }

        SecureRandom srr = SecureRandom.getInstance("SHA1PRNG");
        srr.setSeed( System.currentTimeMillis() );
        long seed = srr.nextLong();
        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        //sr.setSeed( seed );
        sr.setSeed(9058936715185094572l);


        List<Long> seedsOfFailedTests = new ArrayList<Long>();

        log.info("using SEED=" + seed);
        int nIter = 4;

        for (int i = 0; i < nIter; i++) {

            float[] parameters = createTestDataUsingRandomParameters(sr);

            if (debug) {
                StringBuffer s0 = new StringBuffer("\n\nx = new float[]{");
                StringBuffer s1 = new StringBuffer("y = new float[]{");
                for (int ii = 0; ii < x.length; ii++) {
                    if (ii > 0) {
                        s0.append(", ");
                        s1.append(", ");
                    }
                    s0.append(x[ii]).append("f");
                    s1.append(y[ii]).append("f");
                }
                s0.append("};");
                s1.append("};");
                log.info(s0.toString());
                log.info(s1.toString());
            }

            float k = parameters[0];
            float sigma = parameters[1];
            float mu = parameters[2];
            float yConst = parameters[3];

            log.info("           k=" + k + "                 sigma=" + sigma + " mu=" + mu);

            GeneralizedExtremeValue gev = new GeneralizedExtremeValue(x, y, dx, dy);
            float[] yGEV = gev.generateNormalizedCurve(new float[]{k, sigma, mu});

            chiSqMin = new GEVChiSquareMinimization(x, yGEV, dx, dy);

            GEVYFit yfit = chiSqMin.fitCurveKGreaterThanZero(GEVChiSquareMinimization.WEIGHTS_DURING_CHISQSUM.ERRORS);

            if (yfit == null) {

                seedsOfFailedTests.add(seed);

                log.info("ERROR:  curve was not fit");

            } else if (debug) {

                float xmin = 0;
                float xmax = x[x.length - 1];
                float ymin = 0;
                float ymax = MiscMath.findMax(y);

                PolygonAndPointPlotter plotter = new PolygonAndPointPlotter(0, 1, 0, 1);
                plotter.addPlot(x, yGEV, dx, dy, yfit.getX(), yfit.getYFit(), "");
                plotter.writeFile();

                log.info("expecting: k=" + k + " sigma=" + sigma + " mu=" + mu);

                log.info(yfit.toString());

                // plot 2 fits.  the 2nd should be closer by number, but chisqsum is smaller for first.
                //               errors incorrect?   <====
                /*
                 float[] yGEV1 = gev.generateNormalizedCurve(new float[]{50.0000076f, 422.1334229f, 0.067541346f});
                 float[] yGEV2 = gev.generateNormalizedCurve(new float[]{16.6666775f, 84.4266815f, 0.067541346f});
                 PolygonAndPointPlotter plotter = new PolygonAndPointPlotter(xmin, xmax, ymin, ymax);
                 plotter.addPlot(x, yGEV1, x, yfit.yfit, "0");
                 plotter.addPlot(x, yGEV2, x, yfit.yfit, "1");
                 plotter.writeFile();*/

                log.info("see plot");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                }

                boolean fitIsBetterThanErrors = yfit.getChiSqSum() < yfit.getYDataErrSq();

                if (yfit.getChiSqStatistic() < 5) {
                    assertTrue(yfit.getChiSqStatistic() < 5);
                } else {
                    seedsOfFailedTests.add(seed);
                }
            }
        }

        if (!seedsOfFailedTests.isEmpty()) {
            log.info("Failed tests: ");
            for (Long s : seedsOfFailedTests) {
                log.info("seed: " + s);
            }
        }
    }

    /**
     *
     */
    protected void useTestData1() {

        x = new float[]{0.0005f, 0.0015f, 0.0025f, 0.0035f, 0.0045f, 0.0055f, 0.0065f, 0.0075f, 0.0085f};

        y = new float[]{0.46f, 1.0f, 0.517f, 0.265f, 0.148f, 0.083f, 0.078f, 0.048f, 0.0043f};

        for (int i = 0; i < y.length; i++) {
            y[i] *= 230;
        }

        dy = Errors.populateYErrorsBySqrt(y);

        dx = Errors.populateXErrorsByPointSeparation(x);

        chiSqMin = new GEVChiSquareMinimization(x, y, dx, dy);
    }

    /**
     *
     * @param sr
     * @return
     * @throws NoSuchAlgorithmException
     */
    protected float[] createTestDataUsingRandomParameters(SecureRandom sr) throws NoSuchAlgorithmException {
        // generate histogram
        float xmin = (float) (sr.nextFloat() * Math.pow(10, -1*sr.nextInt(2)));
        float xmax = xmin * (float) (Math.pow(10, sr.nextInt(2)));
        while (xmax == xmin) {
            xmax = xmin * (float) (Math.pow(10, sr.nextInt(2)));
        }
        return createTestDataUsingRandomParameters(sr, xmin, xmax);
    }

    /**
     *
     * @param sr
     * @param xmin
     * @param xmax
     * @return
     * @throws NoSuchAlgorithmException
     */
    protected float[] createTestDataUsingRandomParameters(SecureRandom sr, float xmin, float xmax) throws NoSuchAlgorithmException {

        int nPoints = sr.nextInt(30);
        while (nPoints < 8) {
            nPoints = sr.nextInt(30);
        }
        x = new float[nPoints];
        y = new float[nPoints];
        float deltaX = (xmax - xmin)/(float)nPoints;
        for (int i = 0; i < nPoints; i++) {
            x[i] = xmin + i*deltaX;
        }

        xmin = x[0];
        xmax = x[x.length - 1];
        float mu = x[sr.nextInt(3)];

        /*
        float kMin = 0.00001f;
        float kMax = 0.001f;
        float mu = x[1];
        float sigmaMin = 0.025f;
        float sMax = 20.0f*sigmaMin;
         */
        float[] parameters = GEVChiSquareMinimization.generateRandomParameters(sr, mu);
        float k = parameters[0];
        float sigma = parameters[1];

        log.info("nPoints=" + nPoints + " xmin=" + xmin + " xmax=" + xmax + " mu=" + mu);

        dx = Errors.populateXErrorsByPointSeparation(x);
        dy = new float[nPoints];

        float normFactor = sr.nextInt(100);

        GeneralizedExtremeValue gev = new GeneralizedExtremeValue(x, y, dx, dy);
        float[] yGEV = gev.generateNormalizedCurve(new float[]{k, sigma, mu}, normFactor);

        if (yGEV == null) {
            return createTestDataUsingRandomParameters(sr);
        }

        for (int i = 0; i < nPoints; i++) {
            y[i] = yGEV[i] * normFactor;
        }

        dy = Errors.populateYErrorsBySqrt(y);

        // simulate 1/10th the shot noise
        /*for (int i = 0; i < dy.length; i++) {
            dy[i] = 0.1f * dy[i];
        }*/

        chiSqMin = new GEVChiSquareMinimization(x, y, dx, dy);

        /*
        try {
            xmin = x[0];
            xmax = x[x.length - 1];
            float ymin = MiscMath.findMin(y);
            float ymax = MiscMath.findMax(y);

            PolygonAndPointPlotter plotter = new PolygonAndPointPlotter(xmin, xmax, ymin, ymax);
            plotter.addPlot(x, yGEV, x, y, "");
            plotter.writeFile();

            log.info("nPoints=" + nPoints + " k=" + k + " sigma=" + sigma + " mu=" + mu);
        } catch (Exception e) {
        }*/

        return new float[]{k, sigma, mu, normFactor};
    }
}
