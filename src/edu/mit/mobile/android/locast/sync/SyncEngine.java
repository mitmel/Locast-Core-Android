package edu.mit.mobile.android.locast.sync;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;

import org.json.JSONArray;
import org.json.JSONException;

import android.accounts.Account;
import android.content.ContentProviderClient;
import android.content.ContentProviderOperation;
import android.content.ContentValues;
import android.content.Context;
import android.content.SyncResult;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.CastMedia;
import edu.mit.mobile.android.locast.data.Comment;
import edu.mit.mobile.android.locast.data.Event;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.JsonSyncableItem;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.SyncException;
import edu.mit.mobile.android.locast.data.SyncMap;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

public class SyncEngine {
	private static final String TAG = SyncEngine.class.getSimpleName();

	private static final HashMap<String, Class<? extends JsonSyncableItem>> TYPE_MAP = new HashMap<String, Class<? extends JsonSyncableItem>>();

	static {
		TYPE_MAP.put(MediaProvider.TYPE_CAST_DIR, Cast.class);
		TYPE_MAP.put(MediaProvider.TYPE_CAST_ITEM, Cast.class);

		TYPE_MAP.put(MediaProvider.TYPE_CASTMEDIA_DIR, CastMedia.class);
		TYPE_MAP.put(MediaProvider.TYPE_CASTMEDIA_ITEM, CastMedia.class);

		TYPE_MAP.put(MediaProvider.TYPE_COMMENT_DIR, Comment.class);
		TYPE_MAP.put(MediaProvider.TYPE_COMMENT_ITEM, Comment.class);

		TYPE_MAP.put(MediaProvider.TYPE_ITINERARY_DIR, Itinerary.class);
		TYPE_MAP.put(MediaProvider.TYPE_ITINERARY_ITEM, Itinerary.class);

		TYPE_MAP.put(MediaProvider.TYPE_EVENT_DIR, Event.class);
		TYPE_MAP.put(MediaProvider.TYPE_EVENT_ITEM, Event.class);
	}


	private final Context mContext;
	private final NetworkClient mNetworkClient;

	public SyncEngine(Context context, NetworkClient networkClient) {
		mContext = context;
		mNetworkClient = networkClient;
	}

	private static final String[] EXIST_QUERY_PROJECTION = new String[]{JsonSyncableItem._ID, JsonSyncableItem._PUBLIC_URI};

	public boolean sync(Uri toSync, Account account, Bundle extras, ContentProviderClient provider, SyncResult syncResult) throws RemoteException, SyncException, JSONException, IOException, NetworkProtocolException {
		final ArrayList<ContentProviderOperation> cpo = new ArrayList<ContentProviderOperation>();

		final ContentProviderOperation.Builder q = ContentProviderOperation.newAssertQuery(Cast.CONTENT_URI);

		//cpo.add(q.build());

		//final Cursor c = provider.query(url, projection, selection, selectionArgs, sortOrder)

		try {
			final String pubPath = MediaProvider.getPublicPath(mContext, toSync);
			final NetworkClient nc = NetworkClient.getInstance(mContext);
			final JSONArray ja = nc.getArray(pubPath);
			final int len = ja.length();
			final ContentValues[] cvs = new ContentValues[len];

			final SyncMap sm = getSyncMap(provider, toSync);

			for (int i = 0; i < len; i++){
				cvs[i] = JsonSyncableItem.fromJSON(mContext, null, ja.getJSONObject(i), sm);
			}

			final StringBuilder sb = new StringBuilder();
			sb.append(JsonSyncableItem._PUBLIC_URI);
			sb.append(" in (");
			for (final ContentValues cv: cvs){
				final String uri = cv.getAsString(JsonSyncableItem._PUBLIC_URI);
				sb.append("'");
				sb.append(uri);
				sb.append("'");
			}
			sb.append(")");

			//Log.d(TAG, ListUtils.joinAsStrings(Arrays.asList(cvs), ","));

			final Cursor c = provider.query(toSync, EXIST_QUERY_PROJECTION, sb.toString(), null, null);
			for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
				Log.d(TAG, "already have " + c.getString(c.getColumnIndex(JsonSyncableItem._PUBLIC_URI)));
			}

		} catch (final NoPublicPath e) {
			Log.e(TAG, "no public path", e);
		}


		return true;
	}

	private SyncMap getSyncMap(ContentProviderClient provider, Uri toSync) throws RemoteException, SyncException {
		final String type = provider.getType(toSync);

		final Class<? extends JsonSyncableItem> syncable = TYPE_MAP.get(type);
		if (syncable == null){
			throw new SyncException("cannot figure out how to synchronize "+toSync+" which has type " + type);
		}

		try {
			final Field syncMap = syncable.getField("SYNC_MAP");
			final int modifiers = syncMap.getModifiers();
			if (!Modifier.isStatic(modifiers)){
				throw new SyncException("sync map for "+syncable+" is not static");
			}
			return (SyncMap) syncMap.get(null);

		} catch (final SecurityException e) {
			final SyncException se = new SyncException("error extracting sync map");
			se.initCause(e);
			throw se;
		} catch (final NoSuchFieldException e) {
			final SyncException se = new SyncException("SYNC_MAP static field missing from "+ syncable);
			se.initCause(e);
			throw se;
		} catch (final IllegalArgumentException e) {
			final SyncException se = new SyncException("error extracting sync map");
			se.initCause(e);
			throw se;
		} catch (final IllegalAccessException e) {
			final SyncException se = new SyncException("error extracting sync map");
			se.initCause(e);
			throw se;
		}
	}

}
