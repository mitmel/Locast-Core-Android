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
 * </p>
 *
 * <p>
 * Other classes implementing this interface have a few special details to handle in order to
 * interface with the {@link SyncEngine}.
 * </p>
 *
 * <p>
 * On {@link ContentProvider#insert(Uri, android.content.ContentValues)} and
 * {@link ContentProvider#update(Uri, android.content.ContentValues, String, String[])}
 * <ul>
 * <li>Handle {@link JsonSyncableItem#COL_DIRTY} and {@link JsonSyncableItem#COL_MODIFIED_DATE}. See
 * also {@link #FLAG_DO_NOT_CHANGE_DIRTY} for details on what needs to be handled and how.</li>
 * <li>Handle {@link JsonSyncableItem#COL_DELETED}. See also {@link #QUERY_RETURN_DELETED}.
 * </ul>
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 * @see SyncableSimpleContentProvider
 *
 */
public interface SyncableProvider {

    /**
     * The {@link SyncEngine} will add this in as the value of {@link JsonSyncableItem#COL_DIRTY} to
     * {@link ContentProvider#insert(Uri, android.content.ContentValues)} and
     * {@link ContentProvider#update(Uri, android.content.ContentValues, String, String[])} calls
     * when the underlying provider should not update the modified date of the content item. When
     * this flag is missing, however, it's the responsibility of the {@link SyncableProvider} to
     * bump the {@link JsonSyncableItem#COL_MODIFIED_DATE} date.
     */
    public static final int FLAG_DO_NOT_CHANGE_DIRTY = -1;

    /**
     * <p>
     * Pass this in when querying a {@link SyncableProvider} in order to prevent it from
     * automatically filter out deleted items. This is passed to
     * {@link ContentProvider#query(Uri, String[], String, String[], String)} by adding it in as a
     * value in the {@code projection}. It will be removed from the {@code projection} if present.
     * If it is the only item in the {@code projection}, {@code projection} will be set to
     * {@code null}.
     * </p>
     * <p>
     * The {@link SyncEngine} will add this when querying the provider when it's processing the
     * local items.
     * </p>
     */
    public static final String QUERY_RETURN_DELETED = "edu.mit.mobile.android.locast.sync.query_return_deleted";

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
     * @param networkClient
     *            a instance of the network client
     * @return a URL or relative URL (based on the {@link NetworkClient} base URL) which represents
     *         the local URL
     * @throws NoPublicPath
     *             if there is no public path
     */
    public String getPublicPath(Context context, Uri uri, NetworkClient networkClient) throws NoPublicPath;

    /**
     * If this method returns true, the {@link SyncEngine} will attempt to sync it. If false, it
     * will skip over it entirely.
     *
     * @param uri
     * @return true if the given URI is syncable using this provider.
     */
    public boolean canSync(Uri uri);

    /**
     * @param uri
     * @param networkClient
     *            a instance of the network client
     * @param cr
     * @return The path that one should POST to for the given content item. Should always point to
     *         an item, not a dir.
     * @throws NoPublicPath
     */
    public String getPostPath(Context context, Uri uri, NetworkClient networkClient) throws NoPublicPath;

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
