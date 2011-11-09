lang=it en pt
# the name of the directory that Locast has been extracted to
srcdir = Locast
version := $(shell sed '/versionName/ { s/.*versionName="\([^"]*\)".*/\1/; s/ /_/g; p }; d' AndroidManifest.xml)
out_package := ../$(srcdir)_$(version).tar.gz

all: README.html export_strings import_strings
package: $(out_package)

######################################################

export_strings:
	a2po export --no-template --ignore-fuzzy $(lang)

import_strings:
	a2po import --ignore-fuzzy

%.html: %.md
	markdown $< > $@

$(out_package): .
	tar -zcv --exclude .git --exclude-vcs --exclude-backups -X .gitignore -f $@ -C ../ $(srcdir)
