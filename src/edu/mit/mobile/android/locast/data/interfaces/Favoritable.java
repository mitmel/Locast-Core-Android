package edu.mit.mobile.android.locast.data.interfaces;

import edu.mit.mobile.android.content.column.BooleanColumn;
import edu.mit.mobile.android.content.column.DBColumn;

public interface Favoritable {
    @DBColumn(type = BooleanColumn.class)
    public static final String COL_FAVORITED = "favorited";
}