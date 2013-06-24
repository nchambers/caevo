#!/usr/bin/perl
#
# Reads a directory of individual annotation files.
# Produces a new directory of merged annotations that came from the same documents.
#
# merge-annotations.pl <dir>
#

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
foreach $docname (keys %docs) {
    print "DOCUMENT $docname\n";
    my @annotators = keys %{$docs{$docname}};
    my $numAnnotators = scalar @annotators;
    print "\tnum annotators = $numAnnotators\n";
    print "\tannotator[0] = $annotators[0]\n";

    foreach $pair (keys %{$docs{$docname}{$annotators[0]}}) {
	my %counts = ();

	for( my $xx = 0; $xx < $numAnnotators; $xx++ ) {
#	    print "$pair with anno $annotators[$xx]\n";
	    $counts{$docs{$docname}{$annotators[$xx]}{$pair}}++;
	}

	my $foundone = 0;
	foreach $label (keys %counts) {
	    if( $counts{$label} > ($numAnnotators / 2) ) {
		print "$pair\t$label\n";
		$foundone = 1;
	    }
	}
	if( !$foundone ) {
	    print "$pair\tv\n";
	}
    }
}

