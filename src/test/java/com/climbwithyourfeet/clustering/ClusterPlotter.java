package com.climbwithyourfeet.clustering;

import algorithms.util.PairInt;
import algorithms.util.ResourceFinder;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
Convenience class to create a plot to visualize results of the
cluster code.

@author nichole
*/
public class ClusterPlotter {

    /**
     *
     */
    protected final StringBuffer plotContent;

    /**
     *
     */
    protected int plotNumber = 0;
    
    /**
     *
     */
    protected Float x0 = null;

    /**
     *
     */
    protected Float x1 = null;

    /**
     *
     */
    protected Float y0 = null;

    /**
     *
     */
    protected Float y1 = null;

    /**
     *
     * @throws FileNotFoundException
     * @throws IOException
     */
    public ClusterPlotter() throws FileNotFoundException, IOException {

        plotContent = getTemplateHtmlPlot();
    }

    /**
     *
     * @param xMin
     * @param xMax
     * @param yMin
     * @param yMax
     * @param points
     * @param clusterSets
     * @param bckgndseps
     * @param plotLabel2
     */
    public void addPlotWithoutHull( 
        float xMin, float xMax, float yMin, float yMax,
        Set<PairInt> points, List<Set<PairInt>> clusterSets,
        float[] bckgndseps,
        String plotLabel2) {

        StringBuffer dataSB = new StringBuffer();

        String str = String.format("%.7f", xMin);
        dataSB.append("var x_min_").append(plotNumber).append(" = ")
            .append(str).append(";\n");
        str = String.format("%.7f", xMax);
        dataSB.append("var x_max_").append(plotNumber).append(" = ")
            .append(str).append(";\n");

        str = String.format("%.7f", yMin);
        dataSB.append("var y_min_").append(plotNumber).append(" = ")
            .append(str).append(";\n");
        str = String.format("%.7f", yMax);
        dataSB.append("var y_max_").append(plotNumber).append(" = ")
            .append(str).append(";\n");
        
        //  ===== add points data =====
        dataSB.append("\n\n").append("var data_points_").append(plotNumber)
            .append(" = [\n");
        
        int i = 0;
        for (PairInt p : points) {
            if (i > 0) {
                dataSB.append(",\n");
            }
            dataSB.append("    {x:").append(p.getX()).append(", y:").append(p.getY());
            dataSB.append("}");
            ++i;
        }
        dataSB.append("\n];\n");


          //  ===== add group centroids =====
        dataSB.append("\n\n").append("var data_group_centroids_")
            .append(plotNumber).append(" = [\n");
        
        /*for (i = 0; i < clusterSets.size(); i++) {
            Set<PairInt> set = clusterSets.get(i);
            double[] xyCen = com.climbwithyourfeet.clustering.util.MiscMath.calculateXYCentroids(set);
            if (i > 0) {
                dataSB.append(",\n");
            }
            dataSB.append("    {x:").append(xyCen[0])
                .append(", y:").append(xyCen[1]).append(", name: ")
                .append(i).append("}");
        }*/
        dataSB.append("\n];\n");


        dataSB.append("\n").append("var data_groups_")
            .append(plotNumber).append(" = [\n");
        
        for (i = 0; i < clusterSets.size(); i++) {
            
            Set<PairInt> cluster = clusterSets.get(i);
        
            dataSB.append("    [");
            int ii = 0;
            for (PairInt p : cluster) {
                String xStr = String.format("%d", p.getX());
                String yStr = String.format("%d", p.getY());
                if (ii > 0) {
                    dataSB.append(",\n");
                }
                dataSB.append("    {x:").append(xStr).append(", y:").append(yStr).append("}");
                ++ii;
            }
            if (i == (clusterSets.size() - 1)) {
                dataSB.append("]\n");
            } else {
                dataSB.append("],\n ");
            }
        }
        dataSB.append("];\n");

        dataSB.append("\n").append("var plot_label_").append(plotNumber)
            .append("='").append(Arrays.toString(bckgndseps)).append("';\n");

        // ======= add RENDER statement ==========
        dataSB.append("\nrenderPlotWithoutHull('plot").append(plotNumber)
            .append("', data_group_centroids_").append(plotNumber)
            .append(", data_points_").append(plotNumber)
            .append(", data_groups_").append(plotNumber)
            .append(", plot_label_").append(plotNumber)
            .append(", x_min_").append(plotNumber)
            .append(", x_max_").append(plotNumber)
            .append(", y_min_").append(plotNumber)
            .append(", y_max_").append(plotNumber)
            .append(");\n");

        String srchFor = "/* === DO NOT REMOVE THIS == END DATA */";
        int insertOffset = plotContent.indexOf(srchFor);
        if (insertOffset == -1) {
            throw new IllegalStateException("cannot find END DATA marker");
        }
        plotContent.insert(insertOffset, dataSB.toString());
        dataSB = null;


        // ========== add the PLOT DIVS ==============
        StringBuffer plotDivs = new StringBuffer();
        plotDivs.append("<div id='plot").append(plotNumber).append("' class='plot'></div>\n");
        
        srchFor = "<!-- === DO NOT REMOVE THIS == END PLOT DIVS -->";
        insertOffset = plotContent.indexOf(srchFor);
        if (insertOffset == -1) {
            throw new IllegalStateException("cannot find END DATA marker");
        }
        plotContent.insert(insertOffset, plotDivs.toString());
        plotDivs = null;

        plotNumber++;
    }

