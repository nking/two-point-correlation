package com.climbwithyourfeet.clustering;

import algorithms.matrix.MatrixUtil;
import algorithms.util.ResourceFinder;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import gnu.trove.set.TIntSet;
import gnu.trove.set.hash.TIntHashSet;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AmazonFoodReviewsReader {

    // utility matrix with row indexes = user id indexes, col indexes = product id indexes, value = score
    private double[][] userProductScore;
    //(userIdx, userId)
    private Map<Integer, String> userIdxIdMap;
    //(productIdx, productId)
    private Map<Integer, String> productIdxIdMap;

    /*
    see test/resources/amazon_fine_food_reviews_README.txt

    <pre>
        number of unique productIds =  74258
        number of unique userIds    = 256059
        number of users with > 50 reviews = 260

        from https://www.kaggle.com/code/naushads/1-2-amazon-fine-food-reviews-eda-data-cleaning-fe
        There are 16 datapoints having 'ProfileName' as Null.
        There are 27 datapoints having 'Summary' as Null.

        from https://www.kaggle.com/code/naushads/1-2-amazon-fine-food-reviews-eda-data-cleaning-fe
        +-------------------+--------+---------------------+
        |                   | Count  | Percentage of Total |
        +-------------------+--------+---------------------+
        | Duplicate Reviews | 174562 |  30.70820154313278  |
        |  Original Reviews | 393892 |  69.29179845686723  |
        +-------------------+--------+---------------------+
        We have about 1.75 E5 reviews(~30% of total reviews) which are duplicated across product variants.
        Basically reviews by the same user at the same time with same review text.

        TODO: write to outfile a version with these removed: same user with same review
            see the writer file TODO...

        top 100 number of reviews for each product: [913, 632, 632, 632, 632, 623, 567, 564, 564, 564, 564, 564, 564,
        564, 564, 564, 556, 556, 542, 542, 542, 542, 542, 542, 542, 530, 530, 506, 506, 491, 488, 487, 487, 487, 487,
        487, 479, 464, 457, 457, 457, 457, 456, 456, 456, 456, 455, 455, 455, 455, 455, 455, 455, 455, 455, 452, 424,
        424, 424, 424, 424, 424, 413, 413, 413, 413, 413, 413, 413, 389, 389, 389, 387, 387, 387, 387, 387, 387, 387,
        385, 385, 385, 385, 385, 385, 385, 385, 379, 375, 362, 362, 356, 356, 347, 343, 341, 340, 340, 340, 340]

        top 100 number of reviews by each user: [448, 421, 389, 365, 256, 204, 201, 199, 178, 176, 175, 172, 167, 162,
        162, 161, 157, 155, 154, 151, 150, 150, 149, 143, 143, 142, 141, 140, 135, 134, 133, 133, 127, 126, 125, 125,
        124, 123, 121, 120, 119, 118, 117, 116, 115, 114, 114, 113, 113, 113, 111, 110, 110, 109, 107, 107, 106, 105,
        105, 105, 104, 104, 104, 99, 98, 98, 98, 96, 96, 95, 95, 94, 94, 94, 92, 92, 90, 89, 89, 88, 87, 87, 86, 85,
        85, 84, 84, 83, 83, 82, 82, 82, 80, 80, 79, 79, 78, 78, 78, 77]

        RangeIndex: 568454 entries, 0 to 568453
        Data columns (total 10 columns):
         #   Column                  Non-Null Count   Dtype
        ---  ------                  --------------   -----
         0   Id                      568454 non-null  int64
         1   ProductId               568454 non-null  object
         2   UserId                  568454 non-null  object
         3   ProfileName             568438 non-null  object  <-- has nulls?
         4   HelpfulnessNumerator    568454 non-null  int64
         5   HelpfulnessDenominator  568454 non-null  int64
         6   Score                   568454 non-null  int64
         7   Time                    568454 non-null  int64
         8   Summary                 568427 non-null  object <-- has nulls?
         9   Text                    568454 non-null  object
    </pre>
     */

    /**
     * read in the columns userId, productId and score to make a user product utility matrix.
     * Note that this method reads in all 568454 entries by default which requires at least
     * 568454 * (8 + 8 + 4 + 3*(16)) bytes ~ 4 E7 Bytes for just the utility matrix.
     * Multiply that by about 10 to include other data structures.
     * To read a smaller number, use the overloaded method.
     *     e.g. readUserProductUtilityMatrix(12000, true, null)
     *
     * @throws IOException exception while attempting to read the csv file
     */
    public void readUserProductUtilityMatrix() throws IOException {
        readUserProductUtilityMatrix(null);
    }

    /**
     * read csv file into a user product utility matrix.
     * note that the csv file groups multiple entries for a single productid sequentially and so
     * using random is not a good idea for a small nRead.
     *
     * @param nRead number of product entries to read.  max is 74258.
     *              After the nRead products are parsed,
     *              one more pass is made to gather all entries their reviewing users.
     *
     * @throws IOException exception while attempting to rad the csv file
     */
    public void readUserProductUtilityMatrix(Integer nRead) throws IOException {

        if (nRead != null && nRead < 0) {
            throw new IllegalArgumentException("nRead must non-negative");
        }

        String inPath = AmazonFoodReviewsReaderWriter.filePathProdUserScoreSortProd;
        File f = new File(inPath);
        if (!f.exists()) {
            AmazonFoodReviewsReaderWriter t = new AmazonFoodReviewsReaderWriter();
            t.writeSortedProductFileForCleanedInput();
            f = new File(inPath);
            assert(f.exists());
        }

        int nUniqueProducts = 0;
        // resource is auto-closed when declared an initialized inside the try
        try (FileInputStream in = new FileInputStream(inPath)) {
            while (in.read() != -1) {
                ++nUniqueProducts;
            }
        }

        if (nRead >= nUniqueProducts) {
            nRead = nUniqueProducts;
        }

        int nCols = 10;

        // key is productIdx, value = Map with key is useridx, value = score
        TIntObjectMap<TIntIntMap> productUserScoreMap = new TIntObjectHashMap<>();

        // key is userIdx, value = Map with key is productidx, value = score
        TIntObjectMap<TIntIntMap> userProductScoreMap = new TIntObjectHashMap<>();

        String productId;
        String userId;
        int score;

        TIntIntMap userScoreMap;
        TIntIntMap productScoreMap;

        Map<String, Integer> userIdIdxMap = new HashMap<>();
        Map<String, Integer> productIdIdxMap = new HashMap<>();

        userIdxIdMap = new HashMap<Integer, String>();
        productIdxIdMap = new HashMap<Integer, String>();

        FileInputStream in = null;
        int i = 0;
        try {
            in = new FileInputStream(inPath);

            int productIdx;
            int userIdx;
            int nB;
            while (productIdIdxMap.size() < nRead) {
                // read a line
                nB = in.read();
                if (nB == -1) {
                    break;
                }
                productId = new String(in.readNBytes(nB), StandardCharsets.UTF_8);
                nB = in.read();
                userId = new String(in.readNBytes(nB), StandardCharsets.UTF_8);
                score = in.read();

                if (!productIdIdxMap.containsKey(productId)) {
                    productIdx = productIdIdxMap.size();
                    productIdIdxMap.put(productId, productIdx);
                    productIdxIdMap.put(productIdx, productId);
                } else {
                    productIdx = productIdIdxMap.get(productId);
                }
                if (!userIdIdxMap.containsKey(userId)) {
                    userIdx = userIdIdxMap.size();
                    userIdIdxMap.put(userId, userIdx);
                    userIdxIdMap.put(userIdx, userId);
                } else {
                    userIdx = userIdIdxMap.get(userId);
                }

                userScoreMap = productUserScoreMap.get(productIdx);
                if (userScoreMap == null) {
                    userScoreMap = new TIntIntHashMap();
                    productUserScoreMap.put(productIdx, userScoreMap);
                }
                // note: haven't checked for multiple entries for a user.  if present, should edit this
                //   and sort by created_time
                userScoreMap.put(userIdx, score);


                productScoreMap = userProductScoreMap.get(productIdx);
                if (productScoreMap == null) {
                    productScoreMap = new TIntIntHashMap();
                    userProductScoreMap.put(userIdx, productScoreMap);
                }
                productScoreMap.put(productIdx, score);

                ++i;
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }

        // TODO: if nRead is less than max, make another pass to gather all user entries already in userProductScoreMap
        if (nRead < nUniqueProducts) {
            // skip past those already read, and then aggregate any remaining with userId from userIdIdxMap
            int lastIRead = i - 1;
            i = 0;
            try {
                in = new FileInputStream(inPath);

                int productIdx;
                int userIdx;
                int nB;
                while (true) {
                    // read a line
                    nB = in.read();
                    if (nB == -1) {
                        break;
                    }
                    productId = new String(in.readNBytes(nB), StandardCharsets.UTF_8);
                    nB = in.read();
                    userId = new String(in.readNBytes(nB), StandardCharsets.UTF_8);
                    score = in.read();

                    if (i < lastIRead || !userIdIdxMap.containsKey(userId)) {
                        ++i;
                        continue;
                    }

                    if (!productIdIdxMap.containsKey(productId)) {
                        productIdx = productIdIdxMap.size();
                        productIdIdxMap.put(productId, productIdx);
                        productIdxIdMap.put(productIdx, productId);
                    } else {
                        productIdx = productIdIdxMap.get(productId);
                    }

                    userIdx = userIdIdxMap.get(userId);

                    userScoreMap = productUserScoreMap.get(productIdx);
                    if (userScoreMap == null) {
                        userScoreMap = new TIntIntHashMap();
                        productUserScoreMap.put(productIdx, userScoreMap);
                    }
                    // note: haven't checked for multiple entries for a user.  if present, should edit this
                    //   and sort by created_time
                    userScoreMap.put(userIdx, score);


                    productScoreMap = userProductScoreMap.get(productIdx);
                    if (productScoreMap == null) {
                        productScoreMap = new TIntIntHashMap();
                        userProductScoreMap.put(userIdx, productScoreMap);
                    }
                    productScoreMap.put(productIdx, score);

                    ++i;
                }
            } finally {
                if (in != null) {
                    in.close();
                }
            }
        }

        System.out.printf("size of userProductScoreMap=%d\n", userProductScoreMap.size());
        System.out.printf("size of productUserScoreMap=%d\n", productUserScoreMap.size());
        System.out.flush();

        printFrequencyMap(userProductScoreMap, "userProduct");
        printFrequencyMap(productUserScoreMap, "productUser");

        userProductScore = MatrixUtil.zeros(userIdIdxMap.size(), productIdIdxMap.size());

        int productIdx;
        int userIdx;

        TIntObjectIterator<TIntIntMap> iter = productUserScoreMap.iterator();
        TIntIntIterator iter2;
        while (iter.hasNext()){
            iter.advance();
            productIdx = iter.key();

            userScoreMap = iter.value();
            iter2 = userScoreMap.iterator();
            while (iter2.hasNext()) {
                iter2.advance();
                userIdx = iter2.key();
                score = iter2.value();
                userProductScore[userIdx][productIdx] = score;
            }
        }

        System.out.println("done creating utility matrix");
    }

    private void printFrequencyMap(TIntObjectMap<TIntIntMap> map, String label) {
        TIntObjectIterator<TIntIntMap> iter = map.iterator();
        // key = size, value = number of keys have that size value
        SortedMap<Integer, Integer> freqMap = new TreeMap<>();
        while (iter.hasNext()) {
            iter.advance();
            //int key = iter.key();
            int sz = iter.value().size();
            if (freqMap.containsKey(sz)) {
                freqMap.put(sz, freqMap.get(sz) + 1);
            } else {
                freqMap.put(sz, 1);
            }
        }
        // sorted by key already
        for (Integer key : freqMap.keySet()) {
            System.out.printf("%s: number of size:%d, number of keys:%d\n", label, key, freqMap.get(key));
        }
    }

    public double[][] getUserProductScore() {
        return userProductScore;
    }

    public Map<Integer, String> getUserIdxIdMap() {
        return userIdxIdMap;
    }

    public Map<Integer, String> getProductIdxIdMap() {
        return productIdxIdMap;
    }
}
