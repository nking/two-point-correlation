package algorithms.compGeometry.clustering.twopointcorrelation;

import algorithms.compGeometry.LinesAndAngles;
import algorithms.compGeometry.convexHull.GrahamScanTooFewPointsException;
import algorithms.compGeometry.convexHull.GrahamScan;
import algorithms.curves.GEVYFit;
import algorithms.misc.MiscMath;
import algorithms.util.Errors;
import algorithms.util.PolygonAndPointPlotter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.Arrays;
import java.util.logging.Logger;

/**
  Find clusters in data by looking for regions whose density is
      2 or 3 times the background density (that is 2 or 3 sigma above 'error').
      The default is 3.

  The background density can be determined with the following methods:
      FIT_TWO_POINT_VOIDS -
         clusterFinder.calculateBackgroundVia2PtVoidFit(false);
         returns an estimate of the background density
         by calculating the density of rectangles holding only 2 points,
         fitting a GEV curve to that distribution, and returning the point
         at which 10% of the total area under the curve has occurred.
      OR the background density can be set manually:
          setBackground(float backgroundSurfaceDensity, float standardDeviationOfBackground);

  Usage as an API:
      TwoPointCorrelation clusterFinder = new TwoPointCorrelation(x, y, xErrors, yErrors, getTotalNumberOfPoints());
      clusterFinder.calculateBackground();
      clusterFinder.findClusters();
      clusterFinder.calculateHullsOfClusters();
      String plotFilePath = clusterFinder.plotClusters();


      TwoPointCorrelation clusterFinder = new TwoPointCorrelation(x, y, xErrors, yErrors, getTotalNumberOfPoints());
      setBackground(0.03f, 0.003f);
      clusterFinder.findClusters();
      clusterFinder.calculateHullsOfClusters();
      String plotFilePath = clusterFinder.plotClusters();

  Note:  For datasets in which the density of background points is high, if you don't
     have the ability to reduce the data by a key characteristic, you might consider
     the results of this code as seeds for a Voronoi diagram or other.
     float[] xSeeds = clusterFinder.getXHullCentroids();
     float[] ySeeds = clusterFinder.getYHullCentroids();

  Usage from the command line:
      Requires a tab delimited text file with 4 columns: x, y, xErrors, yErrors.

          java -cp bin/classes  algorithms.compGeometry.clustering.twopointcorrelation.TwoPointCorrelation --file /path/to/file/fileName.txt

  @author nichole
 */
public class TwoPointCorrelation {

    protected enum STATE {
        INITIALIZED, BACKGROUND_SET, CLUSTERS_FOUND, CLUSTER_HULLS_CALCULATED
    }

    protected enum BACKGROUND_METHOD {
        FIT_TWO_POINT_VOIDS, USER_SUPPLIED, DESERIALIZED
    }

    protected final DoubleAxisIndexer indexer;

    private float backgroundSurfaceDensity;
    private float backgroundError;
    private float sigmaFactor = 2.5f;
    // we are looking for points which have surface density > sigmaFactor*backgroundAverage

    protected int minimumNumberInCluster = 10;

    protected STATE state = null;
    protected BACKGROUND_METHOD bMethod = null;

    /**
     * an array to hold each group as an item.  each item contains keys which hold the <xy>Point index.
     */
    protected SimpleLinkedListNode[] groupMembership = null;

    protected int nGroups = 0;

    /*
     * array holding indexes for a point to the group it belongs to.
     * note that the point index is relative to indexer.getXSortedByY
     */
    protected int[] pointToGroupIndex = null;

    /**
     * for groups already calculated in groupMembership, this is how to find the points
     * of the convex hull for each group.
     * For example, to find the convex hull of group # 0
     *    groupHullIndexes[0] will return a linked list to the indexes of x and y
     *    that form the hull for that group.
     */
    protected SimpleLinkedListNode[] groupHullIndexes = null;

    /**
     * centroid coordinates of the hulls for the groups.  note that the centroids
     * are not derived from all points in the group, only from the hull polygon.
     */
    protected float[] xGroupHullCentroids = null;
    protected float[] yGroupHullCentroids = null;
    protected float[] groupHullSurfaceAreas = null;

    protected boolean persistTheMinimaStats = false;
    protected String indexerFilePath = null;
    protected String minimaStatsFilePath = null;

    // for debugging plots, keeping a handle on TwoPointVoidStats.
    public IPointBackgroundStats backgroundStats = null;

    protected boolean debug = false;

    protected boolean doLogPerformanceMetrics = false;

    protected Logger log = Logger.getLogger(this.getClass().getName());

