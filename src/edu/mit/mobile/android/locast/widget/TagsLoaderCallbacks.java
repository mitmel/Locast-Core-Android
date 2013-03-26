package edu.mit.mobile.android.locast.widget;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import edu.mit.mobile.android.locast.data.tags.Tag;

public class TagsLoaderCallbacks implements LoaderCallbacks<Cursor> {

    private final TagListView mTlv;
    private final Context mContext;

    private static final String[] TAG_PROJECTION = new String[] { Tag.COL_NAME };

    /**
     * A {@link Uri} object.
     */
    public static String ARGS_URI = "uri";

    public TagsLoaderCallbacks(Context context, TagListView tagsView) {
        mTlv = tagsView;
        mContext = context;
    }

    @Override
    public Loader<Cursor> onCreateLoader(int arg0, Bundle args) {
        if (args == null) {
            args = Bundle.EMPTY;
        }

        final Uri uri = args.getParcelable(ARGS_URI);
        if (uri == null) {
            throw new IllegalArgumentException("Missing loader arguments. Need to provide ARGS_URI");
        }

        return new CursorLoader(mContext, uri, TAG_PROJECTION, null, null, null);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor c) {
        mTlv.clearAllTags();
        final int tagNameCol = c.getColumnIndexOrThrow(Tag.COL_NAME);

        while (c.moveToNext()) {
            mTlv.addTag(c.getString(tagNameCol));
        }
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        mTlv.clearAllTags();
    }
}
