#!/usr/bin/perl
#
# You should use the merge-annotations-keeporder.pl script instead.
# This script reads a directory of individual annotation files.
# Produces a new directory of merged annotations that came from the same documents.
#
# merge-annotations.pl <dir>
#

my $outdir = "merged";
mkdir($outdir);

# Put the relations into a hash table, key is the pair "e3 e15" and the value is the relation "b".
sub readRelations {
    my ($file) = @_;
    my %rels;

    open(IN,$file);
    while( $line = <IN> ) {
	if( $line =~ /\s*([et]\d+)\s+([a-zA-Z]+)\s+([et]\d+)\s*$/ ) {
	    my $pair = "$1\t$3";
	    my $rel = $2;
	    $rels{$pair} = $rel;
	}
	elsif( $line =~ /\s*([et]\d+)\s+([et]\d+)\s+([a-zA-Z]+)\s*$/ ) {
	    my $pair = "$1\t$2";
	    my $rel = $3;
	    $rels{$pair} = $rel;
	}
	elsif( $line =~ /^#.*/ ) { }
	elsif( $line =~ /^\s*$/ ) { }
	else {
	    print "Unknown line format: $line\n";
	    exit;
	}
    }
    close IN;
    return \%rels;
}

my $dir = $ARGV[0];

opendir(DIR, $dir) || die "Can't open $dir ($!)\n";
foreach $file (readdir(DIR)) {
    if( $file =~ /^([a-zA-Z].+)\.([^\.]+)$/ ) {
	print "$1 -> $2 -> $file\n";	
	$docs{$1}{$2} = readRelations("$dir/$file");
	print "read relations $dir/$file: got " . scalar(keys %{$docs{$1}{$2}}) . " tlinks\n";
    }
}
closedir(DIR);

# Do each document.
open(OUTALL, ">$outdir/alldocs.merged") || die "Can't open for writing ($!)\n";
foreach $docname (keys %docs) {
    print "DOCUMENT $docname\n";
    my @annotators = keys %{$docs{$docname}};
    my $numAnnotators = scalar @annotators;
    print "\tnum annotators = $numAnnotators\n";
    print "\tannotator[0] = $annotators[0]\n";

    open(OUT, ">$outdir/$docname") || die "Can't open $docname for writing ($!)\n";

    my %allpairs = {};
    # Get all pairs in one set.
    foreach $anno (keys %{$docs{$docname}}) {
	foreach $pair (keys %{$docs{$docname}{$anno}}) {
	    $allpairs{$pair} = 1;
	}
    }

#    foreach $pair (keys %{$docs{$docname}{$annotators[0]}}) {
    foreach $pair (sort keys %allpairs) {
	my %counts = ();

	for( my $xx = 0; $xx < $numAnnotators; $xx++ ) {
#	    print "$pair with anno $annotators[$xx]\n";
	    $counts{$docs{$docname}{$annotators[$xx]}{$pair}}++;
	}

	my $foundone = 0;
	foreach $label (keys %counts) {
	    if( $counts{$label} > ($numAnnotators / 2) ) {
		print OUT "$pair\t$label\n";
		print OUTALL "$docname\t$pair\t$label\n";
		$foundone = 1;
	    }
	}
	if( !$foundone ) {
	    print OUT "$pair\tv\n";
	    print OUTALL "$docname\t$pair\tv\n";
	}
    }
    close OUT;
}
close OUTALL;