    /**
     *
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    protected StringBuffer getTemplateHtmlPlot() throws FileNotFoundException, 
        IOException {
        return getTemplateHtmlPlot("plot_twoptcorrelation.html");
    }

    /**
     *
     * @param fileName
     * @return
     * @throws FileNotFoundException
     * @throws IOException
     */
    protected StringBuffer getTemplateHtmlPlot(String fileName) throws 
        FileNotFoundException, IOException 
    {

        StringBuffer sb = new StringBuffer();

        Reader reader = null;
        BufferedReader in = null;

        try {

            String path = ResourceFinder.findFileInResources(fileName);

            reader = new FileReader(new File(path));
            in = new BufferedReader(reader);

            String line = in.readLine();

            while (line != null) {
                sb.append(line).append("\n");
                line = in.readLine();
            }
            
            return sb;

        } catch(IOException e) {

            if (in != null) {
                in.close();
                in = null;
            }
            if (reader != null) {
                reader.close();
                reader = null;
            }
            
            try {
                ClassLoader cls = ResourceFinder.class.getClassLoader();

                InputStream input = cls.getResourceAsStream(fileName);

                if (input == null) {
                    throw new IOException("could not find file " + fileName);
                }

                reader = new InputStreamReader(input);
                in = new BufferedReader(reader);

                String line = in.readLine();

                while (line != null) {
                    sb.append(line).append("\n");
                    line = in.readLine();
                }
                
                return sb;
                
            } catch(IOException e2) {

                // this class and resources might be in a jar file, so look
                // for that
                String sep = System.getProperty("file.separator");
                String cwd = System.getProperty("user.dir");

                String jarFilePath = 
                    "com.climbwithyourfeet.clustering.jar";
                jarFilePath = cwd + sep + "lib" + sep + jarFilePath;

                InputStream inStream = null;
                ByteArrayOutputStream out2 = null;

                try {
                    inStream = ResourceFinder.findJarEntry(jarFilePath, fileName);
                    out2 = new ByteArrayOutputStream();
                    int c;
                    while ((c = inStream.read()) != -1) {
                        out2.write(c);
                    }
                    StringBuffer contents = new StringBuffer(out2.toString());

                    return contents;

                } finally {
                    if (inStream != null) {
                        inStream.close();
                    }
                    if (out2 != null) {
                        out2.close();
                    }
                }
            }

        } finally {
            if (in != null) {
                in.close();
                in = null;
            }
            if (reader != null) {
                reader.close();
                reader = null;
            }
        }
    }

    /**
     *
     * @return
     * @throws IOException
     */
    public String writeFile() throws IOException {
        return writeToFile(this.plotContent.toString(), "clusters.html");
    }
    
    /**
     *
     * @return
     * @throws IOException
     */
    public String writeFile(String fileName) throws IOException {
        
        if (!fileName.endsWith("html")) {
            fileName = fileName + ".html";
        }
        if (!fileName.startsWith("clusters")) {
            fileName = "clusters_" + fileName;
        }
        
        return writeToFile(this.plotContent.toString(), fileName);
    }

    /**
     *
     * @return
     * @throws IOException
     */
    public String writeFile2() throws IOException {
        return writeToFile(this.plotContent.toString(), "clusters2.html");
    }

    /**
     *
     * @return
     * @throws IOException
     */
    public String writeFile3() throws IOException {
        return writeToFile(this.plotContent.toString(), "clusters3.html");
    }

    /**
     *
     * @param fileContent
     * @param fileName
     * @return
     * @throws IOException
     */
    protected String writeToFile(String fileContent, String fileName) throws IOException {

        String copyFilePath = ResourceFinder.writeToCWD(fileContent, fileName);
        return copyFilePath;
    }
}
