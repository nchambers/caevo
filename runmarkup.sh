#!/bin/bash
#
# Converts all of the files in a .info.xml file into plain marked up documents
# and puts them in a markup/ folder.
#
# runmarkup.sh <filepath>
#

# Having extra space at the end of the string seems to make maven angry.
# Don't add arguments from $@ unless we have to.
args=""
if (( $# > 0 )); then
    args="$args $@"
fi

mvn exec:java -Dexec.mainClass=caevo.InfoFile -Dexec.args="$args markup"