    /**
     * construct without errors on xPoints and yPoints.  Note that internally,
     * RMS errors, that is 'shot noise' is used which may be larger than your
     * true errors.
     *
     * @param xPoints
     * @param yPoints
     * @param nXYPoints
     */
    public TwoPointCorrelation(float[] xPoints, float[] yPoints, int nXYPoints) {

        this.indexer = new DoubleAxisIndexer();

        float[] xPointErrors = Errors.populateYErrorsBySqrt(xPoints);
        float[] yPointErrors = Errors.populateYErrorsBySqrt(yPoints);

        indexer.sortAndIndexXThenY(xPoints, yPoints, xPointErrors, yPointErrors, nXYPoints);

        initializeClusterVariables();

        state = STATE.INITIALIZED;
    }

    private void initializeClusterVariables() {

        nGroups = 0;

        pointToGroupIndex = new int[indexer.getNumberOfPoints()];
        Arrays.fill(pointToGroupIndex, -1);

        groupMembership = new SimpleLinkedListNode[10];

        for (int i = 0; i < groupMembership.length; i++) {
            groupMembership[i] = new SimpleLinkedListNode();
        }
    }

    public TwoPointCorrelation(float[] xPoints, float[] yPoints, float[] xPointErrors, float[] yPointErrors, int nXYPoints) {

        this.indexer = new DoubleAxisIndexer();

        indexer.sortAndIndexXThenY(xPoints, yPoints, xPointErrors, yPointErrors, nXYPoints);

        pointToGroupIndex = new int[xPoints.length];
        Arrays.fill(pointToGroupIndex, -1);

        groupMembership = new SimpleLinkedListNode[10];
        for (int i = 0; i < groupMembership.length; i++) {
            groupMembership[i] = new SimpleLinkedListNode();
        }

        state = STATE.INITIALIZED;
    }

    public TwoPointCorrelation(String indexerFilePath) throws IOException {

        this.indexer = SerializerUtil.readPersistedPoints(indexerFilePath, true);

        pointToGroupIndex = new int[this.indexer.nXY];
        Arrays.fill(pointToGroupIndex, -1);

        groupMembership = new SimpleLinkedListNode[10];
        for (int i = 0; i < groupMembership.length; i++) {
            groupMembership[i] = new SimpleLinkedListNode();
        }

        state = STATE.INITIALIZED;
    }

    public TwoPointCorrelation(DoubleAxisIndexer doubleAxisIndexer) throws IOException {

        this.indexer = doubleAxisIndexer;

        pointToGroupIndex = new int[this.indexer.nXY];
        Arrays.fill(pointToGroupIndex, -1);

        groupMembership = new SimpleLinkedListNode[10];
        for (int i = 0; i < groupMembership.length; i++) {
            groupMembership[i] = new SimpleLinkedListNode();
        }

        state = STATE.INITIALIZED;
    }

    public void setSigmaFactorToTwo() {
        if (debug) {
            log.info("threshhold=2.0");
        }
        sigmaFactor = 2.0f;
    }
    public void setSigmaFactorToTwoPointFive() {
        if (debug) {
            log.info("threshhold=2.5");
        }
        sigmaFactor = 2.5f;
    }
    public void setSigmaFactorToThree() {
        if (debug) {
            log.info("threshhold=3.0");
        }
        sigmaFactor = 3.0f;
    }

    public void setDebug(boolean turnDebugOn) {
        this.debug = turnDebugOn;
    }

    protected void logPerformanceMetrics() {
        this.doLogPerformanceMetrics = true;
    }

    public void persistIndexer(boolean doPersistIndexer) throws IOException {
        indexerFilePath = SerializerUtil.serializeIndexer(indexer);
    }

    public void setPersistMinimaStats(boolean doPersistMinimaStats) {
        persistTheMinimaStats = doPersistMinimaStats;
    }

    public void setMinimumNumberInCluster(int minimumNumberForClusterMembership) {
        this.minimumNumberInCluster = minimumNumberForClusterMembership;
    }

    public void setBackground(float backgroundSurfaceDensity, float standardDeviationOfBackground) {

        this.backgroundSurfaceDensity = backgroundSurfaceDensity;

        this.backgroundError = standardDeviationOfBackground;

        state = STATE.BACKGROUND_SET;

        bMethod = BACKGROUND_METHOD.USER_SUPPLIED;
    }

