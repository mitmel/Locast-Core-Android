package edu.mit.mobile.android.locast.sync;

import android.content.ContentProviderClient;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncMap;
import edu.mit.mobile.android.locast.data.SyncMapException;

public interface SyncableProvider {

    public static final String CV_FLAG_DO_NOT_MARK_DIRTY = "edu.mit.mobile.android.locast.flag.NOT_DIRTY";

    /**
     * Retrieves the sync map from the class that maps to the given uri
     *
     * @param provider
     * @param toSync
     * @return
     * @throws RemoteException
     * @throws SyncMapException
     */
    public SyncMap getSyncMap(ContentProviderClient provider, Uri toSync) throws RemoteException,
            SyncMapException;

    public String getPublicPath(Context context, Uri uri) throws NoPublicPath;

    /**
     * @param uri
     * @return true if the given URI is syncable using this provider.
     */
    public boolean canSync(Uri uri);

    /**
     * @param cr
     * @param uri
     * @return The path that one should post to for the given content item. Should always point to
     *         an item, not a dir.
     * @throws NoPublicPath
     */
    public String getPostPath(Context context, Uri uri) throws NoPublicPath;

    /**
     * @return the authority that this provider handles
     */
    public String getAuthority();

    public JsonSyncableItem getWrappedContentItem(Uri item, Cursor c);

}
