#!/bin/bash
#
# runcaevoxml.sh <text-file>
#


props=default.properties
sieves=default.sieves
args=""

# Having extra space at the end of the string seems to make maven angry.
# Don't add arguments from $@ unless we have to.
if (( $# > 0 )); then
    args="$args $@ rawxml"
else
    echo "Usage: runcaevoxml.sh <path>"
    exit
fi

echo "args are $args"
echo mvn exec:java -Dexec.mainClass=caevo.Main -Dprops=$props -Dsieves=$sieves -Dexec.args="$args"
mvn exec:java -Dexec.mainClass=caevo.Main -Dprops=$props -Dsieves=$sieves -Dexec.args="$args"
