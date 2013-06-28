import sys
import re
import xml.etree.ElementTree as ET

'''
    Input: xml file that represents corpus
    Assumption: the root node's immediate children are file nodes
    Assumption: the name of a document is stored under that file node
    as an attribute, called 'name'.
    
    Output: text documents ready for SENNA (or something else) to process
    
    High level process: for each file, write the each sentence to a new text file in
    the output directory. 
    
    We assume that in doing this, for a given sentence, the tokenization obtained by
    any annotation process that splits the line on the space character will be equal to the 
    tokenization provided in the xml file (by tokenization i mean the mapping from 
    integers to strings, where integers are sentence positions)
    
    Starting at 1 instead of 0 can be handled when the output of processing (for senna in this case)
    is dealth with, i.e. read by the system that cares about the annotation process.
'''

# this regex matches text of <t> element in
# the xml format in which we are currently storing timebank annotation
re_get_token = re.compile('"\s*"\s+"(.*?)"')

# input xml file
inputXmlFilePath = sys.argv[1]
# output directory to put text tiles, one for each document
outputSrlDirectory = sys.argv[2]

# the name of the node that corresponds the ordered list of token
# in a given sentence
tokensNodeName = '{http://chambers.com/corpusinfo}tokens'

# parse the xml file and get its root
tree = ET.parse(inputXmlFilePath)
root = tree.getroot()


# for each file node...
# create an output file
# for each tokens node under the file node...
# write its children's text (t nodes) to the output file
# separated by the space character
# for the last token, the space is instead a \n

for file in root:
    out = open(outputSrlDirectory + '/' + file.attrib['name'] + '.txt','w')
    for tokens in file.iter(tokensNodeName):
        for t in tokens[:-1]:
            out.write(re_get_token.match(t.text).group(1) + ' ')
        out.write(re_get_token.match(tokens[-1].text).group(1) + '\n')
    out.close()

