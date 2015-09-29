package algorithms.util;

import java.util.logging.Logger;
import algorithms.compGeometry.clustering.twopointcorrelation.TwoPointCorrelation;
import java.util.HashMap;

/**

  Usage from the command line:
      Requires a tab delimited text file with 4 columns: x, y, xErrors, yErrors.

          java -jar two-point-correlation.jar --file /path/to/file/fileName.txt

  @author nichole
 */
public class MainRunner {

    /**
     *
     */
    protected HashMap<String, String> arguments = null;

    /**
     *
     */
    protected TwoPointCorrelation clusterFinder = null;

    /**
     *
     */
    public MainRunner() {

    }

    /**
     *
     * @return
     */
    public HashMap<String, String> getArguments() {
        return arguments;
    }

    /**
     *
     * @return
     */
    public TwoPointCorrelation getTwoPointCorrelation() {
        return clusterFinder;
    }

    /**
     *
     * @param reader
     * @param args
     * @return
     */
    public TwoPointCorrelation run(IInputFileReader reader, String[] args) {

        if (args != null) {
            for (int i = 0; i < args.length; i++) {
                System.out.println(args[i]);
            }
        }

        if (args == null || args.length < 2) {
            System.out.println("Requires file:  --file /path/to/file/fileName.txt which contains rows of x<tab>y<tab>xerr<tab>yerr");
            System.out.println("     optional:  --twosigma");
            System.out.println("     optional:  --threesigma");
            System.out.println("     optional:  --background 0.3 (requires backgrounderror guesstimate at least)");
            System.out.println("     optional:  --backgrounderror 0.1");
            System.out.println("     optional:  --debug");
            return null;
        }

        arguments = new HashMap<String, String>();

        boolean useTwo = false;
        boolean useThree = false;
        boolean debug = false;

        Float bckgnd = null;
        Float bckgndErr = null;

        String filePath = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--file")) {
                if ( (i+1) < args.length) {
                    filePath = args[i+1];
                    i++;
                    arguments.put("filePath", filePath);
                }
            } else if (args[i].equals("--twosigma")) {
                useTwo = true;
                arguments.put("useSigmaTwo", "true");
            } else if (args[i].equals("--threesigma")) {
                useThree = true;
                arguments.put("useSigmaThree", "true");
            } else if (args[i].equals("--background")) {
                if ( (i+1) < args.length) {
                    String tmp = args[i+1];
                    bckgnd = Float.valueOf(tmp);
                    i++;
                    arguments.put("background", tmp);
                }
            } else if (args[i].equals("--backgrounderror")) {
                if ( (i+1) < args.length) {
                    String tmp = args[i+1];
                    bckgndErr = Float.valueOf(tmp);
                    i++;
                    arguments.put("backgrounderror", tmp);
                }
            } else if (args[i].equals("--debug")) {
                debug = true;
                arguments.put("debug", "true");
            } else {
                System.out.println("WARNING, not an option: " + args[i]);
            }
        }

        if (filePath == null) {
            System.err.println("Requires file:  --file /path/to/file/fileName.txt which contains rows of x<tab>y<tab>xerr<tab>yerr");
            return null;
        }

        if ((bckgnd != null) && (bckgndErr == null)) {
            System.err.println("Requires  --backgrounderror if --background is set");
            return null;
        }

        try {

            reader.read(filePath);

            Logger.getLogger(MainRunner.class.getName()).info("begin TwoPointCorrelation");

            clusterFinder = new TwoPointCorrelation(
                reader.getX(), reader.getY(), reader.getXErrors(), reader.getYErrors(), reader.getX().length);

            if (debug) {
                clusterFinder.setDebug(true);
            } else {
                clusterFinder.setDebug(false);
            }


            // default is 2.5
            if (useTwo) {
                clusterFinder.setThresholdFactorToTwo();
            } else if (useThree) {
                clusterFinder.setThresholdFactorToThree();
            }


            if (bckgnd != null) {

                Logger.getLogger(MainRunner.class.getName()).info("set background and error");

                clusterFinder.setBackground(bckgnd.floatValue(), bckgndErr.floatValue());

            } else {

                Logger.getLogger(MainRunner.class.getName()).info("calculate background");

                clusterFinder.calculateBackground();
            }


            Logger.getLogger(MainRunner.class.getName()).info("find clusters");

            clusterFinder.findClusters();
            

            String plotFilePath = clusterFinder.plotClusters();
            
            return clusterFinder;

        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return null;
    }

}
