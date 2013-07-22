---------------------------------
You can read a directory of individual annotators, and produce a 'merged' directory that resolves their choices. Agreed upon links are preserved, and the others are labeled VAGUE. If there is only one annoator for a document, that doc is skipped.
- merged/alldocs.merged is one huge file of all documents

merge-annotations-keeporder.pl <dir>

---------------------------------
You can put the merged docs into a .info file with this script:

infofile-from-merged.pl tempeval3-nolinks.xml merged/alldocs.merged
