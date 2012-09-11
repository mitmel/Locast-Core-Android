package edu.mit.mobile.android.locast.data;

import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.net.Uri;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

/**
 * A standard resource sync that maps the "primary" resource and its MIME type to
 * {@link CastMedia#COL_MEDIA_URL} and {@link CastMedia#COL_MIME_TYPE} respectively.
 *
 */
public class ResourcesSync extends AbsResourcesSync {

    @Override
    protected void fromResourcesJSON(Context context, Uri localItem, ContentValues cv,
            JSONObject resources) throws NetworkProtocolException, JSONException {
        addToContentValues(cv, "primary", resources, CastMedia.COL_MEDIA_URL, CastMedia.COL_MIME_TYPE, true);

    }
}