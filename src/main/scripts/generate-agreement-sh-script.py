# this program generates a shell script that calls a perl inter-annotator agreement script one time for each
# pair of annotators for a given timebank file
# (annotators are selected from the pool of annotators that submitted an annotation file for the timebank file in question)
# the results of the perl agreement script are written to a directory for further processing by generate-agreement-stats.py

import sys
import os
import re

re_annotation_line = re.compile('[et]\d+\t[et]\d+\t(b|a|i|ii|s|v)\n')

def get_diffs(annoFileDir, fname, annotator1, annotator2):
    lines1 = open(annoFileDir + '/' + fname + '.' + annotator1).readlines()
    lines2 = open(annoFileDir + '/' + fname + '.' + annotator2).readlines()
    pairs1 = [(line.split('\t')[0], line.split('\t')[1]) for line in lines1]
    pairs2 = [(line.split('\t')[0], line.split('\t')[1]) for line in lines2]
    setPairs1 = set([])
    duplicates1 = []
    for pair in pairs1:
        if pair in setPairs1:
            duplicates1.append(pair)
        else:
            setPairs1.add(pair)
    setPairs2 = set([])
    duplicates2 = []
    for pair in pairs2:
        if pair in setPairs2:
            duplicates1.append(pair)
        else:
            setPairs2.add(pair)
    in1not2 = setPairs1.difference(setPairs2)
    in2not1 = setPairs2.difference(setPairs1)

    print annotator1 +':'
    print 'duplicates:'
    print duplicates1
    print '\tpairs ' + annotator2 + ' missed:'
    print in1not2
    
    print annotator2 +':'
    print 'duplicates:'
    print duplicates2
    print '\tpairs ' + annotator1 + ' missed:'
    print in2not1

def sh_file_line(agmtScriptPath, annoDir, outDir, annotator1, annotator2, fname):
    anno1Path = annoDir + '/' + fname + '.' + annotator1
    anno2Path = annoDir + '/' + fname + '.' + annotator2
    outPath   = outDir + '/' + fname + '.' + annotator1 + '.' + annotator2

    #line1 = line = "echo $(\"perl %s %s %s > %s\");\n" % (agmtScriptPath, anno1Path, anno2Path, outPath)
    line = "perl %s %s %s > %s;\n" % (agmtScriptPath, anno1Path, anno2Path, outPath)
    

    return line

def create_shell_script(shScriptPath, agmtScriptPath, annoFileDir, agmtOutputDir, fnameToAnnotators):
    out = open(shScriptPath, 'w')
    for fname in fnameToAnnotators:
        if len(fnameToAnnotators[fname]) == 1:
            print fname + ' was only annotated by ' + fnameToAnnotators[fname][0]
            continue
        for a1 in range(len(fnameToAnnotators[fname])):
            for a2 in range(len(fnameToAnnotators[fname])):
                if a1 >= a2: continue
                out.write( sh_file_line(agmtScriptPath, annoFileDir, agmtOutputDir,
                            fnameToAnnotators[fname][a1], fnameToAnnotators[fname][a2], fname) )

    out.close()

def populate_anno_dict(annoFnamesList, fnameToAnnotators):
    for _fname in annoFnamesList:
        items = _fname.split('.')
        fname = '.'.join(items[:-1])
        annotator = items[-1]
        try:
            fnameToAnnotators[fname].append(annotator)
        except:
            fnameToAnnotators[fname] = [annotator]


def check_annotation_documents_for_duplicates(annoFileDir, fnameToAnnotators):
    print "checking for annotation files with duplicate annotations..."
    for fname in fnameToAnnotators:
        for annotator in fnameToAnnotators[fname]:
            annoFile = open(annoFileDir + '/' + fname + '.' + annotator)
            annoLines = annoFile.readlines()
            annoFile.close()
            pairToAnnotations = {}
            for line in annoLines:
                items = line.strip().split('\t')
                pair = (items[0], items[1])
                #debug print items
                label = items[2]
                try:
                    pairToAnnotations[pair].append(label)
                except:
                    pairToAnnotations[pair] = [label]
                    
            for pair in pairToAnnotations:
                if len(pairToAnnotations[pair]) > 1:
                    print "Pair %s in %s.%s has the following annotations:" % (str(pair), fname, annotator)
                    print pairToAnnotations[pair]
                    print '\n'
                
