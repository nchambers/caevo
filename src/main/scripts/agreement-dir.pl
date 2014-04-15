#!/usr/bin/perl
#
# Assumes relations are the abbreviated: b a i ii s v
# Reads a directory, trims off annotator names from the suffixes, and finds all
# pairs of files for all pairs of names. Sums up all counts for each pair of
# names, and prints out the agreement for each.
#
# agreement.pl <dir>
#


my %annotators;
my %docnames;
my $dir = $ARGV[0];
opendir(DIR, $dir) || die "Can't open $dir ($!)\n";
foreach $file (readdir(DIR)) {
    if( $file =~ /^([a-zA-Z].+)\.([^\.]+)$/ ) {
        # Skip ".adjudicated" files
        if( $file !~ /adjudicate/ ) {
#	print "$1 -> $2 -> $file\n";
            $docnames{$1} = 1;
            $annotators{$2} = 1;
#	$docs{$1}{$2} = readRelations("$dir/$file");
#	print "read relations $dir/$file: got " . scalar(keys %{$docs{$1}{$2}}) . " tlinks\n";
        }
    }
}
closedir(DIR);
#foreach $key (keys %annotators) { print "$key\n"; }
#foreach $key (sort keys %docnames) { print "$key\n"; }



# Global counts...used by the subroutines.
my %relations1 = {};
my %relations2 = {};
my %seen = {};
my $matched = 0, $mismatch = 0;
my %matchtypes;
my %allmatchtypes;


# Double loop over pairs of annotators.
my @names = keys %annotators;
for( $ii = 0; $ii < (scalar @names) - 1; $ii++ ) {
    for( $jj = $ii+1; $jj < (scalar @names); $jj++ ) {
        $matched = 0;
        $mismatch = 0;
        undef %matchtypes;
        my $numdocs = 0;

        # Loop over all documents.
        # Find documents where these two annotators were involved.
        foreach $doc (sort keys %docnames) {
            my $path1 = "$dir/$doc.$names[$ii]";
            my $path2 = "$dir/$doc.$names[$jj]";
            # If we found two docs by these annotators, calculate agreement
            if( -e $path1 && -e $path2 ) {
                undef %seen;
                %relations1 = readRelations($path1);
                %relations2 = readRelations($path2);
                countAgreement();
                $numdocs++;
            }
        }

        # Print agreement.
        if( $numdocs > 0 ) {
            print "-----------------------------\n";
            print "AGREEMENT $names[$ii] $names[$jj] from $numdocs docs.\n";
            printAgreement();
        }
    }
}

# Extra stats on the vague relation
print "----------------------------\n";
print "VAGUE relation split:\n";
print "Total mutual vague: " . $allmatchtypes{"v"}{"v"} . "\n";
my @types = qw( b a i ii s v );
my $sum = 0;
foreach $rel (@types) {
    $sum += $allmatchtypes{"v"}{$rel};
    $sum += $allmatchtypes{$rel}{"v"};
}
print "Total unagreed vague: " . $sum . "\n";



# Put the relations into a hash table, key is the pair "e3 e15" and the value is the relation "b".
# Assumes %relations1 and %relations2 are the global variables with the current annotator counts.
sub readRelations {
    my ($file) = @_;
    my %rels;

    open(IN,$file);
    while( $line = <IN> ) {
	if( $line =~ /\s*([et]\d+)\s+([a-zA-Z]+)\s+([et]\d+)\s*$/ ) {
	    my $pair = "$1 $3";
	    my $rel = $2;
	    $rels{$pair} = $rel;
	}
	elsif( $line =~ /\s*([et]\d+)\s+([et]\d+)\s+([a-zA-Z]+)\s*$/ ) {
	    my $pair = "$1 $2";
	    my $rel = $3;
	    $rels{$pair} = $rel;
	}
	elsif( $line =~ /^#.*/ ) { }
	elsif( $line =~ /^\s*$/ ) { }
	else {
	    print "Unknown line format ($file): $line\n";
	    exit;
	}
    }
    close IN;
    return %rels;
}


sub printAgreement {
    # Print counts.
    print "Total pairs: " . ($matched+$mismatch) . "\n";
    print "Matched: $matched\n";
    print "Not Matched: $mismatch\n";
    print "Accuracy (precision): " . $matched/($matched+$mismatch) . "\n";

    # Print confusion matrix.
    my @types = qw( b a i ii s v );
    foreach $rel2 (@types) {
        print "\t$rel2";
    }
    print "\n";
    foreach $rel (@types) {
        print "$rel\t";
        foreach $rel2 (@types) {
            print "$matchtypes{$rel}{$rel2}\t";
        }
        print "\n";
    }
}

sub countAgreement {
# Loop over the pair labels, and count matches.
    foreach $pair (keys %relations1) {
        my $rel1 = $relations1{$pair};

        my $rel2;
        if( not exists $relations2{$pair} ) { $rel2 = "v"; }
        else { $rel2 = $relations2{$pair}; }
        
        $seen{$pair} = 1;

        if( $rel1 eq $rel2 ) {
            $matched++;
        }
        else {
            $mismatch++;
#	print "$pair:\t$relations1{$pair} - $relations2{$pair}\n";
        }
        $matchtypes{$rel1}{$rel2}++;
        $allmatchtypes{$rel1}{$rel2}++;
    }
# Find all pairs in the second file that weren't in the first.
    foreach $pair (keys %relations2) {
        if( not exists $seen{$pair} ) {
            if( $relations2{$pair} eq "v" ) { 
                $matched++;
                $matchtypes{"v"}{"v"}++;
                $allmatchtypes{"v"}{"v"}++;
            }
            else {
                $mismatch++;
                $matchtypes{"v"}{$relations2{$pair}}++;
                $allmatchtypes{"v"}{$relations2{$pair}}++;
#	    print "$pair:\t - $relations2{$pair}\n";
            }
        }
    }
}
