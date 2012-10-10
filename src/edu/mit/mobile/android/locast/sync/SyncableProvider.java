package edu.mit.mobile.android.locast.sync;

import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import edu.mit.mobile.android.content.SimpleContentProvider;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncMap;
import edu.mit.mobile.android.locast.data.SyncMapException;
import edu.mit.mobile.android.locast.net.NetworkClient;

/**
 * <p>
 * This interface is intended for a {@link ContentProvider} to make its content syncable with the
 * {@link SyncEngine}.
 * </p>
 *
 * <p>
 * If using {@link SimpleContentProvider}, you can instead use {@link SyncableSimpleContentProvider}
 * to gain sync functionality.
 *
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 * @see SyncableSimpleContentProvider
 *
 */
public interface SyncableProvider {

    public static final int FLAG_DO_NOT_CHANGE_DIRTY = -1;

    /**
     * Retrieves the sync map from the class that maps to the given URL.
     *
     * @param provider
     * @param toSync
     * @return
     * @throws RemoteException
     * @throws SyncMapException
     */
    public SyncMap getSyncMap(ContentProviderClient provider, Uri toSync) throws RemoteException,
            SyncMapException;

    /**
     * Given a local content URL, comes up with a public URL for it. This URL should be resolvable
     * to the same data that are in the local content URL.
     *
     * @param context
     * @param uri
     *            local content URL
     * @return a URL or relative URL (based on the {@link NetworkClient} base URL) which represents
     *         the local URL
     * @throws NoPublicPath
     *             if there is no public path
     */
    public String getPublicPath(Context context, Uri uri) throws NoPublicPath;

    /**
     * If this method returns true, the {@link SyncEngine} will attempt to sync it. If false, it
     * will skip over it entirely.
     *
     * @param uri
     * @return true if the given URI is syncable using this provider.
     */
    public boolean canSync(Uri uri);

    /**
     * @param cr
     * @param uri
     * @return The path that one should POST to for the given content item. Should always point to
     *         an item, not a dir.
     * @throws NoPublicPath
     */
    public String getPostPath(Context context, Uri uri) throws NoPublicPath;

    /**
     * @return the authority that this provider handles
     */
    public String getAuthority();

    /**
     * Create an appropriate subclass of {@link JsonSyncableItem} and wrap the given cursor with it.
     *
     * @param item
     * @param c
     * @return
     */
    public JsonSyncableItem getWrappedContentItem(Uri item, Cursor c);

}
