#!/bin/bash

echo "****"
echo "* Ensuring file-list of IDs is up to date with rsync server"
echo "****"

output_stubby_file="htrc-ef2-all-files.txt"

rsync -azv queenpalm.ischool.illinois.edu::features-2020.03/listing/file_listing.txt "src/main/resources/$output_stubby_file"

echo "****"
echo "* Saved as '$output_stubby_file'"
echo "****"