    /**
     * calculate background using complete sampling.  warning, this method takes longer than all other methods.
     *
     * @throws TwoPointVoidStatsException
     * @throws IOException
     */
    public void calculateBackgroundUsingCompleteSampling() throws TwoPointVoidStatsException, IOException {

        if ((bMethod == null) || (bMethod.ordinal() != BACKGROUND_METHOD.USER_SUPPLIED.ordinal())) {

            boolean useCompleteBackgroundSampling = true;

            calculateBackgroundVia2PtVoidFit(useCompleteBackgroundSampling);
        }
    }

    /**
     * calculate background using the default method.
     *
     * @see TwoPointVoidStats.calc()
     *
     * @throws TwoPointVoidStatsException
     * @throws IOException
     */
    public void calculateBackground() throws TwoPointVoidStatsException, IOException {

        if ((bMethod == null) || (bMethod.ordinal() != BACKGROUND_METHOD.USER_SUPPLIED.ordinal())) {

            boolean useCompleteBackgroundSampling = false;

            calculateBackgroundVia2PtVoidFit(useCompleteBackgroundSampling);
        }
    }

    public void reuseStatsForBackgroundCalculation(String minimaFilePath) throws TwoPointVoidStatsException, IOException {

        TwoPointVoidStats minStats = new TwoPointVoidStats(indexer);
        minStats.setDebug(debug);
        minStats.calc(minimaFilePath);

        if (debug) {
            backgroundStats = minStats;
        }

        this.backgroundSurfaceDensity = minStats.getBackgroundSurfaceDensity();
        this.backgroundError = minStats.getBackgroundSurfaceDensityError();

        if (debug) {
            log.info("background surface density ="
                + this.backgroundSurfaceDensity + " with error =" + this.backgroundError);
        }

        state = STATE.BACKGROUND_SET;

        bMethod = BACKGROUND_METHOD.DESERIALIZED;
    }

    protected void calculateBackgroundVia2PtVoidFit(Boolean useCompleteBackgroundSampling) throws TwoPointVoidStatsException, IOException {

        long startTimeMillis = System.currentTimeMillis();

        if ((bMethod != null) && (bMethod.ordinal() == BACKGROUND_METHOD.USER_SUPPLIED.ordinal())) {
            return;
        }

        TwoPointVoidStats voidStats = new TwoPointVoidStats(indexer);
        voidStats.setDebug(debug);

        if (doLogPerformanceMetrics) {
            voidStats.logPerformanceMetrics();
        }

        if ((useCompleteBackgroundSampling != null) && useCompleteBackgroundSampling) {
            voidStats.setUseCompleteSampling();
        }

        voidStats.calc();

        //if (debug) {
            backgroundStats = voidStats;
        //}

        if (persistTheMinimaStats) {
            minimaStatsFilePath = voidStats.persistTwoPointBackground();
        }

        this.backgroundSurfaceDensity = voidStats.getBackgroundSurfaceDensity();
        this.backgroundError = voidStats.getBackgroundSurfaceDensityError();

        if (debug) {
            log.info("==>background surface density ="
                + this.backgroundSurfaceDensity + " with error =" + this.backgroundError);
        }

        state = STATE.BACKGROUND_SET;

        bMethod = BACKGROUND_METHOD.FIT_TWO_POINT_VOIDS;

        if (doLogPerformanceMetrics) {

            long stopTimeMillis = System.currentTimeMillis();

            printPerformanceMetrics(startTimeMillis, stopTimeMillis,
                "calculateBackgroundVia2PtVoidFit");
        }
    }


    protected void printPerformanceMetrics(long startTimeMillis, long stopTimeMillis, String methodName) {

        long diffSec = (stopTimeMillis - startTimeMillis)/1000;

        MemoryMXBean mbean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = mbean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = mbean.getNonHeapMemoryUsage();

        String str = String.format("%35s:  N=%9d  RT(sec)=%8d  instance estimates(bytes)=%9d   heapUsed(bytes)=%9d   memoryPoolsSum(bytes)=%9d",
            methodName,
            indexer.nXY, diffSec, approximateMemoryUsed(),
            heapUsage.getUsed(), nonHeapUsage.getUsed() );

        Logger.getLogger(this.getClass().getSimpleName()).info(str);
    }

