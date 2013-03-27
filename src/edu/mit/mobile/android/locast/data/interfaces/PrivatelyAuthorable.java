package edu.mit.mobile.android.locast.data.interfaces;

import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;

public interface PrivatelyAuthorable extends Authorable {

    @DBColumn(type = TextColumn.class)
    public static final String COL_PRIVACY = "privacy";

    public static final String PRIVACY_PUBLIC = "public", PRIVACY_PROTECTED = "protected",
            PRIVACY_PRIVATE = "private";
}