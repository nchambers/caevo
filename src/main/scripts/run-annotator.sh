#!/bin/bash
#

if [ $# -lt 1 ]; then
    echo "run-annotator.sh <html-file> [progress.txt]"
    exit;
fi

#export JARS=$1
#export INPUTDIR=$2
export JAR=annotator.jar

# Run it.
export CLASSPATH=.:$JAR
java caevo.Annotator $1 $2