    public long approximateMemoryUsed() {

        long sumBytes = 16 + 16 + indexer.approximateMemoryUsed() + (2*8);

        long sumBits = (5*32) + (2*2);

        if (groupMembership != null) {
            sumBytes += (16 + (groupMembership.length*(8+4)));
        }
        if (groupHullIndexes != null) {
            sumBytes += (16 + (groupHullIndexes.length*(8+4)));
        }
        if (xGroupHullCentroids != null) {
            sumBytes += 3*(16 + (xGroupHullCentroids.length*4));
        }
        if (pointToGroupIndex != null) {
            sumBytes += (16 + (pointToGroupIndex.length*4));
        }
        if (backgroundStats != null) {
            sumBytes += backgroundStats.approximateMemoryUsed();
        }
        if (indexerFilePath != null) {
            sumBytes += (16 + (indexerFilePath.length()*16));
        }
        if (minimaStatsFilePath != null) {
            sumBytes += (16 + (minimaStatsFilePath.length()*16));
        }

        sumBytes += (sumBits/8);

        // amount of padding needed to make it a round 8 bytes
        long padding = (sumBytes % 8);

        sumBytes += padding;

        return sumBytes;
    }

    public void findClusters() throws TwoPointVoidStatsException, IOException {

        if (state.ordinal() < STATE.BACKGROUND_SET.ordinal()) {
            calculateBackgroundVia2PtVoidFit(false);
        }

        bruteForceCalculateGroups();
    }

    public String plotClusters(float xMin, float xMax, float yMin, float yMax)
        throws FileNotFoundException, IOException, TwoPointVoidStatsException {

        if (state.ordinal() < STATE.CLUSTER_HULLS_CALCULATED.ordinal()) {
            calculateHullsOfClusters();
        }

        TwoPointCorrelationPlotter plotter = new TwoPointCorrelationPlotter(xMin, xMax, yMin, yMax);
        plotter.addPlot(this);

        return plotter.writeFile();
    }

    public String plotClusters() throws FileNotFoundException, IOException, TwoPointVoidStatsException {

        if (state.ordinal() < STATE.CLUSTER_HULLS_CALCULATED.ordinal()) {
            calculateHullsOfClusters();
        }

        float[] xMinMax = MiscMath.calculateOuterRoundedMinAndMax(indexer.getX());
        float[] yMinMax = MiscMath.calculateOuterRoundedMinAndMax(indexer.getY());

        TwoPointCorrelationPlotter plotter = new TwoPointCorrelationPlotter(xMinMax[0], xMinMax[1], yMinMax[0], yMinMax[1]);
        String label = "";
        if ((this.backgroundStats != null) && (this.backgroundStats instanceof TwoPointVoidStats)) {
            GEVYFit bestFit = ((TwoPointVoidStats)this.backgroundStats).bestFit;
            if (bestFit != null) {
                label = String.format("k=%.7f sigma=%.7f mu=%.7f", bestFit.getK(), bestFit.getSigma(), bestFit.getMu());
            }
        }
        plotter.addPlot(this, label);

        return plotter.writeFile3();
    }

