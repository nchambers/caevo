#!/usr/bin/perl -w
#
# infofile-from-merged.pl <infofile> <merged-file>
#

my $infopath = $ARGV[0];
my $merged = $ARGV[1];

my %docs = ();


sub getRelation {
    my $short = $_[0];

    if( $short =~ /^a$/ ) { return "AFTER"; }
    if( $short =~ /^b$/ ) { return "BEFORE"; }
    if( $short =~ /^i$/ ) { return "INCLUDES"; }
    if( $short =~ /^ii$/ ) { return "IS_INCLUDED"; }
    if( $short =~ /^s$/ ) { return "SIMULTANEOUS"; }
    if( $short =~ /^v$/ ) { return "VAGUE"; }

    print "Unknown relation: $short\n";
    return "null";
}


# Read the links.
open(IN, $merged) || die "Can't open $merged ($!)\n";
while( $line = <IN> ) {
    if( $line =~ /(.+)\t(.+)\t(.+)\t(.+)$/ ) {
	$docs{"$1.tml"}{"$2\t$3"} = $4;
#	print "$1 -> $2\t$3\n";
    }
    else {
	print "ERROR: bad line format: $line";
	exit;
    }
}
close IN;


# Read the InfoFile, only print the documents that we have links for.
my $good = 1;
my $currentDoc = "";
my %ids = ();
open(IN, $infopath) || die "Can't open $infopath ($!)\n";
while( $line = <IN> ) {
#    print "line: $line\n";

    if( $line =~ /<file name="(.*?)"/ ) {
	my $doc = $1;
#	print "Checking for *$doc*\n";
	if( exists $docs{$doc} ) {
	    $good = 1;
	    $currentDoc = $doc;
	} else {
	    $good = 0;
	}
    }
    elsif( $good && $line =~ /<event id="(\w+)" eiid="(\w+)" .*/ ) {
	$ids{$1} = $2;
#	print "EVENT $line";
#	print "\t$1 -> $2**\n";
    }
    # Print all our TLinks.
    elsif( $good && $line =~ /<\/file>/ ) {

	foreach $pair (keys %{$docs{$currentDoc}}) {
	    my @events = split(/\s+/, $pair);

	    my $eiid1 = $events[0];
	    my $eiid2 = $events[1];
	    if( $events[0] =~ /^e/ ) { $eiid1 = $ids{$events[0]}; }
	    if( $events[1] =~ /^e/ ) { $eiid2 = $ids{$events[1]}; }

	    if( !$eiid1 ) { 
#		print "Unknown eiid1 from @events\n"; 
	    }
	    elsif( !$eiid2 ) {
#		print "Unknown eiid2 from @events\n"; 
	    }
	    else {
		my $type = "ee";
		if( $events[0] =~ /^t/ && $events[1] =~ /^e/ ) { $type = "et"; }
		elsif( $events[0] =~ /^e/ && $events[1] =~ /^t/ ) { $type = "et"; }
		elsif( $events[0] =~ /^t/ && $events[1] =~ /^t/ ) { $type = "tt"; }

		my $rel = getRelation($docs{$currentDoc}{$pair});
		print "\t<tlink event1=\"$eiid1\" event2=\"$eiid2\" relation=\"$rel\" closed=\"false\" type=\"$type\" />\n";
	    }
	}
    }

    if( $good ) {
	print $line;
    }
}

# Print the closing XML root tag.
if( !$good ) { print "</root>"; }

close IN;
