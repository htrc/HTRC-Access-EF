#!/bin/bash

echo ""
echo "Extracting lcc-pdf-to-json-master/results.json from zip file"
echo "  => src/main/resources/lcc-outline-treemap.json"
echo ""

unzip -qq -c lcc-pdf-to-json-master.zip lcc-pdf-to-json-master/results.json  \
      >  src/main/resources/lcc-outline-treemap.json
