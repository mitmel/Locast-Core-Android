package edu.mit.mobile.android.locast.data.interfaces;

import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;

public interface Commentable {
    @DBColumn(type = TextColumn.class)
    public static final String COL_COMMENT_DIR_URI = "comment_dir_uri";
}