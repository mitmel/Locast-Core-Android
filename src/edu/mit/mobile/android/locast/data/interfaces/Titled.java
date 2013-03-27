package edu.mit.mobile.android.locast.data.interfaces;

import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;

public interface Titled {
    @DBColumn(type = TextColumn.class, notnull = true)
    public static final String COL_TITLE = "title";

    @DBColumn(type = TextColumn.class)
    public static final String COL_DESCRIPTION = "description";
}