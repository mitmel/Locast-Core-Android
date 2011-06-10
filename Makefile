lang=it en
export_strings:
	a2po export --no-template --ignore-fuzzy $(lang)

import_strings:
	a2po import --ignore-fuzzy
