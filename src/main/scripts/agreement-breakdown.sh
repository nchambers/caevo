# compute inter-annotator agreement for two annotation files (arg1 and arg2)
# output confusion matrix for each relation pair type:
# ee, et, tt, eDCT, tDCT, xDCT (where x = e or t)
# as well as overall confusion matrix

echo "Relation Type: all"
perl agreement.pl $1 $2
echo "Relation Type: ee"
perl agreement_ee.pl $1 $2
echo "Relation Type: et"
perl agreement_et.pl $1 $2
echo "Relation Type: tt"
perl agreement_tt.pl $1 $2
echo "Relation Type: eDCT"
perl agreement_eDCT.pl $1 $2
echo "Relation Type: tDCT"
perl agreement_tDCT.pl $1 $2
echo "Relation Type: xDCT"
perl agreement_xDCT.pl $1 $2
