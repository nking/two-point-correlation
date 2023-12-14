package com.climbwithyourfeet.clustering;

import junit.framework.TestCase;

/**
 *
 * @author nichole
 */
public class LungCancerDataTest extends TestCase {

    public void test0() throws Exception {

        LungCancerReader reader = new LungCancerReader();
        reader.readData();

        double[][] x = reader.getX();
        int[] y = reader.getY();

        //TODO: consider Xgboost or LightGBM
        //TODO: like authors of paper, consider Knn and discriminant plane

    }
}