def check_annotation_documents_for_mismatching_pairs(annoFileDir, annoOriginalDir, fnameToAnnotators):
    print "checking for inconsistancies in pair labeling - checking for omitted/added annotations against original pairs for each file/annotator"
    for fname in fnameToAnnotators:
        for annotator in fnameToAnnotators[fname]:
            annoFile = open(annoFileDir + '/' + fname + '.' + annotator)
            annoLines = annoFile.readlines()
            annoFile.close()
            pairs = set([(line.split('\t')[0], line.strip().split('\t')[1]) for line in annoLines])
            annoOriginalFile = open(annoOriginalDir + '/' + fname + '.tml')
            annoOriginalLines = annoOriginalFile.readlines()
            annoOriginalFile.close()
            originalPairs = set([(line.split('\t')[0], line.strip().split('\t')[1]) for line in annoOriginalLines])
            # pairs missing from original
            missingFromAnnotation = originalPairs.difference(pairs)
            # pairs not in original
            addedInAnnotation = pairs.difference(originalPairs)
            if missingFromAnnotation:
                print "%s.%s is missing:" % (fname, annotator)
                print missingFromAnnotation
            if addedInAnnotation:
                print "%s.%s contains the extraneous pairs:" % (fname, annotator)
                print addedInAnnotation
            

def check_annotation_documents_for_proper_formatting(annoFileDir, fnameToAnnotators):
    print "checking for annotation files for proper formatting"
    for fname in fnameToAnnotators:
        for annotator in fnameToAnnotators[fname]:
            annoFile = open(annoFileDir + '/' + fname + '.' + annotator)
            annoLines = annoFile.readlines()
            annoFile.close()
            newLines = []
            FIX_FILE = False
            FIXABLE = True
            for line in annoLines:
                if not re_annotation_line.match(line):
                    FIX_FILE = True
                    line = line.replace('\t', ' ')
                    line = re.sub("\s+"," ",line)
                    line = '\t'.join(line.split())
                    line = line.strip() + '\n'
                    if re_annotation_line.match(line):
                        newLines.append(line)
                    else:
                        return line
                        FIXABLE = False
                else:
                    #print line
                    newLines.append(line)
            if FIX_FILE and FIXABLE:
                print "%s.%s is fixable by changing all delims to tab - fixing automatically" % (fname, annotator)
                out = open(annoFileDir + '/' + fname + '.' + annotator,'w')
                out.writelines(newLines)
                out.close()
            elif FIX_FILE and not FIXABLE:
                print "%s needs to be fixed by %s" % (fname, annotator)
                

annoFileDir = sys.argv[1]
annoOriginalDir = sys.argv[2]
agmtOutputDir = sys.argv[3]
agmtScriptPath = sys.argv[4]
shScriptPath = sys.argv[5]
annoFnamesList = os.listdir(annoFileDir)
fnameToAnnotators = {}

######################################################

# Populate dictionary that maps each document name to the set of pairs of annotators who annotated the document
populate_anno_dict(annoFnamesList, fnameToAnnotators)

# create a shell script that gets inter-annotator agreement and document stats for each document for each pair,
# by calling the agreement script
create_shell_script(shScriptPath, agmtScriptPath, annoFileDir, agmtOutputDir, fnameToAnnotators)

#check for proper formatting, fix if possible
check_annotation_documents_for_proper_formatting(annoFileDir, fnameToAnnotators)

#check for duplicate annotations
check_annotation_documents_for_duplicates(annoFileDir, fnameToAnnotators)

#check to see if any pairs were missed or added during annotation
x=check_annotation_documents_for_mismatching_pairs(annoFileDir, annoOriginalDir, fnameToAnnotators)

'''
#check to see if any files vary in number of lines across annotator
annotatorToNumLines = {}
for fname in fnameToAnnotators:
    annotatorToNumLines[fname] = {}
    for annotator in fnameToAnnotators[fname]:
        lines = open(annoFileDir + '/' + fname + '.' + annotator).readlines()
        annotatorToNumLines[fname][annotator] = len(lines)

for fname in annotatorToNumLines:    
    annoNumSet = set([annotatorToNumLines[fname][annotator] for annotator in annotatorToNumLines[fname]])
    numUniqueAnno = len(annoNumSet)
    if numUniqueAnno != 1:
        print 'inconsistent annotation numbers: ' + fname
        print annotatorToNumLines[fname]
        get_diffs(annoFileDir, fname, 'cassidy', 'mcdowell')

'''
