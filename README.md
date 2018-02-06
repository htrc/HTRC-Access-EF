# HTRC-Mashup-EF
A mashup by David Bainbridge used to provide easy access to Extracted Features files

*Note:* You must have Maven >= 3.3.2 for the auto-versioning to work properly.

# Run locally (on port 8080)
```bash
mvn jetty:run
```
# Package as WAR file
```bash
mvn package
```
...then find `htrc-mashup.war` in the `target/` folder.

The compile step will download (if necessary) the Extracted Features file listing which is quite big, so it might take a while.

If you need to start up mongodb manually, this can be done with a command
of the form:
```bash
/usr/local/bin/mongod --dbpath /usr/local/mongodb/db/
