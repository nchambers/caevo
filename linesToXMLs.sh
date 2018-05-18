#!/bin/bash
#

outdir=xmls

mkdir xmls
rm xmls/*

counter=1000
while read -r line; do
    cat linesToXMLs.header >  $outdir/file$counter
    echo "$line" | cut -f4 >> $outdir/file$counter
    cat linesToXMLs.tail   >> $outdir/file$counter
    counter=$((counter+1))
done <$1
   
