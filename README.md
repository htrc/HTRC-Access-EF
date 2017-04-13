# HTRC-Mashup-EF
A mashup by David Bainbridge used to provide easy access to Extracted Features files

*Note:* You must have Maven >= 3.3.2 for the auto-versioning to work properly.

# Run locally
```bash
mvn jetty:run
```
# Package as WAR file
```bash
mvn compile war:war
```
The compile step will download (if necessary) the Extracted Features file listing which is quite big, so it might take a while.
