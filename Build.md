# Build #

The project uses ant to build.  You'll need to have installed on your computer java and ant.
The other libraries are contained in the project.

To list the targets:
```
  ant
```

To build:
```
  ant compile
```

To run the tests:
```
  ant runTests
```

# Use the API with default settings: #

```
  TwoPointCorrelation clusterFinder = new TwoPointCorrelation(x, y, xErrors, yErrors, totalNumberOfPoints);
  
       clusterFinder.calculateBackground();
       
       clusterFinder.findClusters();
```

### Access the results: ###
```
     int n = clusterFinder.getNumberOfGroups()
      
      To get the hull for groupId 0:
          ArrayPair hull0 = clusterFinder.getGroupHull(0)

      To get the points in groupId 0:
          ArrayPair group0 = clusterFinder.getGroup(int groupNumber)
      
      To plot the results:
          String plotFilePath = clusterFinder.plotClusters();
```

If debugging is turned on, plots are generated and those file paths are printed to standard out, and statements are printed to standard out.


### To set the background density manually, before calculateBackground(): ###
```
  clusterFinder.setBackground(0.03f, 0.003f);
```


If the centers of the cluster hulls are needed for something else, seeds for a Voronoi diagram, for instance, one can use:
```
  ArrayPair seeds = clusterFinder.getHullCentroids();
```

### To choose between 2 methods of interpreting the histogram of 2-point densities instead of allowing the code to automatically determine it: ###
```
     useFindMethodForDataWithoutBackgroundPoints() or useFindMethodForDataWithBackgroundPoints()
```

# Use from the Command Line #

Requires a tab delimited text file with 4 columns: x  y  xErrors  yErrors where all numbers are read as floating point numbers.

```
  ant package
  
  java -jar dist/two-point-correlation.jar --file /path/to/file/fileName.txt

    optional flags:
       --twosigma
       --threesigma
       --background <value> (requires backgrounderror guesstimate at least)
       --backgrounderror <value>
```

Note that work on the histogram code is in progress.  Currently datasets with a small number of points may have less than ideal solutions.