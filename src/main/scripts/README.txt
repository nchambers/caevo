---------------------------------
MERGE MULTIPLE ANNOTATIONS INTO A SINGLE ANNOTATION

merge-annotations-keeporder.pl <dir>
- You can read a directory of individual annotators, and produce a 'merged' directory that resolves their choices. Agreed upon links are preserved, and the others are labeled VAGUE. If there is only one annoator for a document, that doc is skipped.
- merged/alldocs.merged is one huge file of all documents

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
