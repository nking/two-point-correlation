package com.climbwithyourfeet.clustering;

import algorithms.imageProcessing.DistanceTransform;
import algorithms.util.PixelHelper;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;

/**
 * miscellaneous methods to examine the x and y transforms
 * separately and compare with the 2D results.
 * 
 * @author nichole
 */
public class DistanceTransformUtil {
    
    public static int[][] transform(TIntSet pixIdxs, int width, int height) {
        
        PixelHelper ph = new PixelHelper();
        int[] xy = new int[2];
        
        int[][] data = new int[width][];
        int[][] dtX = new int[width][];
        int[][] dtY = new int[width][];
        for (int i = 0; i < width; ++i) {
            data[i] = new int[height];
            dtX[i] = new int[height];
            dtY[i] = new int[height];
        }
        
        TIntIterator iter = pixIdxs.iterator();
        while (iter.hasNext()) {
            int pixIdx = iter.next();
            ph.toPixelCoords(pixIdx, width, xy);
            data[xy[0]][xy[1]] = 1;
        }
        
        DistanceTransform trans = new DistanceTransform();
        
        for (int i = 0; i < width; ++i) {
            dtX[i] = trans.applyMeijsterEtAl1D(data[i]);
        }
        
        // transform data by 90 degrees, then the results by -90
        for (int j = 0; j < height; ++j) {
            int[] dataTr = new int[width];
            for (int i = 0; i < width; ++i) {
                dataTr[i] = data[i][j];
            }
            int[] tmp = trans.applyMeijsterEtAl1D(dataTr);
            for (int i = 0; i < width; ++i) {
                dtY[i][j] = tmp[i];
            }
        }
        
        int[][] dt = trans.applyMeijsterEtAl(data);
        
        //System.out.println("2D:");
        //printDT(dt);
        
        int v;
        for (int i = 0; i < width; ++i) {
            for (int j = 0; j < height; ++j) {
                v = Math.min(dtX[i][j], dtY[i][j]);
                v *= v;
                dt[i][j] = Math.min(dt[i][j], v);
            }
        }
        
        //System.out.println("dtX:");
        //printDT(dtX);
        //System.out.println("dtY:");
        //printDT(dtY);
        
        return dt;
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
}