    public void bruteForceCalculateGroups() {

        long startTimeMillis = System.currentTimeMillis();

        // use temporary variables first to prune groups with less than 3 members

        SimpleLinkedListNode[] tmpGroupMembership = new SimpleLinkedListNode[10];
        int nTmpGroups = 0;
        for (int i = 0; i < tmpGroupMembership.length; i++) {
            tmpGroupMembership[i] = new SimpleLinkedListNode();
        }

        int[] tmpPointToGroupIndex = new int[indexer.nXY];
        Arrays.fill(tmpPointToGroupIndex, -1);

        // the indexes stored in the instance vars such as pointToGroupIndex are w.r.t. the arrays sorted by y
        float[] x = indexer.getXSortedByY();
        float[] y = indexer.getYSortedByY();

        float densityThreshold = sigmaFactor*backgroundSurfaceDensity;
        // 2 or 3 times the background surface density
        if (debug) {
            log.info("For clusters, using densityThreshold=" + densityThreshold);
        }

        SimpleLinkedListNode tmpPointsInMoreThanOneGroup = new SimpleLinkedListNode();

        // store the distances between points when the separation implies it is above surface density
        for (int i = 0; i < indexer.nXY; i++) {

            // groups are points whose separation is an implied surface density higher than 2 or 3 times the background.
            // see if this point is part of a group already, by looking for it in the pointTo2ndPointIndex entry.
            // if it is a part of a group already, set the groupNumber,
            // else, leave the groupNumber as -1 until another point is close enough to start a new group.

            // this will be -1 if not yet assigned
            int groupNumber = tmpPointToGroupIndex[i];

            for (int j = (i + 1); j < indexer.nXY; j++) {

                float d = (float) Math.sqrt( Math.pow((x[i] - x[j]), 2) + Math.pow((y[i] - y[j]), 2) );

                float dens = 2.f/d;

                if (dens > densityThreshold) {

                    if (tmpGroupMembership.length < (nTmpGroups + 1)) {
                        int oldN = tmpGroupMembership.length;
                        int n = (int) (1.5f * oldN);
                        if (n < (oldN + 1)) {
                            n = oldN + 1;
                        }
                        tmpGroupMembership = Arrays.copyOf(tmpGroupMembership, n);
                        for (int k = oldN; k < n; k++) {
                            tmpGroupMembership[k] = new SimpleLinkedListNode();
                        }
                    }

                    if (groupNumber == -1) {

                        if (tmpPointToGroupIndex[j] != -1) {

                            groupNumber = tmpPointToGroupIndex[j];

                        } else {

                            groupNumber = nTmpGroups;
                        }
                        tmpGroupMembership[groupNumber].insert(i);
                        tmpPointToGroupIndex[i] = groupNumber;
                        nTmpGroups++;

                    }// else, is already a member of a group

                    if ( (tmpPointToGroupIndex[j] != -1) && ( tmpPointToGroupIndex[j] != groupNumber ) ) {

                        tmpPointsInMoreThanOneGroup.insertIfDoesNotAlreadyExist(j);
                    }

                    tmpGroupMembership[groupNumber].insertIfDoesNotAlreadyExist(j);

                    tmpPointToGroupIndex[j] = groupNumber;
                }
            }
        }

        // consolidate overlapping groups.  high surface density backgrounds will have larger number of overlapping clusters
        SimpleLinkedListNode latest = tmpPointsInMoreThanOneGroup;
        while (latest != null && latest.key != -1) {

            int pointIndex = latest.key;

            int gn1 = tmpPointToGroupIndex[pointIndex];

            for (int i = 0; i < nTmpGroups; i++) {

                if (i == gn1) {
                    continue;
                }

                SimpleLinkedListNode grNode = tmpGroupMembership[i].search(pointIndex);

                if (grNode != null) {

                    int n1 = tmpGroupMembership[gn1].getNumberOfKeys();
                    int n2 = tmpGroupMembership[i].getNumberOfKeys();

                    if (n1 > n2) {
                        //put all of group 2 points into group 1
                        SimpleLinkedListNode group2 = tmpGroupMembership[i];
                        while ( (group2 != null) && (group2.key != -1) ) {

                            int aPointInG2 = group2.key;

                            tmpPointToGroupIndex[aPointInG2] = gn1;

                            tmpGroupMembership[gn1].insertIfDoesNotAlreadyExist(aPointInG2);

                            tmpGroupMembership[i].delete(aPointInG2);

                            //group2 = group2.next;
                            //instead of using next, start from top again due to delete while in iteration
                            group2 = tmpGroupMembership[i];
                        }
                    } else {
                        //put all of group 1 points into group 2
                        SimpleLinkedListNode group1 = tmpGroupMembership[gn1];
                        while ( (group1 != null) && (group1.key != -1) ) {

                            int aPointInG1 = group1.key;

                            tmpPointToGroupIndex[aPointInG1] = i;

                            tmpGroupMembership[i].insertIfDoesNotAlreadyExist(aPointInG1);

                            tmpGroupMembership[gn1].delete(aPointInG1);

                            //group2 = group2.next;
                            //instead of using next, start from top again due to delete while in iteration
                            group1 = tmpGroupMembership[gn1];
                        }
                    }

                    i = nTmpGroups;
                }
            }
            tmpPointsInMoreThanOneGroup.delete(pointIndex);
            latest = tmpPointsInMoreThanOneGroup;
        }

        // iterate over group data and store in instance vars when group membership is > 2
        for (int i = 0; i < nTmpGroups; i++) {

            int currentTmpGroupNumber = i;

            SimpleLinkedListNode grpMembersNode = tmpGroupMembership[currentTmpGroupNumber];

            int nGrpMembers = grpMembersNode.getNumberOfKeys();

            if (nGrpMembers >= minimumNumberInCluster) {

                // store these associated with current group number which is nGroups

                int groupNumber = nGroups;
                nGroups++;

                while ( (grpMembersNode != null) && (grpMembersNode.key != -1) ) {

                    int pointIndex = grpMembersNode.key;

                    checkAndExpandGroupMembershipArray();

                    pointToGroupIndex[pointIndex] = groupNumber;

                    groupMembership[groupNumber].insertIfDoesNotAlreadyExist(pointIndex);

                    grpMembersNode = grpMembersNode.next;
                }
            }
        }

        state = STATE.CLUSTERS_FOUND;

        if (doLogPerformanceMetrics) {

            long stopTimeMillis = System.currentTimeMillis();

            printPerformanceMetrics(startTimeMillis, stopTimeMillis, "bruteForceCalculateGroups");
        }

        try {
            temporaryWorkaroundForSampling();
        } catch (IOException e) {
            log.severe(e.getMessage());
        } catch (TwoPointVoidStatsException e) {
            log.severe(e.getMessage());
        }
    }

