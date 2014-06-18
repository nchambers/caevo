#!/bin/bash
#
# runcaevoraw.sh <text-file>
#


props=default.properties
sieves=default.sieves
args=""

# Having extra space at the end of the string seems to make maven angry.
# Don't add arguments from $@ unless we have to.
if (( $# > 0 )); then
    args="$args $@ raw"
fi


mvn exec:java -Dexec.mainClass=caevo.Main -Dprops=$props -Dsieves=$sieves -Dexec.args="$args"
