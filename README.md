time-sieve
==========

A TempEval-style system for fast temporal relationship tagging.


Prerequisites
-------------

time-sieve uses the WordNet dictionaries.  The dictionary files can be
downloaded from:

    http://wordnetcode.princeton.edu/wn3.1.dict.tar.gz

In order to run the test-suite, you will need to place a copy of the
WordNet dictionaries in the src/test/resources/wordnet directory.  You
should be able to download and install the directories in this
location by running the shell script:

    ./download-wordnet-dictionaries


Building
--------

    mvn compile
    mvn test
    mvn install
