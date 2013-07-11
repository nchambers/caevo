#!/usr/bin/perl
#
# Two annotations of the same document, but the pairs might appear on different lines
# in each annotation file. This treats the first given file is the correct order, and
# prints out a new order for the second file, preserving labels with their pairs.
#
# align-annotation-order.pl <file1> <file2>
#

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
	else {
	    print "Unknown line format: $line\n";
	    exit;
	}
    }
    close IN;
    return @rels;
}

my $file1 = $ARGV[0];
my $file2 = $ARGV[1];

#print "$file1 and then $file2\n";
my @pairs1 = readRelations($file1);
my @pairs2 = readRelations($file2);

my %hashed2 = ();
for( my $ii = 0; $ii < scalar @pairs2; $ii++ ) {
#    print "Pairs1 $pairs1[$ii][0] value $pairs1[$ii][1]\n";
#    print "Pairs2 $pairs2[$ii][0] value $pairs2[$ii][1]\n";
    $hashed2{$pairs2[$ii][0]} = $pairs2[$ii][1]; 
}


for( my $ii = 0; $ii < scalar @pairs1; $ii++ ) {
    $pair = $pairs1[$ii][0];
    if( not exists $hashed2{$pair} ) {
	print "$pair\tUNKNOWN\n";
    } else {
	print "$pair\t$hashed2{$pair}\n";
    }
}
