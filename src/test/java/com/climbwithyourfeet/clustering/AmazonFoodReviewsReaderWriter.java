package com.climbwithyourfeet.clustering;

import algorithms.matrix.MatrixUtil;
import algorithms.sort.MiscSorter;
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
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * reads the file and extracts only productId, userId, and score and
 * writes those into other csv files, depending upon sort chosen or not.
 */
public class AmazonFoodReviewsReaderWriter {

    private static final int nEntries = 568454;

    /*
    see test/resources/amazon_fine_food_reviews_README.txt

    <pre>
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

    public void writeProductUseScoreFile() throws IOException {

        /*
        0     1        2       3            4                    5                   6     7    8       9
        Id,ProductId,UserId,ProfileName,HelpfulnessNumerator,HelpfulnessDenominator,Score,Time,Summary,Text

        reading in these features:

        col 1 is productId : string
        col 2 is user_id : string
        col 6 is score : int
            (range 0 - 5?)
        */

        final String sep = System.getProperty("file.separator");
        String testDir = ResourceFinder.findTestResourcesDirectory();
        String path = testDir + sep + "amazon_fine_food_reviews.csv";
        File f = new File(path);
        if (!f.exists()) {
            throw new IOException("could not find file at " + path);
        }

        String outPath = testDir + sep + "amazon_fine_food_reviews_sub_prod_sort.bin";

        int nCols = 10;

        String[] items;
        String productId;
        String userId;
        int score;