    protected float calculateFractionOfAreaOutsideOfClusters() throws IOException, TwoPointVoidStatsException {

        if (state.ordinal() < STATE.CLUSTER_HULLS_CALCULATED.ordinal()) {
            calculateHullsOfClusters();
        }

        // ignore overlapping clusters at this point:
        float groupAreaSum = 0;

        for (int i = 0; i < nGroups; i++) {
            groupAreaSum += groupHullSurfaceAreas[i];
        }

        //xmin, xmax, ymin, ymax
        float[] xyMinMax = this.indexer.findXYMinMax();
        float dataPointsArea = (xyMinMax[3] - xyMinMax[2])*(xyMinMax[1] - xyMinMax[0]);

        float frac = (dataPointsArea - groupAreaSum)/dataPointsArea;

        return frac;
    }

    protected float calculateFractionOfPointsOutsideOfClusters() {

        boolean[] insideClusters = new boolean[indexer.nXY];

        for (int i = 0; i < nGroups; i++) {

            SimpleLinkedListNode groupNode = groupMembership[i];

            while ((groupNode != null) && (groupNode.key != -1)) {

                int pointIndex = groupNode.key;

                insideClusters[pointIndex] = true;

                groupNode = groupNode.next;
            }
        }

        // count number outside
        int count = 0;
        for (int i = 0; i < insideClusters.length; i++) {
            if (!insideClusters[i]) {
                count++;
            }
        }

        float frac = (float)count/(float)indexer.nXY;

        return frac;
    }

    protected void temporaryWorkaroundForSampling() throws TwoPointVoidStatsException, IOException {

        // This is a temporary work around to account for not being able to distinguish yet for the default case
        //   of sampling.  If we used TwoPointVoidStats.Sampling.SEMI_COMPLETE,
        //   and if there are many points are outliers or all points are in one cluster
        //   we have to redo-the analysis with TwoPointVoidStats.Sampling.SEMI_COMPLETE_RANGE_SEARCH

        if (backgroundStats == null) {
            return;
        }

        if (((TwoPointVoidStats)backgroundStats).getSampling().ordinal() == TwoPointVoidStats.Sampling.SEMI_COMPLETE.ordinal()) {

            // this is very roughly trying to determine whether there are many
            //   outliers, and if so, we should use the range search because
            //   it samples between the clusters better.
            // Caveat is should not reduce the sampling to 'range search' if there are few points already.

            int nDensities = ((TwoPointVoidStats)backgroundStats).nTwoPointSurfaceDensities;

            //if (nDensities < 1001) {
            //    return;
            //}

            float fracPoints = calculateFractionOfPointsOutsideOfClusters();

            float fracArea = calculateFractionOfAreaOutsideOfClusters();

            int n = nGroups;

            if (fracPoints <= 0.11f) {
                if (fracArea > 0.33f) {
                    log.info("KEEP " + TwoPointVoidStats.Sampling.SEMI_COMPLETE.name()
                        + " fracPoints=" + fracPoints + " fracArea=" + fracArea + " nGroups=" + n);
                    return;
                }
            }
            log.info("CHANGE TO " + TwoPointVoidStats.Sampling.SEMI_COMPLETE_RANGE_SEARCH.name()
                + " fracPoints=" + fracPoints + " fracArea=" + fracArea + " nGroups=" + n);

            if (debug) {
                plotClusters();
            }

            TwoPointVoidStats voidStats = new TwoPointVoidStats(indexer);
            voidStats.setDebug(debug);

            if (doLogPerformanceMetrics) {
                voidStats.logPerformanceMetrics();
            }

            voidStats.setUseSemiCompleteRangeSampling();

            voidStats.calc();

            backgroundStats = voidStats;

            if (persistTheMinimaStats) {
                minimaStatsFilePath = voidStats.persistTwoPointBackground();
            }

            this.backgroundSurfaceDensity = voidStats.getBackgroundSurfaceDensity();
            this.backgroundError = voidStats.getBackgroundSurfaceDensityError();

            if (debug) {
                log.info("==>background surface density ="
                    + this.backgroundSurfaceDensity + " with error =" + this.backgroundError);
            }

            initializeClusterVariables();

            bruteForceCalculateGroups();
        }
    }


