
To install HTRC-Access-EF2 as a service, run:


./INSTALL-SERVICE.sh [username]

By default, it installs as the user 'www-data'

This script checks a few things first, and if all is well, goes ahead
and creates the htrc-access-ef2.service file (in this folder), and then
installs it.

It finishes by printing out some extra details, such as how to use the
service with 'systemctl', including how to add it in to the boot-up
sequence.

