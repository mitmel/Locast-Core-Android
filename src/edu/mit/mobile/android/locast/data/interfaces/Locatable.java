package edu.mit.mobile.android.locast.data.interfaces;

import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.FloatColumn;
import edu.mit.mobile.android.content.column.TextColumn;

/**
 * implement this in order to inherit columns needed for becoming locatable.
 *
 * @author steve
 *
 */
public interface Locatable {
    @DBColumn(type = FloatColumn.class)
    public static final String COL_LATITUDE = "lat";

    @DBColumn(type = FloatColumn.class)
    public static final String COL_LONGITUDE = "lon";

    @DBColumn(type = TextColumn.class)
    public static final String COL_GEOCELL = "geocell";

}