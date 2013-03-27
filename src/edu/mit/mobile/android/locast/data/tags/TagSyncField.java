package edu.mit.mobile.android.locast.data.tags;

import java.io.IOException;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import edu.mit.mobile.android.content.m2m.M2MManager;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncCustom;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

public class TagSyncField extends SyncCustom {

    private final M2MManager mTarget;

    public TagSyncField(String remoteKey, M2MManager target) {
        super(remoteKey);
        mTarget = target;
    }

    @Override
    public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
            throws JSONException, NetworkProtocolException, IOException {

        final JSONArray ja = new JSONArray();

        try {
            final Set<String> tags = TaggableUtils.getTags(context.getContentResolver()
                    .acquireContentProviderClient(localItem), mTarget.getUri(localItem));
            for (final String tag : tags) {
                ja.put(tag);
            }
        } catch (final RemoteException e) {
            throw new IOException("provider error");
        }

        return ja;
    }

    @Override
    public ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp)
            throws JSONException, NetworkProtocolException, IOException {

        final ContentValues cv = new ContentValues();

        cv.put(lProp, TaggableUtils.toFlattenedString(item.getJSONArray(remoteKey)));

        return cv;
    }

}
