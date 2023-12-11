package com.climbwithyourfeet.clustering;

import algorithms.util.ResourceFinder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * reads the file and extracts only productId, userId, and score and
 * writes those into other csv files, depending upon sort chosen or not.
 */
public class AmazonFoodReviewsReaderWriter {

    /*
    NOTE: if need the results to be "real-time", can build methods using Apache datasketches API's ItemSketch which
    is a frequent items algorithm, and/or set membership query algorithms like the Bloom Filter or Ribbon Filter.
    Also, for building a sparse utility matrix and using the top k eigenvectors associated with the largest eigenvalues
    for use in projection (dimension reduction), can use the JAX API for sparse matrices.
    The publicly available JAX is in python currently.
     */

    private static final int nEntries = 568454;

    private static final String sep = System.getProperty("file.separator");
    private static final String eol = System.getProperty("line.separator");
    private static final String testDir;
    public static final String filePath0;
    public static final String filePathCleaned;
    public static final String filePathCleanedSortProd;
    public static final String filePathCleanedDegSortProd;
    public static final String filePathProdUserScoreSortProd;
    static {
        try {
            testDir = ResourceFinder.findTestResourcesDirectory();
            filePath0 = testDir + sep + "amazon_fine_food_reviews.csv";
            filePathCleaned = testDir + sep + "amazon_fine_food_reviews_cleaned.csv";
            filePathCleanedSortProd = testDir + sep + "amazon_fine_food_reviews_cleaned_sort_prod.csv";
            filePathCleanedDegSortProd = testDir + sep + "amazon_fine_food_reviews_cleaned_deg_sort_prod.csv";
            filePathProdUserScoreSortProd = testDir + sep + "amazon_fine_food_reviews_cleaned_sort_prod_pr_us_sc.csv";
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalStateException("cannot find test resources directory");
        }
    }

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

