package edu.mit.mobile.android.locast.sync;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;

import android.content.ContentProviderClient;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import edu.mit.mobile.android.content.ContentItem;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.content.SimpleContentProvider;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncMap;
import edu.mit.mobile.android.locast.data.SyncMapException;

/**
 * A {@link SimpleContentProvider} that implements the {@link SyncableProvider} interface. This
 * looks for public, static fields named SYNC_MAP in your {@link JsonSyncableItem} classes in order
 * to determine how to sync.
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

    @Override
    public SyncMap getSyncMap(ContentProviderClient provider, Uri toSync) throws RemoteException,
            SyncMapException {
        final Class<? extends ContentItem> itemClass = getContentItem(toSync);

        try {
            final Field syncMapField = itemClass.getField("SYNC_MAP");
            if ((syncMapField.getModifiers() & Modifier.STATIC) == 0) {
                throw new SyncMapException("SYNC_MAP field in " + itemClass + " is not static");
            }
            if ((syncMapField.getModifiers() & Modifier.PUBLIC) == 0) {
                throw new SyncMapException("SYNC_MAP field in " + itemClass + " is not public");
            }

            return (SyncMap) syncMapField.get(null);

        } catch (final NoSuchFieldException e) {
            throw new SyncMapException(itemClass + " does not have a public static field SYNC_MAP",
                    e);
        } catch (final IllegalArgumentException e) {
            throw new SyncMapException("programming error in Locast Core", e);
        } catch (final IllegalAccessException e) {
            throw new SyncMapException("programming error in Locast Core", e);
        }
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
        final boolean dirty = !values.containsKey(CV_FLAG_DO_NOT_MARK_DIRTY);
        ProviderUtils.extractContentValueItem(values, CV_FLAG_DO_NOT_MARK_DIRTY);

        final Uri newItem = super.insert(uri, values);

        return newItem;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        final boolean dirty = !values.containsKey(CV_FLAG_DO_NOT_MARK_DIRTY);
        ProviderUtils.extractContentValueItem(values, CV_FLAG_DO_NOT_MARK_DIRTY);

        return super.update(uri, values, selection, selectionArgs);
    }


    @Override
    public JsonSyncableItem getWrappedContentItem(Uri item, Cursor c) {
        final Class<? extends JsonSyncableItem> itemClass = (Class<? extends JsonSyncableItem>) getContentItem(item);

        if (itemClass == null) {
            throw new RuntimeException("could not get content item; no mapping has been made");
        }

        try {
            final Constructor<? extends JsonSyncableItem> cons = itemClass
                    .getConstructor(Cursor.class);
            return cons.newInstance(c);

        } catch (final NoSuchMethodException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final IllegalArgumentException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final InstantiationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final IllegalAccessException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        } catch (final InvocationTargetException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return null;
    }
}
