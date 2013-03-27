package edu.mit.mobile.android.locast.data.interfaces;

import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;

/**
 * The content item has an author.
 * 
 */
public interface Authorable {
    @DBColumn(type = TextColumn.class)
    public static final String COL_AUTHOR = "author";

    @DBColumn(type = TextColumn.class, notnull = true)
    public static final String COL_AUTHOR_URI = "author_uri";

}