    /**
     * read the file amazon_fine_food_reviews.csv, remove redundant entries and write output files.
     *
     * Note that no text changes for canonicalization, etc have been performed in the cleaning as the text
     * and summary aren't used currently for the single test using the file in this project.
     *
     * removing 171865 redundant entries.
     *
     * writes output files:
     *    amazon_fine_food_reviews_cleaned.csv
     *    amazon_fine_food_reviews_cleaned_sort_product.csv
     <pre>
     What this method does:
      - finds redundant entries to define product groups
      - makes an alias map for the product group, choosing the first member to be the canonical product name
      - renames productids to the canonical name and removes redundancies
      - write the remaining entries as amazon_fine_food_reviews_cleaned.csv
      - also writes file:
         -- entries sorted by productId.  write the file as amazon_fine_food_reviews_cleaned_sort_product.csv
     </pre>
     * @throws IOException thrown for missing input file or other file I/O exceptions
     */
    public void writeCleanedFile() throws IOException {

        /*
        0     1        2       3            4                    5                   6     7    8       9
        Id,ProductId,UserId,ProfileName,HelpfulnessNumerator,HelpfulnessDenominator,Score,Time,Summary,Text

        reading in these features:

        col 1 is productId : string
        col 2 is user_id : string
        col 6 is score : int
            (range 0 - 5?)
        */

        File f = new File(filePath0);
        if (!f.exists()) {
            throw new IOException("could not find file at " + filePath0 +
                    " Are you missing the original amazon fine foods review file amazon_fine_food_reviews.csv?  " +
                    " You can download it at https://www.kaggle.com/datasets/snap/amazon-fine-food-reviews/" +
                    " and place it in src/test/resources.  The file size is 287MB.");
        }

        String[] items;
        String userId, prodId;

        Map<String, Set<String>> trLineProdIdMap = new HashMap<>();
        Set<String> prodIds;
        BufferedReader in = null;
        int i = 0;
        try {
            in = new BufferedReader(new FileReader(f));

            String line = in.readLine();
            if (line == null) {
                throw new IOException("could not read a line from " + filePath0);
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

                if (items[items.length-1].trim().isEmpty() && items[items.length-2].trim().isEmpty()) {
                    // this is not reached.  all have at least summary
                    ++i;
                    line = in.readLine();
                    continue;
                }

                prodId = items[1];
                userId = items[2];

                // trim off first 3 items
                int i0 = line.indexOf(userId) + userId.length();
                line = line.substring(i0, line.length() - 1);

                prodIds = trLineProdIdMap.get(line);
                if (prodIds == null) {
                    prodIds = new HashSet<String>();
                    trLineProdIdMap.put(line, prodIds);
                }
                prodIds.add(prodId);

                ++i;
                line = in.readLine();
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }

        Map<String, String> productIdAliasMap = new HashMap<>();
        Iterator<Map.Entry<String, Set<String>>> iter = trLineProdIdMap.entrySet().iterator();
        Map.Entry<String, Set<String>> entry;
        String[] ps;
        long nRemoving = 0;
        while (iter.hasNext()) {
            entry = iter.next();
            //trLine = entry.getKey();
            prodIds = entry.getValue();
            if (prodIds.size() == 1) {
                continue;
            }
            ps = prodIds.toArray(new String[prodIds.size()]);
            Arrays.sort(ps);
            for (String pId : ps) {
                productIdAliasMap.put(pId, ps[0]);
            }
            nRemoving += (ps.length - 1);
        }

        // allow gc to reclaim this memory:
        trLineProdIdMap = null;

        //removing 171865 redundant entries
        System.out.printf("removing %d redundant entries\n", nRemoving);
        System.out.flush();

        // rename productIds using alias map and skip any entries already in Set<String> trLineSet
        // then perform the equiv of 'cat file | sort | uniq'

        Set<String> lineSet = new HashSet<>();
        String p0Id;
        String trLine;

        BufferedWriter out = null;
        i = 0;
        try {
            in = new BufferedReader(new FileReader(f));
            out = new BufferedWriter(new FileWriter(filePathCleaned));

            String line = in.readLine();
            if (line == null) {
                throw new IOException("could not read a line from " + filePath0);
            }
            // write the comment line
            out.write(line);
            out.write(eol);

            line = in.readLine();

            while (line != null) {
                if (line.trim().isEmpty()) {
                    break;
                }
                items = line.split(",");

                prodId = items[1];

                p0Id = productIdAliasMap.get(prodId);
                if (p0Id != null && !p0Id.equals(prodId)) {
                    line = line.replaceFirst(prodId, p0Id);
                }

                // remove id string
                trLine = line.substring(line.indexOf(",") + 1);

                if (lineSet.contains(trLine)) {
                    ++i;
                    line = in.readLine();
                    continue;
                }
                lineSet.add(trLine);

                out.write(line);
                out.write(eol);

                ++i;
                line = in.readLine();
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (out != null) {
                out.flush();
                out.close();
            }
        }

    }

    public void writeSortedProductFileForCleanedInput() throws IOException {
        File f = new File(filePathCleaned);
        if (!f.exists()) {
            writeCleanedFile();
        }
        writeSortedFile(filePathCleaned, filePathCleanedSortProd, true);
        writeProductUseScoreFile(filePathCleanedSortProd, filePathProdUserScoreSortProd);
    }

    protected void writeProductUseScoreFile(String inFilePath, String outFilePath) throws IOException {

        /*
        0     1        2       3            4                    5                   6     7    8       9
        Id,ProductId,UserId,ProfileName,HelpfulnessNumerator,HelpfulnessDenominator,Score,Time,Summary,Text

        reading in these features:

        col 1 is productId : string
        col 2 is user_id : string
        col 6 is score : int
            (range 0 - 5?)
        */
        File f = new File(inFilePath);
        if (!f.exists()) {
            throw new IOException("could not find file at " + inFilePath);
        }

        int nCols = 10;

        String[] items;
        String productId;
        String userId;
        String score;

        byte[] bytes = null;
        BufferedReader in = null;
        BufferedWriter out = null;
        // write productId, userId, score to fs
        int i = 0;
        try {
            in = new BufferedReader(new FileReader(f));
            out = new BufferedWriter(new FileWriter(new File(outFilePath)));

            String line = in.readLine();
            if (line == null) {
                throw new IOException("could not read a line from " + inFilePath);
            }
            line = in.readLine();

            // java doesn't support conditional constructs, so cannot easily
            // insist that a group starting with a " must end with a " too.
            // TODO: improve parsing for quoted if need the text fields.  for now, not using those
            //Pattern pattern = Pattern.compile("^(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*),(\\S.*)$");
            //    TODO: improve on the pattern to use digits restriction in columns 0, 4,5,6,7
            while (line != null) {
                line = line.trim();
                if (line.isEmpty()) {
                    break;
                }
                line = line.replaceAll("\"\"", "");

                items = line.split(",");

                productId = items[1].trim();
                userId = items[2].trim();

                if (items.length == nCols) {
                    score = items[6].trim();
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
                        // doesn't reach here
                        line = in.readLine();
                        ++i;
                        continue;
                    } else {
                        score = items[j0 + 2].trim();
                    }
                }

                out.write(productId);
                out.write(",");
                out.write(userId);
                out.write(",");
                out.write(score);
                out.write(eol);

                ++i;
                line = in.readLine();
            }
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
            if (in != null) {
                in.close();
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
     * @param inFilePath path to file containing full line entries
     * @param outFilePath path to write file sorted full line entries
     * @param sortByProduct if true sorts by number of product reviews, else sorts by number of user reviews
     * @throws IOException
     */
    private void writeSortedFile(String inFilePath, String outFilePath, boolean sortByProduct) throws IOException {

        /*
        0     1        2       3            4                    5                   6     7    8       9
        Id,ProductId,UserId,ProfileName,HelpfulnessNumerator,HelpfulnessDenominator,Score,Time,Summary,Text

        reading in these features:

        col 1 is productId : string
        col 2 is user_id : string
        col 6 is score : int
            (range 0 - 5?)
        */

        File f = new File(inFilePath);
        if (!f.exists()) {
            throw new IOException("could not find file at " + inFilePath +
                    " Are you missing the original amazon fine foods review file?  " +
                    " You can download it at https://www.kaggle.com/datasets/snap/amazon-fine-food-reviews/" +
                    " and place it in src/test/resources.  The file size is 287MB.");
        }

        /*
        map with key = productId, value = line
        or
        map with key = userId, value = line

        using a Red-Black tree to sort by natural ordering of keys
        */
        SortedMap<String, List<String>> sMap = new TreeMap<>();

        String[] items;
        String key;

        BufferedReader in = null;
        String commentLine = null;

        List<String> lines;
        int i = 0;
        try {
            in = new BufferedReader(new FileReader(f));

            String line = in.readLine();
            if (line == null) {
                throw new IOException("could not read a line from " + inFilePath);
            }
            commentLine = line;
            line = in.readLine();

            while (line != null) {
                if (line.trim().isEmpty()) {
                    break;
                }
                items = line.replaceAll("\"\"", "").split(",");

                if (sortByProduct) {
                    key = items[1];
                } else {
                    key = items[2];
                }
                lines = sMap.get(key);
                if (lines == null) {
                    lines = new ArrayList<String>();
                    sMap.put(key, lines);
                }
                lines.add(line);

                ++i;
                line = in.readLine();
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }

        BufferedWriter out = null;
        try  {
            out = new BufferedWriter(new FileWriter(new File(outFilePath)));

            out.write(commentLine);
            out.write(eol);

            for (Map.Entry<String, List<String>> stringListEntry : sMap.entrySet()) {
                lines = stringListEntry.getValue();
                for (String line : lines) {
                    out.write(line);
                    out.write(eol);
                }
            }

        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }

    }

    public void writeSortedDegeneracyFileForCleanedInput() throws IOException {
        File f = new File(filePathCleaned);
        if (!f.exists()) {
            writeCleanedFile();
        }
        writeDegeneracySortedFile(filePathCleaned, filePathCleanedDegSortProd, true);
        writeProductUseScoreFile(filePathCleanedSortProd, filePathProdUserScoreSortProd);
    }

    private void writeDegeneracySortedFile(String filePathCleaned, String filePathCleanedDegSortProd, boolean b) {
        throw new UnsupportedOperationException("not yet implemented");
    }

}
