package algorithms.compGeometry.clustering.twopointcorrelation;

import algorithms.misc.HistogramHolder;
import algorithms.util.PolygonAndPointPlotter;
import algorithms.util.ResourceFinder;
import algorithms.util.Util;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.logging.Logger;

/**
 * This persists the cluster data in files for use elsewhere.  the test
 * that generates the data is disabled when not needed.
 *
 * @author nichole
 */
public class CreateClusterDataTest extends BaseTwoPointTest {

    /**
     *
     */
    public static final String histogramFileNamePrefix = "histogram_random_background_with_";

    /**
     *
     */
    public static final String indexerFileNamePrefix = "indexer_random_background_with_";

    /**
     *
     */
    public static final String indexerSpatialDistrFileNamePrefix = "indexer_spatial_distr_with_";

    /**
     *
     */
    public static final String spatialDistrFewOutliers = "_few_outliers";

    /**
     *
     */
    public static final String spatialDistrManyOutliers = "_many_outliers";

    /**
     *
     */
    protected Logger log = Logger.getLogger(this.getClass().getSimpleName());

    /**
     *
     */
    protected boolean enable = false;

    /**
     *
     * @throws Exception
     */
    public void testCreateData() throws Exception {

        if (!enable) {
            return;
        }

        log.info("testCreateData()");

        float xmin = 0;
        float xmax = 300;
        float ymin = 0;
        float ymax = 300;

        PolygonAndPointPlotter plotter = new PolygonAndPointPlotter();

        SecureRandom srr = SecureRandom.getInstance("SHA1PRNG");
        srr.setSeed(System.currentTimeMillis());
        long seed = srr.nextLong();

        //seed = 310357278571620991l;

        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(seed);
        //sr.setSeed(4066852271294423764l);
        //sr.setSeed(310357278571620991l);
        //sr.setSeed(-1993887065899734688l);
        //sr.setSeed(-6886733535826319879l);
        //sr.setSeed(-3765842324512485314l);
        //sr.setSeed(1152752110035096347l);
        //sr.setSeed(-6221198867223436351l);
        //sr.setSeed(6899554926901724961l);

        log.info("SEED=" + seed);

        // a long running test to calculcate and print the stats of fits
        //  for sparse, moderate, and densely populated backgrounds,
        //  all with the same number of clusters and cluster points, though
        //  randomly distributed.

        int nIterPerBackground = 30;

        int m = nIterPerBackground*6;

        AxisIndexer indexer = null;

        int count = 0;

        for (int i = 0; i < 6; i++) {

            for (int ii = 0; ii < nIterPerBackground; ii++) {

                String numberOfClusters = "";
                String str = String.valueOf(count);
                while (str.length() < 3) {
                    str = "0" + str;
                }
                String fileNamePostfix = "_clusters_" + str + ".dat";

                switch(i) {
                    case 0:
                        numberOfClusters = "0";
                        indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            new int[0], 100, null);
                        break;
                    case 1:
                        numberOfClusters = "3";
                        indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            3, 30, 60, 0.1f);
                        break;
                    case 2:
                        numberOfClusters = "1";
                        indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            1, 30, 60, 1f);
                        break;
                    case 3:
                        numberOfClusters = "2";
                        indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            2, 30, 60, 1f);
                        break;
                    case 4:
                        numberOfClusters = "3";
                        indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            3, 30, 60, 1f);
                        break;
                    case 5:
                        numberOfClusters = "3";
                        indexer = createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            3, 30, 60, 10.0f);
                        break;
                    default:
                        break;
                }

                indexer.sortAndIndexX(generator.x, generator.y, generator.xErrors, generator.yErrors, generator.x.length);

                String fileName = indexerFileNamePrefix + numberOfClusters + fileNamePostfix;
                String filePath = ResourceFinder.getAFilePathInTmpData(fileName);


                writeIndexer(filePath, indexer);

                plotter.addPlot(indexer.getX(), indexer.getY(), indexer.getXErrors(), indexer.getYErrors(), null, null, String.valueOf(i));

                log.info(" " + count + " " + i + " number of clusters=" + numberOfClusters + " (" + indexer.nXY + " points) ... ");

                /*TwoPointVoidStatsExt twoPtC = new TwoPointVoidStatsExt(indexer);
                twoPtC.setDebug(true);

                float[] xf = null;
                float[] yf = null;
                HistogramHolder histogram = twoPtC.constructAndFitHistogram();
                if (twoPtC.bestFit != null) {
                    xf = twoPtC.bestFit.getOriginalScaleX();
                    yf = twoPtC.bestFit.getOriginalScaleYFit();
                }

                fileName = histogramFileNamePrefix + numberOfClusters + fileNamePostfix;
                filePath = ResourceFinder.getAFilePathInTmpData(fileName);


                writeHistogram(filePath, histogram);


                plotter.addPlot(histogram.getXHist(), histogram.getYHistFloat(),
                    histogram.getXErrors(), histogram.getYErrors(), xf, yf,
                    String.valueOf(count) + " : " + i + " : " + numberOfClusters + " => " + twoPtC.bestFit.getChiSqStatistic());*/

                //addPlot(float[] xPoints, float[] yPoints, float[] xErrPoints, float[] yErrPoints,
                //float[] xPolygon, float[] yPolygon, String plotLabel)

                plotter.writeFile();

                count++;
            }
        }

        log.info("SEED=" + seed);
    }

    static void createSpatialDistributionTestFiles() throws NoSuchAlgorithmException, IOException {

        int nIterPerBackground = 10;

        int m = nIterPerBackground*6;

        AxisIndexer indexer = null;

        int count = 0;

        // creating highly clustered datasets with few to no outliers, and datasets that have moderately dense to dense background points

        float xmin = 0;
        float xmax = 300;
        float ymin = 0;
        float ymax = 300;

        SecureRandom srr = SecureRandom.getInstance("SHA1PRNG");
        srr.setSeed(System.currentTimeMillis());
        long seed = srr.nextLong();

        seed = 310357278571620991l;

        SecureRandom sr = SecureRandom.getInstance("SHA1PRNG");
        sr.setSeed(seed);
        //sr.setSeed(4066852271294423764l);

        Logger.getLogger(CreateClusterDataTest.class.getSimpleName()).info("SEED=" + seed);

        RandomClusterAndBackgroundGenerator generator = new RandomClusterAndBackgroundGenerator();

        for (int i = 0; i < 5; i++) {

            for (int ii = 0; ii < nIterPerBackground; ii++) {

                String fileNamePostfix = "";

                switch(i) {
                    case 0:
                        // all outliers no clusters
                        fileNamePostfix = spatialDistrManyOutliers + ".dat";

                        //int[] nClusters, int nBackgroundPoints, CLUSTER_SEPARATION clusterSeparation
                        generator.createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            new int[0], 100, RandomClusterAndBackgroundGenerator.CLUSTER_SEPARATION.MODERATE);

                        break;
                    case 1:
                        // 10% of total number in clusters are added as outliers and separation is moderate
                        fileNamePostfix = spatialDistrFewOutliers + ".dat";

                        //int numberOfClusters, int minimumNumberOfPointsPerCluster, int maximumNumberOfPointsPerCluster,
                        //float backgroundPointFractionToClusters
                        generator.createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            3, 30, 60, 0.1f, RandomClusterAndBackgroundGenerator.CLUSTER_SEPARATION.MODERATE);
                        break;
                    case 2:
                        // 10% of total number in clusters are added as outliers and separation is large
                        fileNamePostfix = spatialDistrFewOutliers + ".dat";

                        generator.createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            3, 30, 60, 0.1f, RandomClusterAndBackgroundGenerator.CLUSTER_SEPARATION.LARGE);

                        break;
                    case 3:
                        // 10% of total number in clusters are added as outliers and separation is moderate
                        fileNamePostfix = spatialDistrManyOutliers + ".dat";

                        generator.createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            2, 30, 60, 1f, RandomClusterAndBackgroundGenerator.CLUSTER_SEPARATION.MODERATE);

                        break;
                    case 4:
                        // 10% of total number in clusters are added as outliers and separation is large
                        fileNamePostfix = spatialDistrManyOutliers + ".dat";

                        generator.createIndexerWithRandomPoints(sr, xmin, xmax, ymin, ymax,
                            2, 30, 60, 1f, RandomClusterAndBackgroundGenerator.CLUSTER_SEPARATION.LARGE);

                        break;
                    default:
                        break;
                }

                indexer = new AxisIndexer();
                indexer.sortAndIndexX(generator.x, generator.y, generator.xErrors, generator.yErrors, generator.x.length);

                String countStr = String.valueOf(count);
                while (countStr.length() < 3) {
                    countStr = "0" + countStr;
                }

                String fileName = indexerSpatialDistrFileNamePrefix + countStr + fileNamePostfix;
                String filePath = ResourceFinder.getAFilePathInTmpData(fileName);

                writeIndexer(filePath, indexer);

                count++;
            }
        }

        indexer = new AxisIndexer();

        // write the wikipedia data set to a file:
        indexer = CreateClusterDataTest.getWikipediaDBScanExampleData();

        String countStr = String.valueOf(count);
        while (countStr.length() < 3) {
            countStr = "0" + countStr;
        }

        String fileName = indexerSpatialDistrFileNamePrefix + countStr + spatialDistrFewOutliers + ".dat";
        String filePath = ResourceFinder.getAFilePathInTmpData(fileName);

        writeIndexer(filePath, indexer);

        count++;
    }

    /**
     *
     * @return
     * @throws IOException
     */
    public static String[] getSpatialDistributionTestFilePaths() throws IOException {

        String tmpDataDirPath = ResourceFinder.findTmpDataDirectory();

        File tmpDataDir = new File(tmpDataDirPath);

        File[] histogramFiles = tmpDataDir.listFiles(new SpatialDistrFileFilter());

        String[] filePaths = new String[histogramFiles.length];

        for (int i = 0; i < histogramFiles.length; i++) {
            filePaths[i] = histogramFiles[i].getPath();
        }

        return filePaths;
    }

    /**
     *
     * @return
     * @throws IOException
     */
    public static String[] getHistogramFilePaths() throws IOException {

        String tmpDataDirPath = ResourceFinder.findTmpDataDirectory();

        File tmpDataDir = new File(tmpDataDirPath);

        File[] histogramFiles = tmpDataDir.listFiles(new HistogramFileFilter());

        String[] filePaths = new String[histogramFiles.length];

        for (int i = 0; i < histogramFiles.length; i++) {
            filePaths[i] = histogramFiles[i].getPath();
        }

        return filePaths;
    }

    /**
     *
     * @return
     * @throws IOException
     */
    public static String[] getIndexerFilePaths() throws IOException {

        String tmpDataDirPath = ResourceFinder.findTmpDataDirectory();

        File tmpDataDir = new File(tmpDataDirPath);

        File[] indexerFiles = tmpDataDir.listFiles(new IndexerFileFilter());

        String[] filePaths = new String[indexerFiles.length];

        for (int i = 0; i < indexerFiles.length; i++) {
            filePaths[i] = indexerFiles[i].getPath();
        }

        return filePaths;
    }

    /**
     *
     */
    protected static class HistogramFileFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            if (pathname.getName().startsWith(histogramFileNamePrefix)) {
                return true;
            }
            return false;
        }
    }

    /**
     *
     */
    protected static class IndexerFileFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            if (pathname.getName().startsWith(indexerFileNamePrefix)) {
                return true;
            }
            return false;
        }
    }

    /**
     *
     */
    protected static class SpatialDistrFileFilter implements FileFilter {
        @Override
        public boolean accept(File pathname) {
            if (pathname.getName().startsWith(indexerSpatialDistrFileNamePrefix)) {
                return true;
            }
            return false;
        }
    }

    /**
     *
     * @param filePath
     * @return
     */
    public static boolean nameSuggestsBackgroundIsSparse(String filePath) {
        return (filePath.contains(spatialDistrFewOutliers));
    }

    /**
     *
     * @param filePath
     * @return
     * @throws IOException
     */
    public static AxisIndexer readIndexer(String filePath) throws IOException {
        return SerializerUtil.readPersistedPoints(filePath, true);
    }

    /**
     *
     * @param filePath
     * @return
     * @throws IOException
     */
    public static HistogramHolder readHistogram(String filePath) throws IOException {

        HistogramHolder histogram = null;

        FileInputStream fileInputStream = null;
        FilterInputStream filterInputStream = null;
        ObjectInputStream objectInputStream = null;

        try {

            fileInputStream = new FileInputStream(filePath);

            objectInputStream = new ObjectInputStream(fileInputStream);

            histogram = Util.readExternal(objectInputStream);

            return histogram;

        } finally {
            if (fileInputStream != null) {
                fileInputStream.close();
            }
            if (filterInputStream != null) {
                filterInputStream.close();
            }
            if (objectInputStream != null) {
                objectInputStream.close();
            }
        }
    }

    public static void writeIndexer(String filePath, AxisIndexer indexer) throws IOException {
        SerializerUtil.serializeIndexer(indexer, filePath);
    }

    /**
     *
     * @param filePath
     * @param histogram
     * @throws IOException
     */
    protected static void writeHistogram(String filePath, HistogramHolder histogram) throws IOException {

        FileOutputStream fileOutputStream = null;
        FilterOutputStream filterOutputStream = null;
        ObjectOutputStream objectOutputStream = null;

        try {

            fileOutputStream = new FileOutputStream(filePath);

            objectOutputStream = new ObjectOutputStream(fileOutputStream);

            Util.writeExternal(objectOutputStream, histogram);

        } finally {
            if (fileOutputStream != null) {
                fileOutputStream.close();
            }
            if (filterOutputStream != null) {
                filterOutputStream.close();
            }
            if (objectOutputStream != null) {
                objectOutputStream.close();
            }
        }
    }
    
    /**
     *
     * @param fileName
     * @return
     * @throws IOException
     */
    public static AxisIndexer getUEFClusteringDataset(String fileName) throws IOException {
        
        String dirPath = ResourceFinder.findTestResourcesDirectory();

        String sep = System.getProperty("file.separator");

        dirPath = dirPath + sep + "uEasternFinlandClustering";
        
        String filePath = dirPath + sep + fileName;
        
        BufferedReader in = null;
        FileReader reader = null;
        
        AxisIndexer indexer = new AxisIndexer();
        
        boolean hasTabs = false;
        
        try {
            File fl = new File(filePath);
            //System.out.println("looking for file=" + fl.toString() + " exists=" + fl.exists());
            reader = new FileReader(fl);
            in = new BufferedReader(reader);
            
            int nLines = 0;
            
            String line = in.readLine();
            line = line.trim();
            if (line.split("\\t") != null && line.split("\\t").length > 1) {
                hasTabs = true;
            }
            while (line != null) {
                nLines++;
                line = in.readLine();
            }
            if (reader != null) {
                reader.close();
            }
            if (in != null) {
                in.close();
            }
            
            float[] x = new float[nLines];
            float[] y = new float[x.length];
            float[] xe = new float[x.length];
            float[] ye = new float[y.length];
        
            reader = new FileReader(new File(filePath));
            in = new BufferedReader(reader);
            
            int i = 0;
            
            line = in.readLine().trim();
            
            //System.out.println("fileName=" + fileName);
            
            while (line != null) {
                
                line = line.trim();
                
                String[] items = null;
                
                if (hasTabs) {
                    items = line.split("\\t");
                } else {
                    items = line.split("\\s+");
                }
                
                if (items == null) {
                    break;
                }
                                
try {
                x[i] = Float.valueOf(items[0]).floatValue();
                y[i] = Float.valueOf(items[1]).floatValue();
                
                if (fileName.equals("a1.txt")) {
                    x[i] /= 10.f;
                    y[i] /= 10.f;
                }
} catch(Throwable t) {
    System.out.println("line=" + line + " items[0]=" + items[0] + "  items[1]=" + items[1]);
}
                xe[i] = 0.1f;
                ye[i] = 0.1f;
            
                i++;
                
                line = in.readLine();
            }
            
            indexer.sortAndIndexX(x, y, xe, ye, x.length);
            
        } finally {
            if (reader != null) {
                reader.close();
            }
            if (in != null) {
                in.close();
            }
        }
        
        return indexer;
    }

    public static AxisIndexer getWikipediaDBScanExampleData() {

        // dug these points out of http://upload.wikimedia.org/wikipedia/commons/0/05/DBSCAN-density-data.svg

        float[] x = new float[] {
            18.749945f, 18.87986f, 20.411247f, 16.524462f, 21.470438f, 18.527779f, 17.92728f, 17.873465f, 15.92131f, 20.260233f,
            15.771837f, 16.702463f, 22.127533f, 15.530968f, 17.413048f, 20.025242f, 14.753721f, 22.527637f, 15.30417f, 21.055384f,
            20.309248f, 15.040461f, 16.801958f, 21.875782f, 21.07572f, 14.64366f, 23.427776f, 17.030579f, 22.814903f, 23.729746f,
            16.487822f, 19.288128f, 16.364511f, 24.832254f, 19.022017f, 23.035681f, 14.839039f, 13.526122f, 18.657576f, 13.140135f,
            16.973349f, 25.638096f, 24.313515f, 25.655933f, 26.000902f, 17.65452f, 13.135197f, 17.619543f, 11.542064f, 18.519711f,
            11.525213f, 16.714855f, 13.877624f, 13.955661f, 15.521287f, 17.004204f, 11.980457f, 10.929038f, 10.065219f, 22.886425f,
            11.329317f, 17.32514f, 25.779974f, 13.153481f, 12.873313f, 17.899742f, 15.181395f, 25.649042f, 14.042642f, 13.833298f,
            11.568692f, 10.068832f, 12.237115f, 14.945386f, 12.562246f, 24.574707f, 11.89878f, 11.681068f, 21.980679f, 17.613543f,
            23.901054f, 10.891254f, 18.990652f, 17.690008f, 17.340229f, 15.658839f, 15.376773f, 13.448872f, 12.999125f, 20.720238f,
            24.285158f, 7.79249f, 6.4890728f, 12.860877f, 18.599541f, 14.4505f, 13.541567f, 12.652037f, 11.829375f, 17.550991f,
            5.0165715f, 14.416343f, 12.884194f, 13.518251f, 21.529566f, 10.461231f, 13.664962f, 9.4046078f, 9.8159657f, 6.50284f,
            10.495624f, 11.470833f, 9.9185486f, 11.331136f, 24.221281f, 12.003872f, 13.11056f, 17.701727f, 7.3073034f, 8.4215107f,
            7.3329473f, 6.4646049f, 5.187531f, 7.2422605f, 6.4629173f, 14.021983f, 10.131664f, 7.604115f, 7.5672803f, 9.6978426f,
            12.173581f, 6.5025353f, 6.9794717f, 10.723043f, 8.280735f, 5.1113524f, 7.6021819f, 7.7455487f, 14.851383f, 5.7411575f,
            12.101986f, 7.3279614f, 3.4531045f, 3.2710021f, 8.297081f, 7.5691047f, 4.4219213f, 7.2279086f, 2.8643413f, 10.194936f,
            10.904344f, 7.5988874f, 9.6627684f, 10.832296f, 10.474449f, 5.9095459f, 5.7485676f, 9.5218163f, 6.7359967f, 7.4746933f,
            4.7839293f, 7.2260604f, 7.5565901f, 2.8465104f, 7.0313864f, -0.78679436f, 3.1917744f, 5.425467f, 9.9491282f, 3.8844697f,
            4.6276155f, 4.6172285f, 1.8755013f, 4.3173423f, 1.1191622f, 2.9636648f, -3.4278278f, 6.649437f, 4.8449302f, 2.8595517f,
            1.9104351f, 0.44318703f, -1.534204f, 7.4510021f, 0.62392056f, 3.9007993f, 6.7646661f, 8.2116241f, 4.4051805f, 4.811914f,
            -2.4842772f, 6.4165354f, 2.296339f, -1.6347483f, 10.881722f, 7.5424252f, -0.30360937f, -2.5986171f, -0.34082291f, -4.0376272f,
            -2.3913908f, 5.4034252f, 5.6191783f, 3.2519114f, 3.5172796f, -2.125948f, -1.8779308f, 6.5516644f, 6.0859289f, 10.850755f,
            2.8316591f, 7.1445704f, -0.47462517f, -0.38431031f, 2.0151327f, -5.261271f, -7.1425705f, -7.6236806f, -4.9814978f, -3.6048574f,
            -4.0346799f, -6.1979427f, -5.5697393f, 0.14594802f, 3.0205059f, 4.1878114f, 8.2713366f, -3.0001142f, -1.3049239f, -0.54548651f,
            -2.4523211f, -6.3511853f, -6.6774416f, -0.39134982f, -4.0755248f, -6.7356811f, -5.6707964f, -2.7720299f, -4.6953793f, 1.786981f,
            0.51451367f, -11.118571f, -6.7446051f, -8.7796574f, -5.5254846f, -11.11821f, -9.7447586f, -3.8060746f, -8.0907507f, -7.8562112f,
            -5.3214025f, -1.5706922f, 0.28417328f, -5.5564032f, -5.7251158f, -6.9425106f, -7.5217018f, -8.6092339f, -4.6365209f, -9.7011051f,
            -9.5489922f, -7.3318939f, -8.4956617f, -9.9823503f, -1.372031f, -10.298453f, -10.431129f, -12.24888f, 11.028576f, -10.921333f,
            -6.5151596f, -11.746081f, -12.295823f, -22.169931f, -22.224676f, -23.198801f, -22.826401f, -20.088793f, -21.720598f, -25.058939f,
            -20.08556f, -25.30032f, -22.992655f, -25.863869f, -22.94038f, -19.389978f, -24.498081f, -23.891254f, -24.223629f, -22.83522f,
            -16.746836f, -17.729311f, -16.59285f, -27.462017f, -23.027359f, -18.963808f, -28.569824f, -28.32728f, -28.288704f, -19.782501f,
            -25.857988f, -15.557507f, -29.501484f, -23.753839f, -14.813729f, -17.825733f, -16.464754f, -28.84277f, -14.741172f, -18.907148f,
            -30.40266f, -25.624304f, -29.671732f, -17.217144f, -14.361845f, -16.617348f, -16.689478f, -14.431077f, -13.049248f, -12.908418f,
            -15.092773f, -28.122734f, -22.884155f, -31.297386f, -25.412062f, -30.840576f, -12.72188f, -21.698126f, -13.860935f, -20.495693f,
            -22.843927f, -14.965554f, -30.587751f, -13.552161f, -28.76128f, -12.530189f, -32.071049f, -23.121933f, -18.44795f, -13.705269f,
            -11.027974f, -35.096119f, -16.465176f, -28.8244f, -30.023354f, -23.178581f, -21.012383f, -22.485052f, -11.617287f, -11.237891f,
            -10.55516f, -25.532976f, -26.659966f, -28.905308f, -21.397221f, -10.050189f, -9.0290518f, -9.6726475f, -8.2725096f, -9.2031279f,
            -9.2326155f, -35.775013f, -35.49836f, -22.633123f, -28.592319f, -9.9510641f, -7.918611f, -7.2466722f, -6.9141564f, -11.174644f,
            -28.806396f, -5.99263f, -21.885376f, -0.52615768f, 31.988411f, -27.468025f, -47.269936f, 18.970428f, 47.409916f, 16.764507f,
            45.073605f, -30.957417f, 26.330013f, -21.987522f, -40.019924f, -14.228955f, -45.342865f, -44.712017f, 33.958027f, 40.452831f
        };

        float[] y = new float[] {
            -7.2887125f, -6.0346928f, -7.2943773f, -7.9101896f, -6.8355699f, -4.226912f, -4.1112642f, -10.487114f, -6.0387182f, -4.1743135f,
            -5.6430335f, -4.0919957f, -5.7977724f, -4.7574453f, -11.53997f, -11.571893f, -8.70432f, -9.4797916f, -3.8663905f, -2.6141429f,
            -12.406201f, -11.147222f, -2.0670464f, -2.707655f, -2.0637777f, -3.3901339f, -4.2628345f, -1.4666758f, -3.0119932f, -10.510004f,
            -13.274735f, -13.859127f, -13.456355f, -6.7699771f, -14.392047f, -1.7921084f, -13.343893f, -2.5354207f, -15.18538f, -12.157531f,
            0.41202229f, -9.9953556f, -1.6277194f, -3.7551036f, -9.8421621f, 1.1748607f, -13.62156f, 1.572374f, -2.7792237f, -16.588253f,
            -2.3218882f, 1.9242258f, 0.61903119f, -15.303885f, 1.6701428f, 2.2229476f, -0.38003772f, -12.764033f, -10.863215f, 2.305068f,
            -14.263559f, 3.4477563f, -14.870654f, 1.7072185f, -16.17058f, 3.9378235f, 3.2360849f, 1.0786716f, 3.2881846f, 3.182267f,
            -15.445324f, -14.170763f, -16.54722f, -17.73152f, -17.163425f, 3.6327641f, 1.9991369f, 2.2107751f, 5.3303585f, 6.1888371f,
            4.4213748f, 2.5850148f, -19.24651f, -19.472651f, -19.929773f, 6.9559469f, -21.387459f, -20.680384f, -20.750387f, -21.925047f,
            7.3062901f, -17.681284f, -15.429129f, -21.77705f, 8.6229277f, -22.59865f, -22.30851f, -22.231216f, -22.701666f, -25.918554f,
            -16.390026f, 9.8579035f, 9.1915379f, 9.9782686f, 10.124148f, 8.7867975f, 11.129749f, -22.687889f, -23.316889f, -21.014086f,
            -23.73962f, -24.476334f, 8.430337f, 10.193221f, -24.547897f, -24.806898f, 11.974107f, 12.979317f, 8.1660147f, 9.5012817f,
            -22.927746f, 8.8408661f, -21.465288f, -24.228111f, -23.94099f, 12.835505f, 11.248788f, 10.005869f, 10.732159f, -25.52804f,
            -26.620852f, -24.467426f, -24.988127f, 12.767778f, 11.682608f, -23.876587f, -26.122078f, 12.082979f, 14.582124f, 11.479012f,
            -31.155994f, -26.809269f, -25.229612f, -26.075153f, -31.751225f, -31.951805f, -29.466974f, -32.315285f, -27.462448f, 16.368002f,
            17.289595f, 13.628726f, 16.478666f, 18.048893f, 18.281094f, 12.493463f, 13.640095f, 18.67926f, 16.783533f, 17.705759f, 12.624443f,
            17.92098f, 18.936922f, 15.553617f, -32.626587f, -27.862463f, 16.601341f, 19.156858f, 22.405083f, 18.752211f, -32.864277f, -33.035328f,
            -31.818016f, -33.49968f, 17.304907f, 19.130764f, -22.569864f, -34.337559f, -34.112652f, -33.359749f, -32.774593f, -31.525198f,
            -30.551994f, 21.325146f, 18.198969f, 20.902521f, -35.748905f, 22.814049f, 21.279699f, 21.649801f, -29.821091f, -36.883343f,
            20.590248f, 18.300419f, -39.482124f, -38.339821f, -33.757153f, -31.057158f, -34.439072f, -32.581512f, -34.504848f, -41.085091f,
            -41.522106f, -41.310238f, -41.497005f, -35.722702f, -37.42609f, 23.524151f, 24.874922f, 28.128252f, 24.501165f, 28.127615f,
            23.366076f, 24.890316f, 27.282427f, 20.200127f, -32.538231f, -31.666355f, -35.517624f, -36.788181f, -36.880669f, -35.828651f,
            -36.644043f, 26.531811f, 28.940485f, 29.603748f, 30.646973f, 25.764006f, -40.434578f, -41.684494f, -39.924072f, -37.57494f,
            21.899988f, 28.874189f, -40.811131f, -37.748699f, 26.06971f, 28.284172f, 27.467716f, -45.065395f, 31.468945f, -34.235188f,
            25.818983f, 23.469088f, -41.627628f, -36.085079f, -38.556549f, -44.557568f, -41.101707f, -43.077122f, -44.667763f, -47.604465f,
            33.577927f, 29.128754f, 30.441603f, 29.85181f, 30.115021f, 28.709229f, 32.861679f, 26.373762f, 27.846214f, 31.503048f,
            30.747528f, 27.793583f, 36.692127f, 27.662888f, -45.373184f, -43.170696f, 40.102226f, 30.751806f, 35.269764f, 29.776733f,
            32.412769f, -1.8225783f, -0.6506682f, -0.87642932f, -3.458533f, -1.5777688f, 1.0652202f, -2.7320876f, 0.60463542f, -1.9749573f,
            -5.3107557f, -2.9298537f, -6.4118266f, -5.6035914f, -6.110486f, 3.2756102f, -7.0082688f, 4.0567265f, -2.0489278f, 1.8054565f,
            -1.2692223f, -4.0639081f, -8.2148571f, 4.1429257f, -2.2951632f, -3.8340383f, -3.9782491f, 4.9416819f, -8.0038271f, -4.1802239f,
            -2.9251621f, 6.3985705f, -3.799809f, -8.7510004f, 3.7639797f, -6.3479118f, -4.5638494f, -9.8281155f, -1.5129712f, -10.198759f,
            2.6688087f, -9.5279045f, -5.7087145f, 5.8070745f, 6.1467929f, -7.1458111f, -3.3746657f, -2.5745723f, -8.5144653f, 6.1626215f,
            8.5550985f, -4.4684477f, -11.685962f, 2.5101566f, -3.1992762f, -12.602847f, 3.8503811f, -12.745718f, -12.902734f, 5.9547386f,
            4.2184772f, 4.2516937f, 7.4931679f, 1.4383799f, 3.0997586f, -13.593785f, -12.753609f, 5.6743593f, 3.6742311f, 0.032113042f,
            -12.571997f, -13.297199f, -12.392374f, -16.238951f, -16.710419f, -17.311451f, -9.7373581f, -9.2511139f, -6.8979521f, 11.559745f,
            -17.211737f, -16.209808f, -17.587132f, 0.47021702f, -2.0267189f, 2.6299148f, 1.3646059f, -7.6098294f, 6.2105756f, -4.8853154f,
            -7.8747759f, -18.601803f, -17.126717f, -11.435168f, -10.499501f, -10.718688f, 2.9472184f, -16.136086f, -19.680878f, 1.4649292f,
            -23.611599f, -2.7894599f, 18.583561f, 23.31039f, -20.090324f, -49.644733f, -11.04325f, 45.74509f, 44.349609f, -42.387772f,
            24.497849f, -39.570999f, -23.722649f, 45.124107f, 48.922989f, 39.955421f, -34.082947f, -17.997713f
        };

        float[] xe = new float[x.length];
        float[] ye = new float[y.length];

        for (int i = 0; i < x.length; i++) {

            x[i] += 100;
            y[i] += 100;

            xe[i] = 0.1f;
            ye[i] = 0.1f;
        }

        AxisIndexer indexer = new AxisIndexer();
        indexer.sortAndIndexX(x, y, xe, ye, x.length);

        return indexer;
    }
}
