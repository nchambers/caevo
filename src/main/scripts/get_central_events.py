'''

A halflink can be thought of as a pair (event, tlink_label).
Consider the annotation excerpt:
e1 before e2
e2 includes e3

normalized halflinks:
e1, before
e2, after
e2, includes
e3, included_in

Purpose of this program: get all halflinks and their counts from a timebank xml file
The HalfLinks takes an the timebank file as inputFname

HalfLinks.get_halfLinks() and HalfLinks.get_halfLink_counts() get the halfLinks and extract their counts in the attributes halfLinks and halfLinkCounts

The main usage is to print out examples of some class of halflink type.
For example, we might want to see what sort of events and time expressions are placed BEFORE a bunch of surrounding events and time expressions. What makes them different?

'''

### IMPORTS ###
import sys
from collections import OrderedDict
from collections import Counter
import xml.etree.ElementTree as ET

class HalfLinks:
	def __init__(self, inputFname = None, outputFname = None):
		self.inputFname = inputFname
		self.outputFname = outputFname
		self.entIdToText = {}
		self.entIdToSid = {}
		self.sidToSent = {}
		self.entIdToTlinks = {}
		self.halfLinks = []
		self.halfLinkCounts = OrderedDict()
	
	def get_halfLink_counts(self):
		self.halfLinkCounts = Counter(halfLinks)

	def get_root(self):
		tree = ET.parse(self.inputFname)
		root = tree.getroot()
		return root

	def inverse(self, label):
		if label == 'AFTER': return 'BEFORE'
		elif label == 'BEFORE': return 'AFTER'
		elif label == 'IS_INCLUDED': return 'INCLUDES'
		elif label == 'INCLUDES': return 'IS_INCLUDED'
		else: return label

	def get_full_id(self, docId, id):
		return '%s|%s' % (docId, id)

	def get_id_from_full(self, idFull):
		return idFull.split('|')[1]

	def get_docid_from_full(self, entIdFull):
		return entIdFull.split('|')[0]

	def get_eventText_from_fullEntId(self, entIdFull):
		return self.entIdToText[entIdFull]

	def get_sentText_from_fullEntId(self, entIdFull):
		return self.sidToSent[self.entIdToSid[entIdFull]]
	
	def get_sentText_from_fullSid(self, sidFull):
		return self.sidToSent[sidFull]
		
	def get_text_and_sent_from_full(self, entIdFull):
		sentAndText = ''
		sentAndText += '%s(entity text: %s)\n\n' % (entIdFull, 
									  self.get_eventText_from_fullEntId(entIdFull))
		sentAndText += self.get_sentText_from_fullEntId(entIdFull)
		return sentAndText

	def get_text_and_sent_with_context_from_full(self, entIdFull):
		sentAndText = ''
		sentAndText += '%s(entity text: %s)\n\n' % (entIdFull, 
									  self.get_eventText_from_fullEntId(entIdFull))
		docId = self.get_docid_from_full(entIdFull)
		# get sent where entity is mentioned, and previous and following sentences
		# (provided that they exist)
		
		# first get add previous sentence to sentAndText
		sentIdInt = int(self.get_id_from_full(self.entIdToSid[entIdFull]))
		sentIdBefore = str(sentIdInt - 1)
		if int(sentIdBefore) >= 0:
			sentIdBeforeFull = self.get_full_id(docId, sentIdBefore)
			sentAndText += "### Previous Sentence: "
			sentAndText += self.get_sentText_from_fullSid(sentIdBeforeFull)
			sentAndText += "\n\n"
		else:
			sentAndText += "### Previous Sentence: (NONE) \n\n"
			
		# then add sentence where entity is mentioned (no need to check for existence
		sentAndText += "### Sentence with entity: "
		sentAndText += self.get_sentText_from_fullEntId(entIdFull)
		sentAndText += "\n\n"
		
		# then add following sentence to sentAndText (need to check if fullSid is a key)
		# in self.sidToSent
		sentIdAfter = str(sentIdInt + 1)
		sentIdAfterFull = self.get_full_id(docId, sentIdAfter)
		if sentIdAfterFull in self.sidToSent:
			sentAndText += "### Following Sentence: "
			sentAndText += self.get_sentText_from_fullSid(sentIdAfterFull)
			sentAndText += "\n\n"
		else:
			sentAndText += "### Following Sentence: (NONE) \n\n"
			
		# add tlink tuples to sentAndText
		sentAndText += "TLinks: %s\n" % self.get_id_from_full(entIdFull)
		tlinkTuples = self.get_tlink_tuples_for_entIdFull(entIdFull)
		for tt in tlinkTuples:
			targetId = self.get_id_from_full(tt[0])
			targetText = self.get_eventText_from_fullEntId(tt[0])
			relation = tt[1]
			sentAndText += "\t%s\t%s(event text: %s)\n" % (relation, targetId, targetText)

		return sentAndText
	
	def get_tlink_tuples_for_entIdFull(self, entIdFull):
		return self.entIdToTlinks[entIdFull]

	def process_tlink(self, tlinkNode, docid):
		entId1 = self.get_full_id(docid, tlinkNode.attrib['event1'])
		entId2 = self.get_full_id(docid, tlinkNode.attrib['event2'])
		label = tlinkNode.attrib['relation']
		self.halfLinks.append( (entId1, label) )
		self.halfLinks.append( (entId2, self.inverse(label)) )

		try:
			self.entIdToTlinks[entId2].append( (entId1, self.inverse(label)) )
		except KeyError:
			self.entIdToTlinks[entId2]= [(entId1, self.inverse(label))]
		
		try:
			self.entIdToTlinks[entId1].append( (entId2, label) )
		except KeyError:
			self.entIdToTlinks[entId1]= [(entId2, label)]

	def get_halfLinksCountsDict(self):
		return Counter(self.halfLinks)

	def prefixed(self, nodeName):
		return '{http://chambers.com/corpusinfo}' + nodeName
	
	def get_halfLinks(self):
		######################
		# Get halfLinkCounts #
		######################

		root = self.get_root()
	
		for entryNode in root.iter(self.prefixed('entry')):
			docId = entryNode.attrib['file']
			# populate sidToSent
			sid = entryNode.attrib['sid']

			# DEBUG - MORE THAN ONCE SENT PER ENTRY? #
			sentCount = 0
			for sentenceNode in entryNode.iter(self.prefixed('sentence')):
				sentCount+=1
				sentence = sentenceNode.text

			if sentCount > 1:
				print 'MORE THAN ONE SENT PER ENTRY!'

			self.sidToSent[self.get_full_id(docId, sid)] = sentence

			# populate entIdToText, entIdToSid
			# recall that our entId includes docId
			for eventNode in entryNode.iter(self.prefixed('event')):
				entId, text = eventNode.attrib['eiid'], eventNode.attrib['string']
				self.entIdToText[self.get_full_id(docId, entId)] = text
				self.entIdToSid[self.get_full_id(docId, entId)] = self.get_full_id(docId, sid)
			for timexNode in entryNode.iter(self.prefixed('timex')):
				entId, text = timexNode.attrib['tid'], timexNode.attrib['text']
				self.entIdToText[self.get_full_id(docId, entId)] = text
				self.entIdToSid[self.get_full_id(docId, entId)] = self.get_full_id(docId, sid)
		
			# add half links to halfLinks
		for fileNode in root.iter(self.prefixed('file')):
			docId = fileNode.attrib['name']
			for tlinkNode in fileNode.iter(self.prefixed('tlink')):
				# add tlink to halflinks;
				# also store a link from the entId to the tlink
				# this maps entId to a list of tuples (targetEntId, relation)
				self.process_tlink(tlinkNode, docId)
				
			
					
	def get_halfLink_counts(self):
		# Frequency distribution over all (normalized) halflinks
		self.halfLinkCounts = Counter(self.halfLinks)

if __name__ == "__main__":

	### ARGS ###
	inputFname = sys.argv[1]
	outputFname = sys.argv[2]


	### GET HALFLINKS OBJECT###
	hl = HalfLinks(inputFname, outputFname)
	hl.get_halfLinks()
	hl.get_halfLink_counts()
	
	'''
	Experiment 1: 
	Get the most common halflinks of type BEFORE and print their info to a file
	'''
	
	#out = open('/Users/ctaylor/Desktop/includes_halflinks_test.txt','w')
	#for halfLink in hl.halfLinkCounts.most_common():
	#	if halfLink[0][1] == 'INCLUDES' and halfLink[1] > 5:
	#		entId = halfLink[0][0]
	#		out.write(hl.get_text_and_sent_with_context_from_full(entId) + '\n\n')
	#out.close()
	

		


