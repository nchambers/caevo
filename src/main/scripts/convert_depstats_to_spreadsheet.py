'''
    This file produces the following
    
    1) A list of all cases - feature value in each column
     (if you uncomment the "write all cases to file" block
    2) Distribution by feature profile for each specified feature profile
'''

import sys
import re
from collections import OrderedDict
from collections import Counter
import argparse



################################
######## DATA METHODS ##########
################################

#global variable for labels :-(
labels='VAGUE BEFORE AFTER INCLUDES IS_INCLUDED SIMULTANEOUS'.split()

def getProfileDict(profileDictFname, profileDict):
    for line in open(profileDictFname):
        items = line.strip().split('|')
        profileDict[items[0]] = items[1].split()

goldvalue = re.compile('gold:(\S+)')
def get_gold(line):
    """
    Returns the gold label for a feature line
    """
    return goldvalue.search(line).group(1)


#
def get_profile(line, listOfFeatures):
    """
        Returns a tuple of feature values for the profile in question.
        line is a string that contains key:value pairs separated by whitespace.
        Each key is a feature and each value is its value.
        profile is a list of features (keys).
    """
    
    # Convert line to a dictionary using the key:value pairs
    items = line.strip().split()[1:]
    lineDict = OrderedDict([(item.split(':')[0],item.split(':')[1])
                     for item in items])
    
    # lineProfileList will be populated with feature values
    # only print out those feature values that are part of the subset
    # requested, specified in profileDict[profile]
    lineProfileList = []
    
    for key in listOfFeatures:
        lineProfileList.append(lineDict[key])

    # We must typecast the list of requested feature values
    # as a tuple since lists are not hashable,
    # and we want to count the number of times a given
    # list of values occurs in our examples.
    return tuple(lineProfileList)


def colToVal(col, mapping):
    try:
        return '{%s}' % (','.join(mapping[col]))
    except:
        return 'string'

def quoteWekaValue(string):
    specialchars = ["'", ","]
    for char in specialchars:
        if char in string:
            return '"' + string + '"'
    return string

################################
########## FILE STUFF ##########
################################


def populateFeatureToValueMapping(colToVals):
    """
    """
    for line in lines:
        items = line.strip().split()
        lineDict = OrderedDict([(item.split(':')[0],quoteWekaValue(item.split(':')[1]))
                                for item in items[1:]])
        for key in lineDict:
            try:
                colToVals[key].add(lineDict[key])
            except:
                colToVals[key] = set([lineDict[key]])

def writeWekaDataToFile(wekaOutputFname, datasetName, cols, lines):
    # write all cases to a file - weka format
    out_weka = open(wekaOutputFname,'w')
    # first write dataset name name
    out_weka.write('@RELATION %s\n\n' % (datasetName))
    # next write all attribute tags
    # iterate over feature names. use colToVals to get the value setting.
    # if the variable is nominal, you must give the list of all possible values, each value separated by commas,
    # surrounded by curly braces. (this list should have been obtained automatically or hard-coded)
    for col in cols:
        out_weka.write('@ATTRIBUTE ' + col + ' ' + colToVal(col, cols) + '\n')
    
    # write data to weka file
    out_weka.write('\n@DATA\n')
    for line in lines:
        # get items in the line; after first item each item is of the form
        # feature:value
        items = line.strip().split()
        
        # write all feature values to a tab delimited file
        values = [item.split(':')[1] for item in items[1:]]
        for v in range(len(values)):
            if values[v] == '': values[v] = '?'    # missing values should be replaced with ?
            values[v] =  quoteWekaValue(values[v]) # if any value has a comma replace is with a |
        out_weka.write(','.join(values)+'\n')
    
    out_weka.close()


