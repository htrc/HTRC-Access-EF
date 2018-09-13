#!/bin/bash

echo "Extracting lcc-pdf-to-json-master/results.json from zip file"
echo "  => src/main/resources/lcc-outline-treemap.json"

unzip -c lcc-pdf-to-json-master.zip lcc-pdf-to-json-master/results.json  \
      >  src/main/resources/lcc-outline-treemap.json
