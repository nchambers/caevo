#!/usr/bin/perl
#
# This is the main script that was used to merge annotations.
# It reads a directory of individual annotation files.
# It produces a new directory of merged annotations that came from the same documents.
#
# ASSUMES THAT ANNOTATION FILES HAVE THE SAME NUMBER OF LINES WITH THE SAME EVENT PAIR
# ON EACH LINE.
#
# merge-annotations-keeporder.pl <dir>
#

my $outdir = "merged-new";
mkdir($outdir);

my $agreedVague = 0;
my $notAgreedWithVague = 0;
my $notAgreedNoVague = 0;

# Put the relations into a hash table, key is the pair "e3 e15" and the value is the relation "b".
sub readRelations {
    my ($file) = @_;
    my @rels;
    my $ii = 0;

    open(IN,$file);
    while( $line = <IN> ) {
	if( $line =~ /\s*([et]\d+)\s+([a-zA-Z]+)\s+([et]\d+)\s*$/ ) {
	    my $pair = "$1\t$3";
	    my $rel = $2;
	    $rels[$ii][0] = $pair;
	    $rels[$ii][1] = $rel;
	    $ii++;
	}
	elsif( $line =~ /\s*([et]\d+)\s+([et]\d+)\s+([a-zA-Z]+)\s*$/ ) {
	    my $pair = "$1\t$2";
	    my $rel = $3;
	    $rels[$ii][0] = $pair;
	    $rels[$ii][1] = $rel;
	    $ii++;
	}
	elsif( $line =~ /^#.*/ ) { }
	elsif( $line =~ /^\s*$/ ) { }
        elsif( $line =~ /\s*([et]\d+)\s+([et]\d+)\s*$/ ) {   # line with no relation
	    my $pair = "$1\t$2";
	    my $rel = "v";
	    $rels[$ii][0] = $pair;
	    $rels[$ii][1] = $rel;
	    $ii++;
        }
	else {
	    print "Unknown line format: $line\n";
	    exit;
	}
    }
    close IN;
    return \@rels;
}


# Main Program
if( scalar @ARGV == 0 ) {
    print "merge-annotations-keeporder.pl <annotation-dir>\n";
    exit;
}

my $dir = $ARGV[0];

opendir(DIR, $dir) || die "Can't open $dir ($!)\n";
foreach $file (readdir(DIR)) {
    if( $file =~ /^([a-zA-Z].+)\.([^\.]+)$/ ) {
	print "$1 -> $2 -> $file\n";	
	$docs{$1}{$2} = readRelations("$dir/$file");
	print "read relations $dir/$file: got " . scalar(@{$docs{$1}{$2}}) . " tlinks\n";
    }
}
closedir(DIR);


# Do each document.
open(OUTALL, ">$outdir/alldocs.merged") || die "Can't open for writing ($!)\n";
open(OUTVAGUEALL, ">$outdir/alldocs.finegrainvague.merged") || die "Can't open for writing ($!)\n";
foreach $docname (keys %docs) {
    print "DOCUMENT $docname\n";
    my @annotators = keys %{$docs{$docname}};
    my $numAnnotators = scalar @annotators;
    print "\tnum annotators = $numAnnotators\n";

    if( $numAnnotators > 1 ) {
    open(OUT, ">$outdir/$docname") || die "Can't open $docname for writing ($!)\n";

    my @pairs = @{$docs{$docname}{$annotators[0]}};
    for( my $ii = 0; $ii < scalar @pairs; $ii++ ) {
	my $pair = $pairs[$ii][0];
	my %counts = ();
        my $hasvague = 0;

	for( my $xx = 0; $xx < $numAnnotators; $xx++ ) {
	    my $thispair = $docs{$docname}{$annotators[$xx]}[$ii][0];
	    my $thisrel  = $docs{$docname}{$annotators[$xx]}[$ii][1];

	    if( $thispair !~ /$pair/ ) {
		print "ERROR: different pairs on the same line ($docname): $thispair \t\t $pair\n";
	    }
	    $counts{$thisrel}++;
	}
	
	my $foundone = 0;
	foreach $label (keys %counts) {
	    if( $counts{$label} > ($numAnnotators / 2) ) {
		$foundone = 1;
                if( $label =~ /v/ ) { 
                    $agreedVague++; 
                    print OUTVAGUEALL "$docname\t$pair\tmv\n";
                } else {
                    print OUTVAGUEALL "$docname\t$pair\t$label\n";
                }
                print OUT "$pair\t$label\n";
                print OUTALL "$docname\t$pair\t$label\n";
	    }
	}

	if( !$foundone ) {
            # vague counting
            if( $counts{"v"} > 0 ) {
                $notAgreedWithVague++;
                print OUTVAGUEALL "$docname\t$pair\tpv\n";
            } else {
                $notAgreedNoVague++;
                print OUTVAGUEALL "$docname\t$pair\tnv\n";
            }

	    print OUT "$pair\tv\n";
	    print OUTALL "$docname\t$pair\tv\n";
	}
    }
    close OUT;
    }
}
close OUTALL;
close OUTVAGUEALL;

print "Outputted to directory $outdir\n";

# Counted how many VAGUE relations were actually chosen by both annotators,
# or how many came from disagreements.
print "Vague relations agreed upon:  $agreedVague\n";
print "Vague relations from disagreement, but one had vague: $notAgreedWithVague\n";
print "Vague relations from disagreement, none had vague:    $notAgreedNoVague\n";