def writeAllCasesToFile(allCasesOutputFname, cols, lines):
    # write all cases to a file (tab-delimited)
    out_allCases = open(allCasesOutputFname,'w')
    # First write the names of the columns
    out_allCases.write('\t'.join(cols) + '\n')
    #
    for line in lines:
        # get items in the line; after first item each item is of the form
        # feature:value
        items = line.strip().split()
        
        # write all feature values to the output file
        values = [item.split(':')[1] for item in items[1:]]
        out_allCases.write('\t'.join(values)+'\n')

    out_allCases.close()


def writeProfileDistributionsToFiles(ouptutPath, profileDict, profileCounts):
    """
    """
    for profile in profileDict:
        # each element has a tuple,
        # the first tuple-element contains the feature values (itself a tuple)
        # for the features specified in profile, for a given line (example);
        # the second element contains the gold label for a given line.
        profileGoldList = []
        
        for line in lines:
            profileGoldList.append((get_profile(line, profileDict[profile]),get_gold(line)))
        
        # get distribution information for each attested feature value combination
        profileCounts[profile] = {}.fromkeys([pg[0] for pg in profileGoldList])
        for p in profileCounts[profile]:
            profileCounts[profile][p] = OrderedDict.fromkeys(labels,0)
        for pg in profileGoldList:
            p, g = pg[0], pg[1]
            profileCounts[profile][p][g] += 1
        
        
        out = open(ouptutPath+'/'+profile, 'w')
        out.write('\t'.join(profileDict[profile]) + '\t' + '\t'.join(labels) + '\tCount\n')
        for p in profileCounts[profile]:
            out.write('\t'.join(p))
            
            p_total = sum(profileCounts[profile][p].values())
            for g in profileCounts[profile][p]:
                out.write('\t' + str(profileCounts[profile][p][g]/float(p_total)))
            
            out.write('\t' + str(p_total) + '\n')
        out.close()


def getExampleLines(examplesFname):
    file = open(examplesFname)    # This is the input file
    lines = file.readlines()
    return lines

# Names of the features (columns)
def getFeatureNames(lines):
    """
    Get feature names from the first line of the examples
    """
    cols = [item.split(':')[0] for item in lines[0].strip().split()[1:]]
    return cols






#######################################
########## DECLARE VARIABLES ##########
#######################################

parser = argparse.ArgumentParser()
parser.add_argument("--profilesFile", help="specify path to (input) file mapping profile name to space delimited list of features (name| f1 f1 ...)")
parser.add_argument("--examplesFileIn", help="(input) file containing examples; format is featureSetName f1:v1 f2:v2 ...")
parser.add_argument("--examplesFileOut", help="(output) file path for excel friendly file with all feature values for all examples")
parser.add_argument("--isoFeaturesPath", help="path to put excel friendly files that contain all cases, but for only a subset of features (as specified in profilesFile")
parser.add_argument("--wekaFileName", help="path to write weka file")

args = parser.parse_args()

profileDictFname = args.profilesFile
examplesFname = args.examplesFileIn
allCasesOutputFname = args.examplesFileOut
outputPath = args.isoFeaturesPath
wekaOutputFname = args.wekaFileName


# profileDict maps a name of a profile to the features that are requested in output





 # this will map eatch feature to its list of possible values, i.e. those attested in the dataset
# this can be overridden (say, if you want to learn a model and apply smoothing)







lines = getExampleLines(examplesFname)

cols = getFeatureNames(lines)


if allCasesOutputFname:
    writeAllCasesToFile(allCasesOutputFname, cols, lines)

profileDict = {}
if profileDictFname:
    getProfileDict(profileDictFname, profileDict)

profileCounts = OrderedDict().fromkeys(profileDict)

if outputPath and profileDict and profileCounts:
    writeProfileDistributionsToFiles(outputPath, profileDict, profileCounts)

if wekaOutputFname:
    colToValsDict = OrderedDict()
    populateFeatureToValueMapping(colToValsDict)
    writeWekaDataToFile(wekaOutputFname, 'depstats', colToValsDict, lines)












