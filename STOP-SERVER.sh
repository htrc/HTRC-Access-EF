#!/bin/bash

echo "++++"
echo "+Identify process-id of java maven process from: ps auxww | grep maven | grep java"
echo "++++"
echo

ps auxww | grep maven | grep java

ids=`ps auxww | grep maven | grep java | awk '{print $2}'`
ids_len=`echo $ids | wc -w`

if [ $ids_len = "0" ] ; then
    echo "No processes found.  Looks like the server isn't running!"
    echo
    exit 1
fi
    
if [ $ids_len = "1" ] ; then
    id=$ids
    echo
    echo "++++"
    echo "+ running: kill $id"
    echo "++++"
    echo
    kill $id
else
    echo
    echo "++++"
    echo "+ More than one candidate found: $ids"
    echo "++++"
    echo
fi
