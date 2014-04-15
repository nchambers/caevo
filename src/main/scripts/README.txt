---------------------------------
MERGE MULTIPLE ANNOTATIONS INTO A SINGLE ANNOTATION

merge-annotations-keeporder.pl <dir>
- You can read a directory of individual annotators, and produce a 'merged' directory that resolves their choices. Agreed upon links are preserved, and the others are labeled VAGUE. If there is only one annoator for a document, that doc is skipped.
- merged/alldocs.merged is one huge file of all documents
- merged/alldocs.finegrained.merged is the same as above, but instead of VAGUE relations, it contains three subtypes: mv (mutual vague), pv (partial vague), and nv (no vague)


---------------------------------
TRIM ANNOTATIONS BASED ON EXISTING .info FILE EVENTS/TIMES

trim-annotations-based-on-infofile.pl tempeval3-nolinks.xml merged/alldocs.merged
- You may want to remove some annotated TLINKs. The TempEval3 contest removed some
  events from what was in TimeBank, so TLINKs that connect those removed events create
  evaluation problems. The TimeBank-Dense annotation was on TimeBank, so you can trim
  away the TLINKs by calling this script. It just removes anything that involves an
  event or time expression that is not in the info file.


---------------------------------
PUT ANNOTATIONS INTO AN EXISTING .info FILE

infofile-from-merged.pl tempeval3-nolinks.xml merged/alldocs.merged
- You can put the merged docs into a .info file with this script.


---------------------------------
CALCULATE AGREEMENT

agreement-dir.pl <dir>
This finds all annotator pairs and sums up all documents by them.

agreement.pl <doc1> <doc2>
Compute agreement on a single document labeled by two annotators.