        byte[] bytes = null;
        FileReader reader = null;
        BufferedReader in = null;
        FileOutputStream fs = null;
        // write productId, userId, score to fs
        int i = 0;
        try {
            in = new BufferedReader(new FileReader(f));
            fs = new FileOutputStream(outPath);

            String line = in.readLine();
            if (line == null) {
                throw new IOException("could not read a line from " + path);
            }
            line = in.readLine();

            // java doesn't support conditional constructs, so cannot easily
            // insist that a group starting with a " must end with a " too.
            // TODO: improve parsing for quoted if need the text fields.  for now, not using thos
            //Pattern pattern = Pattern.compile("^(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*)$");
            while (line != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    break;
                }
                line = line.replaceAll("\"\"", "");

                items = line.split(",");

                productId = items[1];
                userId = items[2];

                if (items.length == nCols) {
                    score = Integer.parseInt(items[6]);
                } else {
                    //find 4 consecutive elements in items that are numbers, the 3rd is score
                    int nN = 0;
                    int j0 = -1;
                    int j = 3;
                    while (j < items.length && nN < 4) {
                        try {
                            Integer.parseInt(items[j]);
                            if (j0 == -1) {
                                j0 = j;
                            }
                            ++nN;
                        } catch (NumberFormatException ex) {
                            nN = 0;
                            j0 = -1;
                        }
                        ++j;
                    }
                    if (nN != 4) {
                        line = in.readLine();
                        ++i;
                        continue;
                    } else {
                        score = Integer.parseInt(items[j0 + 2]);
                    }
                }

                bytes = productId.getBytes(StandardCharsets.UTF_8);
                fs.write(bytes.length);
                fs.write(bytes);
                bytes = userId.getBytes(StandardCharsets.UTF_8);
                fs.write(bytes.length);
                fs.write(bytes);
                fs.write(score);

                if (i % 100 == 0) {
                    fs.flush();
                }

                ++i;
                line = in.readLine();
            }
        } finally {
            if (fs != null) {
                fs.flush();
                fs.close();
            }
            if (in != null) {
                in.close();
            }
            if (reader != null) {
                reader.close();
            }
        }

        /*
        // try to read from it
        FileInputStream fsIn = null;
        i = 0;
        int nB;
        try {
            fsIn = new FileInputStream(outPath);

            while (true) {
                nB = fsIn.read();
                if (nB == -1) {
                    break;
                }
                productId = new String(fsIn.readNBytes(nB), StandardCharsets.UTF_8);
                nB = fsIn.read();
                userId = new String(fsIn.readNBytes(nB), StandardCharsets.UTF_8);
                score = fsIn.read();
                ++i;
            }
        } finally {
            if (fsIn != null) {
                fsIn.close();
            }
        }
        */

    }

    /**
     * parse the original file, gather all entries, keyed by productId or userId, and then write them to file
     * by descending sort
     * @param sortByProduct if true sorts by number of product reviews, else sorts by number of user reviews
     * @throws IOException
     */
    public void writeSortedProductUseScoreFile(boolean sortByProduct) throws IOException {

        /*
        0     1        2       3            4                    5                   6     7    8       9
        Id,ProductId,UserId,ProfileName,HelpfulnessNumerator,HelpfulnessDenominator,Score,Time,Summary,Text

        reading in these features:

        col 1 is productId : string
        col 2 is user_id : string
        col 6 is score : int
            (range 0 - 5?)
        */

        final String sep = System.getProperty("file.separator");
        String testDir = ResourceFinder.findTestResourcesDirectory();
        String path = testDir + sep + "amazon_fine_food_reviews.csv";
        File f = new File(path);
        if (!f.exists()) {
            throw new IOException("could not find file at " + path);
        }

        // productId, lists of <userId, score>
        Map<String, List<String>> sMap = new HashMap<>();

        String outPath = testDir + sep;
        if (sortByProduct) {
            outPath = outPath + "amazon_fine_food_reviews_sub_prod_sort.bin";
        } else {
            outPath = outPath + "amazon_fine_food_reviews_sub_user_sort.bin";
        }

        int nCols = 10;

        String[] items;
        String productId;
        String userId;
        int score;

        byte[] bytes = null;
        FileReader reader = null;
        BufferedReader in = null;

        List<String> entries;
        // write productId, userId, score to fs
        int i = 0;
        try {
            in = new BufferedReader(new FileReader(f));

            String line = in.readLine();
            if (line == null) {
                throw new IOException("could not read a line from " + path);
            }
            line = in.readLine();

            // java doesn't support conditional constructs, so cannot easily
            // insist that a group starting with a " must end with a " too.
            // TODO: improve parsing for quoted if need the text fields.  for now, not using thos
            //Pattern pattern = Pattern.compile("^(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*)$");
            while (line != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    break;
                }
                line = line.replaceAll("\"\"", "");

                items = line.split(",");

                productId = items[1];
                userId = items[2];

                if (items.length == nCols) {
                    score = Integer.parseInt(items[6]);
                } else {
                    //find 4 consecutive elements in items that are numbers, the 3rd is score
                    int nN = 0;
                    int j0 = -1;
                    int j = 3;
                    while (j < items.length && nN < 4) {
                        try {
                            Integer.parseInt(items[j]);
                            if (j0 == -1) {
                                j0 = j;
                            }
                            ++nN;
                        } catch (NumberFormatException ex) {
                            nN = 0;
                            j0 = -1;
                        }
                        ++j;
                    }
                    if (nN != 4) {
                        line = in.readLine();
                        ++i;
                        continue;
                    } else {
                        score = Integer.parseInt(items[j0 + 2]);
                    }
                }

                if (sortByProduct) {
                    entries = sMap.get(productId);
                    if (entries == null) {
                        entries = new ArrayList<String>();
                        sMap.put(productId, entries);
                    }
                    entries.add(String.format("%s,%d", userId, score));
                } else {
                    entries = sMap.get(userId);
                    if (entries == null) {
                        entries = new ArrayList<String>();
                        sMap.put(userId, entries);
                    }
                    entries.add(String.format("%s,%d", productId, score));
                }

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

        // count productId entries and sort descending by count
        if (sortByProduct) {
            System.out.printf("number of unique productIds = %d\n", sMap.size());
        } else {
            System.out.printf("number of unique userIds = %d\n", sMap.size());
        }
        String[] p = sMap.keySet().toArray(new String[sMap.size()]);
        int[] nP = new int[p.length];
        int[] iP = new int[p.length];
        for (i = 0; i < p.length; ++i) {
            nP[i] = sMap.get(p[i]).size();
            iP[i] = i;
        }
        MiscSorter.sortByDecr(nP, iP);
        int[] _nP = Arrays.copyOf(nP, 100);
        if (sortByProduct) {
            System.out.printf("top 100 number of reviews for each product: %s\n", Arrays.toString(_nP));
        } else {
            System.out.printf("top 100 number of reviews by each user: %s\n", Arrays.toString(_nP));
        }

        FileOutputStream fs = null;
        try  {
            fs = new FileOutputStream(outPath);

            if (sortByProduct) {
                for (i = 0; i < nP.length; ++i) {
                    productId = p[iP[i]];
                    entries = sMap.get(productId);
                    for (String entry : entries) {
                        items = entry.split(",");
                        userId = items[0];
                        score = Integer.parseInt(items[1]);

                        bytes = productId.getBytes(StandardCharsets.UTF_8);
                        fs.write(bytes.length);
                        fs.write(bytes);
                        bytes = userId.getBytes(StandardCharsets.UTF_8);
                        fs.write(bytes.length);
                        fs.write(bytes);
                        fs.write(score);
                    }
                }
            } else {
                for (i = 0; i < nP.length; ++i) {
                    userId = p[iP[i]];
                    entries = sMap.get(userId);
                    for (String entry : entries) {
                        items = entry.split(",");
                        productId = items[0];
                        score = Integer.parseInt(items[1]);

                        bytes = productId.getBytes(StandardCharsets.UTF_8);
                        fs.write(bytes.length);
                        fs.write(bytes);
                        bytes = userId.getBytes(StandardCharsets.UTF_8);
                        fs.write(bytes.length);
                        fs.write(bytes);
                        fs.write(score);
                    }
                }
            }

        } finally {
            if (fs != null) {
                fs.flush();
                fs.close();
            }
        }


        /*
        // try to read from it
        FileInputStream fsIn = null;
        i = 0;
        int nB;
        try {
            fsIn = new FileInputStream(outPath);

            while (true) {
                nB = fsIn.read();
                if (nB == -1) {
                    break;
                }
                productId = new String(fsIn.readNBytes(nB), StandardCharsets.UTF_8);
                nB = fsIn.read();
                userId = new String(fsIn.readNBytes(nB), StandardCharsets.UTF_8);
                score = fsIn.read();
                ++i;
            }
        } finally {
            if (fsIn != null) {
                fsIn.close();
            }
        }
        */

    }

}
