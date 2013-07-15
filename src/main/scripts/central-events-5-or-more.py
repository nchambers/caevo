import sys
import get_central_events as gce



xmlfile = sys.argv[1]
hl = gce.HalfLinks(inputFname=xmlfile)
hl.get_halfLinks()
hl.get_halfLink_counts()


labels = "BEFORE AFTER INCLUDES IS_INCLUDED SIMULTANEOUS VAGUE".split()
outputDir = sys.argv[2]
N = sys.argv[3]
mostCommonHalfLinks = hl.halfLinkCounts.most_common()

for label in labels:
	out = open(sys.argv[2] + '/' + label + '_' + N + '.txt','w')
	for halfLinkInfo in mostCommonHalfLinks:
		if halfLinkInfo[0][1] == label and halfLinkInfo[1] > int(N):
			entId = halfLinkInfo[0][0]
			out.write(hl.get_text_and_sent_with_context_from_full(entId)+'\n\n')
	out.close()	
