density based clustering using a distance transform
http://nking.github.io/two-point-correlation/
================================================================

Find clusters in data in an unsupervised manner by calculating 
the distance transform to nearest points and the critical
two-point density from that.
The critical surface density combined with a factor above background
is used to define the critical separation of two points,
below which they are members of the same cluster.

Note that the distance transform provides information on the 
separation of background points from dataset points and the frequency 
of the largest separations are what is used to characterize the 
background surface density and hence a critical surface density
from that.
(NOTE that when the N^2 approach of determining the distance between
dataset pairs of points is used instead of a distance transform 
the distribution fits a generalized extreme value curve 
which would be expected when the background points are randomly
placed in x,y, that is a poisson distribution.  The peak of that 
GEV (which is the distribution of the distance between points 
spatially) gives the critical density from which a critical 
separation is determined.  Using the distance transform instead
of all pairwise calculations results in surface density frequences 
which are sparse biased samples of the GEV trnsformed to 
surface densities, but still provide the peak at 
lowest surface densities.);

NOTE that the project assumes that the data have proportial
separations for each axis - one critical separation 
is used for both axes.
One could use "standard unit normalization" from the included
shared library to transform the points before use: 
    algorithms.misc.Standardization.standardUnitNormalization()
    (if class is not present yet, it will be soon after a project update).
    and then scale the result to integer values or use another standardization
    technique.
Note that the project acts upon 2 dimensions.  For d-dimensions, use of
this would have a runtime of roughly O(N_dimension_area^(d*(d-1)/2)).
Note also that the distance transform is not ideal for sparse datasets
that span a large amount of space as algorithms depending upon the number of
points instead of dimension lengths of dataset may perform better in that case.

Usage as an API:

    DTClusterFinder clusterFinder = new DTClusterFinder(points,
        imageWidth, imageHeight);
                
    clusterFinder.setToDebug();

    clusterFinder.calculateCriticalDensity();

    // or, set density instead of calculate (for use in pca or lda, for example):
    //clusterFinder.setCriticalDensity(dens);

    clusterFinder.findClusters();

    int nGroups = clusterFinder.getNumberOfClusters();

    // the groups as pixel indexes:
    List<TIntSet> groupListPix = clusterFinder.getGroups();

-----
Build
-----
The project requires java 1.7 or greater.
The ant version should be 1.9.6 or greater.
The jacoco version should be jacoco-0.7.5.201505241946 or greater.
    http://www.jacoco.org/jacoco/
    Then set an environment variable called JACOCO_HOME
    to the path of the base direcotry of jacoco.
    The build scripts looks for $JACOCO_HOME/lib/jacocoant.jar
The other libraries are contained in the project lib directory.

To list the targets:
  ant

To build and package just the project code:
  ant package

To compile the main source code:
  ant compile

To run all tests:
  ant runTests

To run a specific test:
  ant runTest -Dtest=package.TestName

Note, if you want to make code coverage reports, the runCorverage
target requires you to have downloaded Jacoco, and placed it at a
location referred to by environment variable JACOCO_HOME.
http://eclemma.org/download.html

-------------------------
Performance Metrics
-------------------------

The runtime complexity is roughly O(Npixels X log2(Npixels)) where 
Npixels is the width times height of the image. 
The space complexity is roughly platform word size X width X height, 
so for a width of 5000 and height of 5000, the code must be run with 
java arguments to increase the stack size or the data must be reduced 
in size... like knapsack, the code is using dynamic programmining 
using arrays that are as long as needed for capacity.

----------------------
Numerical Resolution
----------------------

floating point data can be converted to integers and scaled to keep the
desired numerical resolution before using this code.

For example, for data where one has an interest in exponential 
similarity functions, one would want to apply the exponential operations 
to the data before use here and scale the data for numerical resolution 
such that an integer holds the significant difference between points
    exp(a - b) is exp(a)/exp(b) 

----------------------
Citation and Licensing
----------------------
The citation for use of this code in a publication is:

For use before Sep, 2015 
    http://code.google.com/p/two-point-correlation/, 
    Nichole King,  "Unsupervised Clustering Based Upon Voids in Two-Point Correlation". March 15, 2013.

else
    https://github.com/nking/two-point-correlation
    Nichole King,  "Unsupervised Density Based Clustering Using Distance Transforms". September 25, 2015.

The code is licensed under the MIT license, usable for commercial and non-commercial purposes:
    http://opensource.org/licenses/mit-license.php
    see LICENSE.txt in this project
