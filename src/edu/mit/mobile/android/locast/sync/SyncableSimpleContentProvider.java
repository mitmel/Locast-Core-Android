package edu.mit.mobile.android.locast.sync;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.util.Log;
import edu.mit.mobile.android.content.ContentItem;
import edu.mit.mobile.android.content.SimpleContentProvider;
import edu.mit.mobile.android.locast.BuildConfig;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncMap;
import edu.mit.mobile.android.locast.data.SyncMapException;

/**
 * A {@link SimpleContentProvider} that implements the {@link SyncableProvider} interface. This
 * instantiates your {@link JsonSyncableItem} classes with a null cursor to call the
 * {@link JsonSyncableItem#getSyncMap()} method in order to determine how to sync.
 *
 */
public abstract class SyncableSimpleContentProvider extends SimpleContentProvider implements
        SyncableProvider {

    public SyncableSimpleContentProvider(String authority, String dbName, int dbVersion) {
        super(authority, dbName, dbVersion);
    }

    public SyncableSimpleContentProvider(String authority, int dbVersion) {
        super(authority, dbVersion);
    }

    static final String[] PUBLIC_PATH_PROJECTION = new String[] { JsonSyncableItem.COL_PUBLIC_URL };
    private static final String TAG = SyncableSimpleContentProvider.class.getSimpleName();

    @Override
    public SyncMap getSyncMap(ContentProviderClient provider, Uri toSync) throws RemoteException,
            SyncMapException {
        final JsonSyncableItem syncableItem = getWrappedContentItem(toSync, null);

        if (syncableItem == null) {
            throw new SyncMapException("could not get wrapped content item for url " + toSync);
        }

        return syncableItem.getSyncMap();
    }

    @Override
    public String getPublicPath(Context context, Uri uri) throws NoPublicPath {
        final Cursor c = query(uri, PUBLIC_PATH_PROJECTION, null, null, null);
        String publicPath;
        try {
            if (c.moveToFirst()) {
                publicPath = c.getString(c.getColumnIndex(JsonSyncableItem.COL_PUBLIC_URL));
                if (publicPath == null) {
                    throw new NoPublicPath("uri " + uri + " does not have a public path");
                }
            } else {
                throw new IllegalArgumentException("provided uri " + uri + " returned no content");
            }
        } finally {
            c.close();
        }
        return publicPath;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        final Object dirty = values.get(JsonSyncableItem.COL_DIRTY);
        if (dirty == null) {
            values.put(JsonSyncableItem.COL_DIRTY, true);

        } else if (dirty instanceof Integer
                && (Integer) dirty == SyncableProvider.FLAG_DO_NOT_CHANGE_DIRTY) {
            values.remove(JsonSyncableItem.COL_DIRTY);
        }

        final Uri newItem = super.insert(uri, values);

        return newItem;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final Object dirty = values.get(JsonSyncableItem.COL_DIRTY);
        if (dirty == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "auto-updating modified date of " + uri);
            }
            values.put(JsonSyncableItem.COL_MODIFIED_DATE, System.currentTimeMillis());
            values.put(JsonSyncableItem.COL_DIRTY, true);

        } else if (dirty instanceof Integer
                && (Integer) dirty == SyncableProvider.FLAG_DO_NOT_CHANGE_DIRTY) {
            values.remove(JsonSyncableItem.COL_DIRTY);
        }

        return super.update(uri, values, selection, selectionArgs);
    }

    @Override
    public JsonSyncableItem getWrappedContentItem(Uri item, Cursor c) {
        final Class<? extends ContentItem> contentItemClass = getContentItem(item);

        if (contentItemClass == null) {
            throw new RuntimeException(
                    "could not get content item; no mapping has been made. Is its DBHelper implementing ContentItemRegisterable?");
        }

        if (!JsonSyncableItem.class.isAssignableFrom(contentItemClass)) {
            throw new IllegalArgumentException("ContentItem " + contentItemClass
                    + " in mapping must extend JsonSyncableItem");
        }

        // checked above dynamically
        @SuppressWarnings("unchecked")
        final Class<? extends JsonSyncableItem> itemClass = (Class<? extends JsonSyncableItem>) contentItemClass;

        try {
            final Constructor<? extends JsonSyncableItem> cons = itemClass
                    .getConstructor(Cursor.class);
            return cons.newInstance(c);

        } catch (final NoSuchMethodException e) {
            Log.e(TAG, "Error getting wrapped content item", e);

        } catch (final IllegalArgumentException e) {
            Log.e(TAG, "Error getting wrapped content item", e);

        } catch (final InstantiationException e) {
            Log.e(TAG, "Error getting wrapped content item", e);

        } catch (final IllegalAccessException e) {
            Log.e(TAG, "Error getting wrapped content item", e);

        } catch (final InvocationTargetException e) {
            Log.e(TAG, "Error getting wrapped content item", e);
        }
        return null;
    }
}
