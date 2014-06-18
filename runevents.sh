#!/bin/bash
#

dataset=dev


# Having extra space at the end of the string seems to make maven angry.
# Don't add arguments from $@ unless we have to.
args=""
if (( $# > 0 )); then
    args="$args $@"
fi


#pushd ~/code/time-sieve
mvn exec:java -Dexec.mainClass=timesieve.TextEventClassifier -Dexec.args="$args"
#popd
