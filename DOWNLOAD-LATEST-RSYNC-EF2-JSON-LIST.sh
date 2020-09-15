#!/bin/bash

echo "****"
echo "* Ensuring file-list of IDs is up to date with rsync server"
echo "****"

output_stubby_file="htrc-ef20-all-files.txt"
full_output_stubby_file="src/main/resources/$output_stubby_file"

rsync -azv queenpalm.ischool.illinois.edu::features-2020.03/listing/file_listing.txt "$full_output_stubby_file"

echo "****"
echo "* Saved as '$full_output_stubby_file'"
echo "****"


