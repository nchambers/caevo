#!/bin/bash
#

#info=src/main/resources/tempeval3-timebankonly-SIEVE.xml
info=src/main/resources/tempeval3-timebankonly-SIEVE-withdctlinks.xml
dataset=dev
props=default.properties

sieves=default.sieves


# Having extra space at the end of the string seems to make maven angry.
# Don't add arguments from $@ unless we have to.
args="-info $info -set $dataset"
if (( $# > 0 )); then
    args="$args $@"
fi


mvn exec:java -Dexec.mainClass=caevo.Main -Dprops=$props -Dsieves=$sieves -Dexec.args="$args"
