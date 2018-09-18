#!/bin/bash
#
# If you have a .info file with parse trees already.
# This will identify the events and add TLINKs.
#
# runcaevoparsed.sh <info-file>
#


props=default.properties
sieves=default.sieves

# Having extra space at the end of the string seems to make maven angry.
# Don't add arguments from $@ unless we have to.
if (( $# > 0 )); then
    args="-info $1 parsed"
    echo "args: $args"
    mvn exec:java -Dexec.mainClass=caevo.Main -Dprops=$props -Dsieves=$sieves -Dexec.args="$args"
else
    echo "./runcaevoparsed.sh <info-file-just-parses>"
fi