    /**
     * calculate convex hulls of clusters and calculate the hull centroids and area
     *
     * @throws IOException
     * @throws TwoPointVoidStatsException
     */
    public void calculateHullsOfClusters() throws IOException, TwoPointVoidStatsException {

        if (state.ordinal() < STATE.CLUSTERS_FOUND.ordinal()) {
            findClusters();
        }

        long startTimeMillis = System.currentTimeMillis();

        groupHullIndexes = new SimpleLinkedListNode[nGroups];

        xGroupHullCentroids = new float[nGroups];
        yGroupHullCentroids = new float[nGroups];
        groupHullSurfaceAreas = new float[nGroups];

        // the indexes stored in the instance vars such as pointToGroupIndex are w.r.t. the arrays sorted by y
        float[] x = indexer.getXSortedByY();
        float[] y = indexer.getYSortedByY();

        PolygonAndPointPlotter plotter = new PolygonAndPointPlotter(indexer.x[indexer.sortedXIndexes[0]],
            indexer.x[indexer.sortedXIndexes[indexer.nXY - 1]],
            indexer.y[indexer.sortedYIndexes[0]], indexer.y[indexer.sortedYIndexes[indexer.nXY - 1]]);

        for (int i = 0; i < nGroups; i++) {

            groupHullIndexes[i] = new SimpleLinkedListNode();

            SimpleLinkedListNode groupNode = groupMembership[i];

            int nMembers = groupNode.getKeys().length;

            float[] xMember = new float[nMembers];
            float[] yMember = new float[nMembers];
            int[] memberIndexes = new int[nMembers];

            int count = 0;

            while ((groupNode != null) && (groupNode.key != -1)) {

                int pointIndex = groupNode.key;

                xMember[count]       = x[pointIndex];
                yMember[count]       = y[pointIndex];
                memberIndexes[count] = pointIndex;
                count++;

                groupNode = groupNode.next;
            }

            float[] xhull = null;
            float[] yhull = null;

            if (count > 2) {
                try {
                    GrahamScan scan = new GrahamScan();
                    scan.computeHull(xMember, yMember);

                    xhull = scan.getXHull();
                    yhull = scan.getYHull();
                } catch (GrahamScanTooFewPointsException e) {
                }
            }

            if (xhull == null) {

                xhull = new float[count];
                yhull = new float[count];
                for (int ii = 0; ii < count; ii++) {
                    xhull[ii] = xMember[ii];
                    yhull[ii] = yMember[ii];
                }
            }

            int hullMatchCount = 0;
            // the hull is a subset of memberIndexes so point to the original coordinates
            //   instead of creating a new instance data structure
            for (int j = 0; j < xhull.length; j++) {

                float xh = xhull[j];
                float yh = yhull[j];

                for (int k = 0; k < count; k++) {

                    int pointIndex = memberIndexes[k];

                    if ( (x[pointIndex] == xh) && (y[pointIndex] == yh) ) {
                        groupHullIndexes[i].insert(pointIndex);
                        hullMatchCount++;
                        break;
                    }
                }
            }

            if (debug) {
                try {
                    plotter.addPlot(xMember, yMember, xhull, yhull, "");
                    plotter.writeFile();
                } catch (IOException e) {

                }
            }

            float[] ca = LinesAndAngles.calcAreaAndCentroidOfSimplePolygon(xhull, yhull);

            if (ca != null) {
                groupHullSurfaceAreas[i] = ca[0];
                xGroupHullCentroids[i] = ca[1];
                yGroupHullCentroids[i] = ca[2];
            }
        }

        state = STATE.CLUSTER_HULLS_CALCULATED;

        if (doLogPerformanceMetrics) {

            long stopTimeMillis = System.currentTimeMillis();

            printPerformanceMetrics(startTimeMillis, stopTimeMillis, "calculateHullsOfClusters");
        }
    }

    public int getNumberOfGroups() {
        return nGroups;
    }

    public float[] calculateGroupCentroidUsingAllPointsEquallyWeighted(int groupNumber) {

        if (groupNumber >= nGroups) {
            throw new IllegalArgumentException("groupNumber is larger than existing number of groups");
        }

        // the indexes stored in the instance vars such as pointToGroupIndex are w.r.t. the arrays sorted by y
        float[] x = indexer.getXSortedByY();
        float[] y = indexer.getYSortedByY();

        SimpleLinkedListNode groupNode = groupMembership[groupNumber];

        float xCoordsAvg = 0;
        float yCoordsAvg = 0;
        int count = 0;

        while ((groupNode != null) && (groupNode.key != -1)) {

            int pointIndex = groupNode.key;

            xCoordsAvg += x[pointIndex];
            yCoordsAvg += y[pointIndex];

            groupNode = groupNode.next;
        }
        xCoordsAvg /= (float)count;
        yCoordsAvg /= (float)count;

        return new float[]{xCoordsAvg, yCoordsAvg};
    }

