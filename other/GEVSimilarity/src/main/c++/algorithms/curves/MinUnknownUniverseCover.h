/* 
 * File:   MinimumUnknownUniverseCover.h
 * Author: nichole
 * 
 * <pre>
 * Given sets of similar curves which were assigned variable numbers for the 
 * unique parameters of {k, sigma, and mu}, the code finds the smallest set of 
 * the variable numbers which represent all curves.
 * 
 * For example, similar curves are listed in a row and the parameter sets 
 * are the numbers:
 * 1  2  3
 * 1
 *         4 5 6
 *           5
 * The smallest set of parameter sets representing all curves in the example
 * would be {1, 5}.
 * 
 * The "Universe" of the solution set is not known ahead of time, because
 * the goal is to only use 1 set from each row in the final "Universe" of sets.
 * 
 * runtime complexity is estimated as O(N lg_2(N)) + approx O(N^2 * m)
 * where m is the average number of variables in a row.
 * 
 * </pre>
 *
 * Created on April 27, 2014
 */
#ifndef ALGORITHMS_CURVES_MINUKNOWNUNIVERSECOVER_H
#define ALGORITHMS_CURVES_MINUKNOWNUNIVERSECOVER_H

#include <vector>
#include <tr1/unordered_map>
#include "Defs.h"

using std::vector;
using std::tr1::unordered_map;

namespace gev {
class MinUnknownUniverseCover {

public:
    MinUnknownUniverseCover();
    
    virtual ~MinUnknownUniverseCover();
    
    void calculateCover(const vector<vector<int> >* inputVariables,
        vector<int>* outputCoverVariables);
        
    /*
     read inputVariables to populate the count frequency map 
     outVariableFrequencyMap.
     runtime is O(nRows * m) where m is the average number of variables per row.
     
     @param inputVariables rows of input variable numbers
     @param outVariableFrequencyMap the output is a map with key=variable number
     * and value is the number of times that variables is in inputVariables.
     */
    void _populateVariableFrequencyMap(
        const vector<vector<int> >* inputVariables,
        unordered_map<int, int> *outVariableFrequencyMap);
    
    /*
     read the variable count frequency from inputVariableFrequencyMap to 
     populate outputCoverVariables, ordered by decreasing count frequency.
     runtime is O(n) + O(n lg_2(n)) where n is the number of keys in the map.
     @param inputVariableFrequencyMap map with key=variable number and value is 
     the variable count
     @param outputCoverVariables list of the inputVariableFrequencyMap ordered 
     by decreasing count.  
     */
    void _initializeVariableCover(
        const unordered_map<int, int> *inputVariableFrequencyMap, 
        vector<int>* outputCoverVariables);
    
    /*     
     runtime is O(nRows * nRows * m) where m is the avg number of variables 
     per row.
     @param inputVariables rows of input variable numbers where each row 
     represents curves that are similar and the variable numbers represent
     parameters that produced a similar curve in that row.
     @param variableFrequencyMap map with key=variable number and value is 
     the variable count.  Note that outVariableFrequencyMap gets modified in
     this method as it is re-used for another purpose.
     @param outputCoverVariables output list of variables that represent
     the minimum number of variables which can generate all curves in
     inputVariables.
     */
    void _findMinRepresentativeCover(const vector<vector<int> >* inputVariables, 
        unordered_map<int, int> *variableFrequencyMap,
        vector<int>* outputCoverVariables);
    
private:
    DISALLOW_COPY_AND_ASSIGN(MinUnknownUniverseCover);
        
};
} // end namespace
#endif