#!/bin/bash

ready_to_go=0
#ready_to_go=1

if [ $ready_to_go = "0" ] ; then
    
  echo ""
  echo "******"
  echo "Some different ways to start MongoDB:"
  echo ""

  echo "  sudo system mongodb start"
  echo "  /usr/bin/mongod --config /etc/mongodb.conf"
  echo "  /usr/local/mongodb/bin/mongod --dbpath /usr/local/mongodb/db/"
  
  echo ""
  echo "Edit this script to choose one of these, or some alternative that is meaningful for your OS"
  echo "  ... then change 'ready_to_go' to '1' and run the script again"
  echo "******"
else
   sudo system mongodb start
#   /usr/bin/mongod --config /etc/mongodb.conf
#   /usr/local/mongodb/bin/mongod --dbpath /usr/local/mongodb/db/
fi
