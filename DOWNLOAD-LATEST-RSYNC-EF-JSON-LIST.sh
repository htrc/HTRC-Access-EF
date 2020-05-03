#!/bin/bash

echo "Ensuring list of IDs is up to date with rsync server"
rsync -azv --progress data.analytics.hathitrust.org::features/listing/htrc-ef-all-files.txt src/main/resources/.


