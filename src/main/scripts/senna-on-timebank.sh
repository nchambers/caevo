# this is somewhat sloppy right now - need to fix
# $1 is the directory that contains text documents for processing
# $2 is where the processed files will go
# $3 is the directory where senna lives
# don't add / to directory ends

# go through each file in input directory, pass to senna
# -usrtokens means the input is already tokenized
# -posvbs means try to apply srl to all verbs tagged as such by SENNA's pos tagger
# -srl means only show the srl output in the file
# -path is required by senna so it knows where to find some of its data it loads into memory


TIMEBANK_INPUT="$1";
TIMEBANK_OUTPUT="$2";
SENNA_PATH="$3"
for fname in $TIMEBANK_INPUT/*.txt;
do
    echo $fname;
    fullpathin=$fname;
    fnameout=$(basename "$fname");
    fullpathout=$TIMEBANK_OUTPUT/$fnameout;
    echo "$SENNA_PATH/senna -posvbs -usrtokens -path $SENNA_PATH/ <$fullpathin> $fullpathout";
    $SENNA_PATH/senna -posvbs -usrtokens -srl -path $SENNA_PATH/ <$fullpathin> $fullpathout;
done