# HTRC-Access-EF

A servlet-based system that provides fine-grained access to the HTRC Extracted
Features files through an API.  Used, for example, to provide the HTRC-Mashup
described in:

  Bainbridge, D., & Downie, J. S. (2017). All for one and one for all:
  reconciling research and production values at the HathiTrust through
  user-scripting. In Proceedings of 2017 ACM/IEEE Joint Conference on
  Digital Libraries (pp. 283-284). Toronto, Canada:
  IEEE. https://doi.org/10.1109/JCDL.2017.7991591

To compile and operate you need to have Maven >= v3.3.2 installed
(needed for the auto-versioning to work properly).

```bash
sudo apt-get update
sudo apt-get install maven

You'll also need mongodb:

```bash
sudo apt-get install mongodb


While control is ultimately provided through Maven, to be consistent
with other source code projects in the Solr-EF search 'stack'
some top-level bash scripts are also provided.

To compile:
```bash
./COMPILE.sh

Then download the list of bzip2 JSON files from the rsync server:

```bash
./DOWNLOAD-LATEST-RSYNC-JSON-LIST.sh


Next, check if mongodb is running:
```bash
ps auxww | grep mongo

And if not present, start it with:

./START-MONGO-DB.sh


./START-SERVER


======
Additional info:

# Run locally (on port 8080)
```bash
mvn jetty:run
```
# Package as WAR file
```bash
mvn package
```
...then find `htrc-mashup.war` in the `target/` folder.

If you need to start up mongodb manually, this can be done with a command
of the form:
```bash
/usr/local/bin/mongod --dbpath /usr/local/mongodb/db/


Obsolete:

The compile step will download (if necessary) the Extracted Features file listing which is quite big, so it might take a while.

