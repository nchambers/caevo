#!/usr/bin/perl
#
# Assumes relations are the abbreviated: b a i ii s v
#
# agreement.pl <ann1> <ann2>
#

# Put the relations into a hash table, key is the pair "e3 e15" and the value is the relation "b".
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
	    print "Unknown line format: $line\n";
	    exit;
	}
    }
    close IN;
    return %rels;
}

my $file1 = $ARGV[0];
my $file2 = $ARGV[1];
my $maxpairs = $ARGV[2]; # optional


# Read in the two files.
my %relations1 = readRelations($file1);
my %relations2 = readRelations($file2);

my %seen;
my $matched = 0, $mismatch = 0;
my %numtypes;
my %matchtypes;

# Count.
countAgreement();

# Print counts.
print "Total pairs: " . ($matched+$mismatch) . "\n";
print "Matched: $matched\n";
print "Not Matched: $mismatch\n";

print "Accuracy: " . $matched/($matched+$mismatch) . "\n";

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
    }
# Find all pairs in the second file that weren't in the first.
    foreach $pair (keys %relations2) {
        if( not exists $seen{$pair} ) {
            if( $relations2{$pair} eq "v" ) { 
                $matched++;
                $matchtypes{"v"}{"v"}++;
            }
            else {
                $mismatch++;
                $matchtypes{"v"}{$relations2{$pair}}++;
#	    print "$pair:\t - $relations2{$pair}\n";
            }
        }
    }
    if( $maxpairs ) {
        my $numvagues = $maxpairs - $matched - $mismatch;
        $matched += $numvagues;
        $matchTypes{"v"}{"v"} += $numvagues;
    }
}
