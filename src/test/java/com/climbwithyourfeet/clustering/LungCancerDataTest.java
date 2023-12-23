package com.climbwithyourfeet.clustering;

import algorithms.matrix.MatrixUtil;
import junit.framework.TestCase;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.SVD;

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

        int p = 2;
        SVD svd = SVD.factorize(new DenseMatrix(x));
        double[][] v = MatrixUtil.convertToRowMajor(svd.getVt());
        double[][] vp = MatrixUtil.copySubMatrix(v, 0, v.length - 1, 0, p - 1);

        double[][] projected = MatrixUtil.multiply(x, vp);

        // rewriting in python to use existing libraries

        //TODO: consider Xgboost (need alot of data though) or LightGBM
        //TODO: like authors of paper, consider Knn and discriminant plane

    }

}
