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

For further details on installation, see:

  docs/INSTALL.txt

