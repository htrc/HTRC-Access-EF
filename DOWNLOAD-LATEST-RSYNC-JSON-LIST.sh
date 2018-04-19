#!/bin/bash

echo "Ensuring list of IDs is up to date with rsync server"
rsync -azv --progress data.analytics.hathitrust.org::features/listing/htrc-ef-all-files.txt src/main/resources/.

#echo
#echo "Copying (-i) 'htrc-ef-all-files.txt' to:"
#echo "  'src/main/resoures'"
#echo
#echo "/bin/cp -i htrc-ef-all-files.txt ./src/main/resources/htrc-ef-all-files.txt"
#
#/bin/cp -i htrc-ef-all-files.txt ./src/main/resources/htrc-ef-all-files.txt
