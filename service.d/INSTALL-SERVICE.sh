#!/bin/bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

HTRC_ACCESSEF2_HOME=${SCRIPT_DIR%/*}


if [ -d "/etc/systemd/system/" ] ; then

    if [ "x$HTRC_ACCESSEF2_HOME" = "x" ] ; then
	cd .. && source ./SETUP.bash && cd service.d
    fi

    htrc_accessef2_service_username=${1-www-data}
    echo ""
    
    echo "****"
    echo "* Generating htrc-accessef2.service from htrc-accessef2.service.in"
    echo "****"
    cat htrc-accessef2.service.in \
	| sed "s%@HTRC_ACCESSEF2_HOME@%$HTRC_ACCESSEF2_HOME%g" \
	| sed "s%@HTRC_ACCESSEF2_SERVICE_USERNAME@%$htrc_accessef2_service_username%g" \
	      > htrc-accessef2.service
     
    echo "****"
    echo "* Copying htrc-accessef2.service to /etc/systemd/system/"
    echo "****"
    sudo /bin/cp htrc-accessef2.service /etc/systemd/system/.

    echo ""
    echo "----"
    echo "Supporting Jar downloads for maven:"
    echo "  To be able to run the maven-based Jetty service (ultimately through mvn jetty:run)"
    echo "  Maven needs to be able to download load (as that user) the jar packages needed to"
    echo "  support this.  By default it does this as the 'www-data' user, and so needs the following:"
    echo "    sudo mkdir /var/www/.m2"
    echo "    sudo chown www-data /var/www/.m2"
    echo ""    
    echo "General info:"
    echo "  In the event of the service being updated, you will most likely need to run:"
    echo "    sudo systemctl daemon-reload"
    echo ""
    echo "  To enable this service to be run at boot-up time, run:"
    echo "    sudo systemctl enable htrc-accessef2"
    echo "----"
    
else
    echo "Error: Failed to find '/etc/systemd/system'" >&2
    echo "This install script was developed on a Debian system." >&2
    echo "It looks like your Linux Distribution uses a different directory structure for services" >&2

    exit 1
fi  

