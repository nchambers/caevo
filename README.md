This software is released under the Apache License, Version 2.0. See LICENSE in the project root directory for all details. Portions of this software were originally developed at the United States Naval Academy as NavyTime, and then expanded into CAEVO at the 2013 SCALE Workshop at Johns Hopkins University. Software from Steven Bethard's ClearTK system is also included as separate sieves.

We make note that this software uses Stanford's CoreNLP library which is under a GPL v2.0 license. If you use CAEVO as-is, the software is governed instead by the GPL v2. If you wish to use CAEVO under the Apache License, you need to replace the CoreNLP dependency with a non-GPL library.


CAEVO
==========

CAEVO: Cascading EVent Ordering System

A TempEval-style system for extracting temporal entities (events and time 
expressions), and labeling the temporal relations between the temporal entities.
More details can be found here:

http://www.usna.edu/Users/cs/nchamber/caevo/


Prerequisites
-------------

CAEVO uses the WordNet dictionaries. The dictionary files can be
downloaded from:

    http://wordnetcode.princeton.edu/wn3.1.dict.tar.gz

You also need a jwnl_file_properties.xml file that points to the location of
your downloaded dictionary. The dictionary and this file can be stored anywhere
on your drive. CAEVO looks for an environment variable JWNL that should have the
path to the xml file.

For convenience, we provide a script that downloads the directories and
automatically sets up your jwnl_file_properties.xml file.
All you have to do is create the JWNL environment variable as indicated by the script:

    ./getwordnet.sh


Building
--------

    mvn compile
    mvn test
    mvn install
