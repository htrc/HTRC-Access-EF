#!/bin/bash

rsync -azv --progress data.analytics.hathitrust.org::features/listing/htrc-ef-all-files.txt .

echo
echo "Copying (-i) 'htrc-ef-all-files.txt' to:"
echo "  'src/main/resoures'"
echo
echo "/bin/cp -i htrc-ef-all-files.txt ./src/main/resources/htrc-ef-all-files.txt"

/bin/cp -i htrc-ef-all-files.txt ./src/main/resources/htrc-ef-all-files.txt
