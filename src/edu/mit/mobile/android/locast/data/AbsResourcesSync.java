package edu.mit.mobile.android.locast.data;

import java.io.IOException;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncCustom;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

/**
 * Abstract resource sync.
 *
 * @author steve
 *
 */
public abstract class AbsResourcesSync extends SyncCustom {

    public static final String DEFAULT_REMOTE_KEY = "resources";
    public static final String DEFAULT_REMOTE_MIME_TYPE_KEY = "mime_type";
    public static final String DEFAULT_REMOTE_URL_KEY = "url";
    public static final int DEFAULT_FLAGS = FLAG_OPTIONAL;

    public AbsResourcesSync() {
        super(DEFAULT_REMOTE_KEY, DEFAULT_FLAGS);
    }

    public AbsResourcesSync(String remoteKey) {
        super(remoteKey, DEFAULT_FLAGS);
    }

    public AbsResourcesSync(String remoteKey, int flags) {
        super(remoteKey, flags);
    }

    @Override
    public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
            throws JSONException, NetworkProtocolException, IOException {
        return null;
    }

    @Override
    public ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp)
            throws JSONException, NetworkProtocolException, IOException {
        final ContentValues cv = new ContentValues();

        final JSONObject resourcesJo = item.getJSONObject(remoteKey);

        fromResourcesJSON(context, localItem, cv, resourcesJo);

        return cv;
    }

    /**
     * Implement this to handle loading from JSON. It's easiest to use
     * {@link #addToContentValues(ContentValues, String, JSONObject, String, String, boolean)}
     * here.
     *
     * @param context
     * @param localItem
     * @param cv
     * @param resources
     * @throws NetworkProtocolException
     * @throws JSONException
     */
    protected abstract void fromResourcesJSON(Context context, Uri localItem, ContentValues cv,
            JSONObject resources) throws NetworkProtocolException, JSONException;

    /**
     * Helper method to make it easy to load the standard resource JSON structure to a flattened
     * {@link ContentValues} object. This should be called from your implementation of
     * {@link #fromResourcesJSON(Context, Uri, ContentValues, JSONObject)}.
     *
     * @param cv
     *            destination to store result in
     * @param resourceKey
     *            the key under "resources" to load. Eg. "primary"
     * @param resources
     *            the input object
     * @param localUrlProp
     *            the local key under which the URL part should be stored.
     * @param localMimeTypeProp
     *            the local key under which the MIME type should be stored. Null is ok here to
     *            not store it.
     * @param required
     *            true if this resource is required. A {@link NetworkProtocolException} will be
     *            thrown if it is and the resource is missing.
     * @throws NetworkProtocolException
     * @throws JSONException
     */
    protected void addToContentValues(ContentValues cv, String resourceKey,
            JSONObject resources, String localUrlProp, String localMimeTypeProp,
            boolean required) throws NetworkProtocolException, JSONException {
        if (resources.has(resourceKey)) {
            final JSONObject resourceItemJo = resources.getJSONObject(resourceKey);

            cv.put(localUrlProp, resourceItemJo.getString(DEFAULT_REMOTE_URL_KEY));

            if (localMimeTypeProp != null) {
                cv.put(localMimeTypeProp,
                        resourceItemJo.getString(DEFAULT_REMOTE_MIME_TYPE_KEY));
            }

        } else {
            if (required) {
                throw new NetworkProtocolException("missing resource '" + resourceKey
                        + "' in JSON");
            }
        }
    }
}