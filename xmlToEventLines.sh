#!/bin/bash
#
# This greps all the events in an INFO file, and prints out their tense/aspect/class.
# One event per line is printed.
#
# xmlToEventLines.sh <info-file>
#

grep "<event " $1 | cut -f2- -d'<' | cut -f5-8 -d' '

