#!/usr/bin/perl -w
#
# infofile-from-merged.pl <infofile> <merged-file>
#
# Reads a .info file that contains document markup.
# Reads a single TimeBank-Dense annotation file.
# Outputs the same annotation file, but with some annotations removed.
# The removed links are the ones that link an event that is not present in
# the given info file.
#
# This is typically run with a full .info file and the alldocs.merged file
# from the TimeBank-Dense annotation directory. The result is another
# .merged file, but possibly with less links.
#
#

if( scalar(@ARGV) < 2 ) {
    print "trim-annotations.pl <infofile> <alldocs.merged>\n";
    exit;
}

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
    if( $short =~ /^mv$/ ) { return "MUTUAL_VAGUE"; }
    if( $short =~ /^pv$/ ) { return "PARTIAL_VAGUE"; }
    if( $short =~ /^nv$/ ) { return "NONE_VAGUE"; }

    print "Unknown relation: $short\n";
    return "null";
}


# Read the InfoFile, only print the documents that we have links for.
my $good = 1;
my $currentDoc = "";
my %ids = ();
my %tids = ();

my %docIDs = ();
my %docTIDs = ();

open(IN, $infopath) || die "Can't open $infopath ($!)\n";
while( $line = <IN> ) {
#    print "line: $line\n";

    if( $line =~ /<file name="(.*?)"/ ) {
	my $doc = $1;
        $doc =~ s/.tml//;
        %{$docIDs{$doc}} = ();
        %{$docTIDs{$doc}} = ();
#	print "Checking for doc *$doc*\n";
        $good = 1;
        $currentDoc = $doc;
    }
    elsif( $good && $line =~ /<event id="(\w+)" eiid="(\w+)" .*/ ) {
	$docIDs{$currentDoc}{$1} = $2;
#        print "Storing $1 as $2\n";
    }
    elsif( $good && $line =~ /<timex.* tid="(\w+)" .*/ ) {
	$docTIDs{$currentDoc}{$1} = 1; # just tracking seen timex tid's
    }
}
close IN;



# Read the links, and print out the ones with IDs that we've seen.
open(IN, $merged) || die "Can't open $merged ($!)\n";
while( $line = <IN> ) {
    if( $line =~ /(.+)\t(.+)\t(.+)\t(.+)$/ ) {
        my $doc = $1;
        my $first = $2;
        my $second = $3;

#        print "Checking $first and $second in $doc\n";

        if( exists $docIDs{$doc}{$first} || exists $docTIDs{$doc}{$first} ) {
            if( exists $docIDs{$doc}{$second} || exists $docTIDs{$doc}{$second} ) {
                print $line;
            }
        }
    }
    else {
	print "ERROR: bad line format: $line";
	exit;
    }
}
close IN;
