package com.climbwithyourfeet.clustering;

import algorithms.matrix.MatrixUtil;
import algorithms.util.ResourceFinder;
import gnu.trove.iterator.TIntIntIterator;
import gnu.trove.iterator.TIntObjectIterator;
import gnu.trove.map.TIntIntMap;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntIntHashMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

public class RecipeReviewsReader {

    // utility matrix with row indexes = user id indexes, col indexes = recipe id indexes, value = stars
    private double[][] userRecipeStars;
    //(userIdx, userId);
    private Map<Integer, String> userIdxIdMap;
    //(recipeIdx, recipeId)
    private Map<Integer, Integer> recipeIdxIdMap;

    //TODO: would like to use the text, commentId, userId, recipeId also

    /*
    see test/resources/ucl_ml_datasets/README_recipe+reviews+and+user+feedback+dataset.txt for citation
    recipe+reviews+and+user+feedback+dataset.csv

    <pre>
        12008 users, 100 recipes.
        8 users made 2 recipe reviews.
        12000 users made 1 recipe review.
        extremely sparse dataset
    </pre>
     */
    public void readUserRecipeUtilityMatrix() throws IOException {

        /*
        ,recipe_number,recipe_code,recipe_name,comment_id,
        user_id,user_name,user_reputation,created_at,reply_count,
        thumbs_up,thumbs_down,stars,best_score,text

        reading in these features:

        col 1 is recipe_number : int
        comment_id : string
        col 5 is user_id : string
        thumbs_up : int
        thumbs_down : int
        col 12 is stars : int
        col 14 is text : string
        */

        final String sep = System.getProperty("file.separator");
        String testDir = ResourceFinder.findTestResourcesDirectory();
        String path = testDir + sep + "ucl_ml_datasets" + sep + "recipe+reviews+and+user+feedback+dataset.csv";
        File f = new File(path);
        if (!f.exists()) {
            throw new IOException("could not find file at " + path);
        }

        int i;
        int nCols = 15;
        int missingValue = 0;

        // key is recipeIdx, value = Map with key is useridx, value = stars
        TIntObjectMap<TIntIntMap> recipeUserStarsMap = new TIntObjectHashMap<>();

        // key is userIdx, value = Map with key is recipeidx, value = stars
        TIntObjectMap<TIntIntMap> userRecipeStarsMap = new TIntObjectHashMap<>();

        String[] items;
        int recipeId;
        String userId;
        int stars;

        TIntIntMap userStarMap;
        TIntIntMap recipeStarMap;

        Map<String, Integer> userIdIndexMap = new HashMap<>();
        TIntIntMap recipeIdIndexMap = new TIntIntHashMap();

        //(userIdx, userId);
        userIdxIdMap = new HashMap<Integer, String>();
        //(recipeIdx, recipeId)
        recipeIdxIdMap = new HashMap<Integer, Integer>();

        FileReader reader = null;
        BufferedReader in = null;
        int[] quotes = new int[2];
        i = 0;
        int j;
        try {
            in = new BufferedReader(new FileReader(f));

            String line = in.readLine();
            if (line == null) {
                throw new IOException("could not read a line from " + path);
            }
            // discard the comment line
            line = in.readLine();
            boolean missingData;
            int recipeIdx;
            int userIdx;
            while (line != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    break;
                }
                items = line.split(",");
                quotes[0] = line.indexOf("\"");
                quotes[1] = line.indexOf("\"", quotes[0] + 1);
                if (quotes[0] > -1) {
                    while (quotes[1] == -1) {
                        // keep reading lines until find end quote
                        line = in.readLine();
                        if (line == null) {
                            break;
                        }
                        quotes[1] = line.indexOf("\"");
                        // if this is > -1, we've read the end of the quotes and so the next line should be a new row
                    }
                }
                assert(items.length >= nCols);

                recipeId = Integer.parseInt(items[1]);
                userId = items[5];
                stars = Integer.parseInt(items[12]);

                if (!recipeIdIndexMap.containsKey(recipeId)) {
                    recipeIdx = recipeIdIndexMap.size();
                    recipeIdIndexMap.put(recipeId, recipeIdx);
                    recipeIdxIdMap.put(recipeIdx, recipeId);
                } else {
                    recipeIdx = recipeIdIndexMap.get(recipeId);
                }
                if (!userIdIndexMap.containsKey(userId)) {
                    userIdx = userIdIndexMap.size();
                    userIdIndexMap.put(userId, userIdx);
                    userIdxIdMap.put(userIdx, userId);
                } else {
                    userIdx = userIdIndexMap.get(userId);
                }

                userStarMap = recipeUserStarsMap.get(recipeIdx);
                if (userStarMap == null) {
                    userStarMap = new TIntIntHashMap();
                    recipeUserStarsMap.put(recipeIdx, userStarMap);
                }
                // note: haven't checked for multiple entries for a user.  if present, should edit this
                //   and sort by created_time
                userStarMap.put(userIdx, stars);


                recipeStarMap = userRecipeStarsMap.get(recipeIdx);
                if (recipeStarMap == null) {
                    recipeStarMap = new TIntIntHashMap();
                    userRecipeStarsMap.put(userIdx, recipeStarMap);
                }
                recipeStarMap.put(recipeIdx, stars);

                ++i;
                line = in.readLine();
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (reader != null) {
                reader.close();
            }
        }

        System.out.printf("size of userRecipeStarsMap=%d\n", userRecipeStarsMap.size());
        System.out.printf("size of recipeUserStarsMap=%d\n", recipeUserStarsMap.size());
        //userRecipeStarsMap has 12008 users
        printFrequencyMap(userRecipeStarsMap, "userRecipe");
        //printFrequencyMap(recipeUserStarsMap, "recipeUser");

        // 12008 users, 100 recipes
        // 8 users made 2 recipe reviews
        // 12000 users made 1 recipe review
        // extremely sparse dataset

        userRecipeStars = MatrixUtil.zeros(userIdIndexMap.size(), recipeIdIndexMap.size());

        int recipeIdx;
        int userIdx;

        TIntObjectIterator<TIntIntMap> iter = recipeUserStarsMap.iterator();
        TIntIntIterator iter2;
        Map.Entry<String, Integer> entry;
        while (iter.hasNext()){
            iter.advance();
            recipeIdx = iter.key();

            userStarMap = iter.value();
            iter2 = userStarMap.iterator();
            while (iter2.hasNext()) {
                iter2.advance();
                userIdx = iter2.key();
                stars = iter2.value();
                userRecipeStars[userIdx][recipeIdx] = stars;
            }
        }
    }

    private void printFrequencyMap(TIntObjectMap<TIntIntMap> map, String label) {
        TIntObjectIterator<TIntIntMap> iter = map.iterator();
        // key = size, value = number of keys have that size value
        SortedMap<Integer, Integer> freqMap = new TreeMap<>();
        while (iter.hasNext()) {
            iter.advance();
            int key = iter.key();
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

    public double[][] getUserRecipeStars() {
        return userRecipeStars;
    }

    public Map<Integer, String> getUserIdxIdMap() {
        return userIdxIdMap;
    }

    public Map<Integer, Integer> getRecipeIdxIdMap() {
        return recipeIdxIdMap;
    }
}
