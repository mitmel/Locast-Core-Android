lang=it en
# the name of the directory that Locast has been extracted to
srcdir=Locast

export_strings:
	a2po export --no-template --ignore-fuzzy $(lang)

import_strings:
	a2po import --ignore-fuzzy

package: ../locast_android.tar.gz
../locast_android.tar.gz: 
	tar -zcv --exclude .git --exclude bin/\* --exclude-vcs --exclude deploy/\* --exclude gen/\* --exclude \*\~ --exclude-backups --exclude \*.tar.gz -X .gitignore -f $@ -C ../ $(srcdir)