    public float[] getXGroupHull(int groupNumber) {

        if (groupNumber >= nGroups) {
            throw new IllegalArgumentException("groupNumber is larger than existing number of groups");
        }

        // the indexes stored in the instance vars such as pointToGroupIndex are w.r.t. the arrays sorted by y
        float[] x = indexer.getXSortedByY();

        SimpleLinkedListNode hullNode = groupHullIndexes[groupNumber];

        float[] xhull = new float[hullNode.getKeys().length];
        int count = 0;

        while ((hullNode != null) && (hullNode.key != -1)) {

            int pointIndex = hullNode.key;

            xhull[count] = x[pointIndex];

            hullNode = hullNode.next;
            count++;
        }

        return xhull;
    }

    public float[] getYGroupHull(int groupNumber) {

        if (groupNumber >= nGroups) {
            throw new IllegalArgumentException("groupNumber is larger than existing number of groups");
        }

        // the indexes stored in the instance vars such as pointToGroupIndex are w.r.t. the arrays sorted by y
        float[] y = indexer.getYSortedByY();

        SimpleLinkedListNode hullNode = groupHullIndexes[groupNumber];

        float[] yhull = new float[hullNode.getKeys().length];
        int count = 0;

        while ((hullNode != null) && (hullNode.key != -1)) {

            int pointIndex = hullNode.key;

            yhull[count] = y[pointIndex];

            hullNode = hullNode.next;
            count++;
        }

        return yhull;
    }

    public float[] getXHullCentroids() {
        float[] seeds = new float[nGroups];
        for (int i = 0; i < nGroups; i++) {
            seeds[i] = getXGroupHullCentroid(i);
        }
        return seeds;
    }
    public float[] getYHullCentroids() {
        float[] seeds = new float[nGroups];
        for (int i = 0; i < nGroups; i++) {
            seeds[i] = getYGroupHullCentroid(i);
        }
        return seeds;
    }

    public float getXGroupHullCentroid(int groupNumber) {

        if (groupNumber >= nGroups) {
            throw new IllegalArgumentException("groupNumber is larger than existing number of groups");
        }

        return xGroupHullCentroids[groupNumber];
    }
    public float getYGroupHullCentroid(int groupNumber) {

        if (groupNumber >= nGroups) {
            throw new IllegalArgumentException("groupNumber is larger than existing number of groups");
        }

        return yGroupHullCentroids[groupNumber];
    }

    public float getBackgroundSurfaceDensity() {
        return backgroundSurfaceDensity;
    }

    public float getBackgroundSurfaceDensityError() {
        return backgroundError;
    }

    protected void checkAndExpandGroupMembershipArray() {

        if (groupMembership.length < (nGroups + 1)) {
            int oldN = groupMembership.length;
            int n = (int) (1.5f * oldN);
            if (n < (oldN + 1)) {
                n = oldN + 1;
            }

            groupMembership = Arrays.copyOf(groupMembership, n);
            for (int k = oldN; k < n; k++) {
                groupMembership[k] = new SimpleLinkedListNode();
            }
        }
    }

    public float[] getX() {
        return indexer.getX();
    }

    public float[] getY() {
        return indexer.getY();
    }
    public float[] getXErrors() {
        return indexer.getXErrors();
    }
    public float[] getYErrors() {
        return indexer.getYErrors();
    }
    DoubleAxisIndexer getIndexer() {
        return indexer;
    }

    public float[] getXGroup(int groupNumber) {

        return getGroupArray(groupNumber, indexer.getXSortedByY());
    }

    public float[] getYGroup(int groupNumber) {

        return getGroupArray(groupNumber, indexer.getYSortedByY());
    }

    protected float[] getGroupArray(int groupNumber, float[] array) {

        int count = 0;

        float[] a = new float[10];

        SimpleLinkedListNode groupNode = groupMembership[groupNumber];

        while ((groupNode != null) && (groupNode.key != -1)) {

            int pointIndex = groupNode.key;

            if (a.length < (count + 1)) {
                int oldN = a.length;
                int n = (int) (1.5f * oldN);
                if (n < (oldN + 1)) {
                    n = oldN + 1;
                }
                a = Arrays.copyOf(a, n);
            }

            a[count] = array[pointIndex];

            count++;

            groupNode = groupNode.next;
        }

        return Arrays.copyOf(a, count);
    }

}