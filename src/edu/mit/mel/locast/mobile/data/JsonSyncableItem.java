package edu.mit.mel.locast.mobile.data;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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
import java.text.ParseException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import edu.mit.mel.locast.mobile.ListUtils;
import edu.mit.mel.locast.mobile.net.NetworkClient;
import edu.mit.mel.locast.mobile.net.NetworkProtocolException;

/**
 * This type of object row can be serialized to/from JSON and synchronized to a server.
 *
 * @author stevep
 *
 */
public abstract class JsonSyncableItem implements BaseColumns {
	public static final String
		_PUBLIC_ID      = "id",
		_MODIFIED_DATE  = "modified",
		_CREATED_DATE 	= "created";

	public static final String[] SYNC_PROJECTION = {
		_ID,
		_PUBLIC_ID,
		_MODIFIED_DATE,
		_CREATED_DATE,

	};

	/**
	 * @return the complete DB projection for the local object. Really only needs to
	 * contain all the fields that are used in the sync map.
	 */
	public abstract String[] getFullProjection();
	/**
	 * @return The URI for a given content directory.
	 */
	public abstract Uri getContentUri();

	/**
	 * @return A mapping of server↔local DB items.
	 */
	public Map<String, SyncItem> getSyncMap(){
		return SYNC_MAP;
	};

	public static final HashMap<String, SyncItem> SYNC_MAP = new HashMap<String, SyncItem>();
	static {
		SYNC_MAP.put(_PUBLIC_ID, 		new SyncMap("id", SyncMap.INTEGER, true));
		SYNC_MAP.put(_MODIFIED_DATE,	new SyncMap("modified", SyncMap.DATE, SyncItem.SYNC_FROM));
		SYNC_MAP.put(_CREATED_DATE,		new SyncMap("created", SyncMap.DATE, true, SyncItem.SYNC_FROM));

	}

	/**
	 * Hook called after an item has been updated on the server.
	 * @param uri Local URI pointing to the newly-updated item.
	 * @throws SyncException
	 * @throws IOException
	 */
	public void onUpdateItem(Context context, Uri uri, JSONObject item) throws SyncException, IOException {}

	/**
	 * Called just before an item is sync'd.
	 * @param c Cursor pointing to the given item.
	 *
	 * @throws SyncException
	 */
	public void onPreSyncItem(ContentResolver cr, Uri uri, Cursor c) throws SyncException {}

	/**
	 * Hook called after an item has been synchronized on the server. Called each time the sync request is made.
	 * @param uri Local URI pointing to the item.
	 * @throws SyncException
	 * @throws IOException
	 */
	public void onPostSyncItem(Context context, Uri uri, JSONObject item) throws SyncException, IOException {}

	public static final String LIST_DELIM = "|";
	// the below splits "tag1|tag2" but not "tag1\|tag2"
	public static final String LIST_SPLIT = "(?<!\\\\)\\|";

	/**
	 * Gets a list for the current item in the cursor.
	 *
	 * @param c
	 * @return
	 */
	public static List<String> getList(int column, Cursor c){
		final String t = c.getString(column);
		return getList(t);
	}

	public static List<String> getList(String listString){
		if (listString != null && listString.length() > 0){
			final String[] split = listString.split(LIST_SPLIT);
			for (int i = 0; i < split.length; i++){
				split[i] = split[i].replace("\\"+LIST_DELIM, LIST_DELIM);
			}
			return Arrays.asList(split);
		}else{
			return new Vector<String>();
		}
	}

	/**
	 * Gets a list for the current item in the cursor.
	 *
	 * @param c
	 * @return
	 */
	public static List<Long> getListLong(int column, Cursor c){
		final String t = c.getString(column);

		if (t != null && t.length() > 0){
			final String[] split = t.split(LIST_SPLIT);
			final List<Long> r = new Vector<Long>(split.length);
			for (final String s : split){
				r.add(Long.valueOf(s));
			}
			return r;
		}else{
			return new Vector<Long>();
		}
	}

	/**
	 * @param v
	 * @param tags
	 * @return
	 */
	public static ContentValues putList(String columnName, ContentValues v, List<?> list){
		v.put(columnName, toListString(list));
		return v;

	}

	public static String toListString(Collection<?> list){

		final List<String> tempList = new Vector<String>(list.size());

		for (final Object ob : list){
			String s = ob.toString();
			// escape all of the delimiters in the individual strings
			s = s.replace(LIST_DELIM, "\\" + LIST_DELIM);
			tempList.add(s);
		}

		return ListUtils.join(tempList, LIST_DELIM);
	}

	private static Pattern durationPattern = Pattern.compile("(\\d{1,2}):(\\d{1,2}):(\\d{1,2})");
	/**
	 * Given a JSON item and a sync map, create a ContentValues map to be inserted into the DB.
	 *
	 * @param context
	 * @param localItem will be null if item is new to mobile. If it's been sync'd before, will point to local entry.
	 * @param item incoming JSON item.
	 * @param mySyncMap A mapping between the JSON object and the content values.
	 * @return new ContentValues, ready to be inserted into the database.
	 * @throws JSONException
	 * @throws IOException
	 * @throws NetworkProtocolException
	 */
	public final static ContentValues fromJSON(Context context, Uri localItem, JSONObject item, Map<String, SyncItem> mySyncMap) throws JSONException, IOException,
			NetworkProtocolException {
		final ContentValues cv = new ContentValues();

		for (final String propName: mySyncMap.keySet()){
			final SyncItem map = mySyncMap.get(propName);
			if (map.getDirection() == SyncItem.SYNC_TO){
				continue;
			}
			if (map.isOptional() &&
					(!item.has(map.remoteKey) || item.isNull(map.remoteKey))){
				continue;
			}

			//item.get
			if (map instanceof SyncMap){
				final SyncMap m2 = (SyncMap)map;

				switch (m2.getType()){
				case SyncMap.STRING:
					cv.put(propName, item.getString(map.remoteKey));
					break;

				case SyncMap.INTEGER:
					cv.put(propName, item.getInt(map.remoteKey));
					break;

				case SyncMap.DOUBLE:
					cv.put(propName, item.getDouble(map.remoteKey));
					break;

				case SyncMap.BOOLEAN:
					cv.put(propName, item.getBoolean(map.remoteKey));
					break;

				case SyncMap.LIST_INTEGER:
				case SyncMap.LIST_STRING:
				case SyncMap.LIST_DOUBLE:{
					final JSONArray ar = item.getJSONArray(map.remoteKey);
					final List<String> l = new Vector<String>(ar.length());
					for (int i = 0; i < ar.length(); i++){
						switch (m2.getType()){
						case SyncMap.LIST_STRING:
							l.add(ar.getString(i));
							break;

						case SyncMap.LIST_DOUBLE:
							l.add(String.valueOf(ar.getDouble(i)));
							break;

						case SyncMap.LIST_INTEGER:
							l.add(String.valueOf(ar.getInt(i)));
							break;
						}
					}
					cv.put(propName, ListUtils.join(l, LIST_DELIM));
				}
					break;

				case SyncMap.DATE:
					try {
						cv.put(propName, NetworkClient.parseDate(item.getString(map.remoteKey)).getTime());
					} catch (final ParseException e) {
						final NetworkProtocolException ne = new NetworkProtocolException("bad date format");
						ne.initCause(e);
						throw ne;
					}
					break;

				case SyncMap.DURATION:{
					final Matcher m = durationPattern.matcher(item.getString(map.remoteKey));
					if (! m.matches()){
						throw new NetworkProtocolException("bad duration format");
					}
					final int durationSeconds = 1200 * Integer.parseInt(m.group(1)) + 60 * Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(3));
					cv.put(propName, durationSeconds);
				} break;
				}
			}else if (map instanceof SyncLiteral){
				// don't need to load these

			}else if (map instanceof SyncMapChain){
				cv.putAll(fromJSON(context, localItem, item.getJSONObject(map.remoteKey),((SyncMapChain)map).getChain()));

			}else if (map instanceof SyncCustom){
				cv.putAll(((SyncCustom)map).fromJSON(localItem, item.getJSONObject(map.remoteKey)));

			}else if (map instanceof SyncCustomArray){
				cv.putAll(((SyncCustomArray)map).fromJSON(context, localItem, item.getJSONArray(map.remoteKey)));
			}
		}
		return cv;
	}

	/**
	 * @param context
	 * @param localItem Will contain the URI of the local item being referenced in the cursor
	 * @param c active cursor with the item to sync selected.
	 * @param mySyncMap
	 * @return a new JSONObject representing the item
	 * @throws JSONException
	 * @throws NetworkProtocolException
	 * @throws IOException
	 */
	public final static JSONObject toJSON(Context context, Uri localItem, Cursor c, Map<String, SyncItem> mySyncMap) throws JSONException, NetworkProtocolException, IOException {
		final JSONObject jo = new JSONObject();

		for (final String lProp: mySyncMap.keySet()){
			final SyncItem map = mySyncMap.get(lProp);
			final int columnIndex = c.getColumnIndex(lProp);

			if (!lProp.startsWith("_") && map.isOptional() && c.isNull(columnIndex)){
				continue;
			}

			if (map.getDirection() == SyncItem.SYNC_FROM){
				continue;
			}
            if (map instanceof SyncLiteral){
            	jo.put(map.remoteKey, ((SyncLiteral)map).getLiteral());

            }else if (map instanceof SyncMapChain){
            		jo.put(map.remoteKey, toJSON(context, localItem, c, ((SyncMapChain)map).getChain()));

            }else if (map instanceof SyncCustom){
            	jo.put(map.remoteKey, ((SyncCustom)map).toJSON(localItem, c));

            }else if (map instanceof SyncCustomArray){
            	jo.put(map.remoteKey, ((SyncCustomArray)map).toJSON(context, localItem, c));

            }else if (map instanceof SyncMap){

            	final SyncMap m2 = (SyncMap)map;

            	switch (m2.getType()){
            	case SyncMap.STRING:
            		jo.put(map.remoteKey, c.getString(columnIndex));
            		break;

            	case SyncMap.INTEGER:
            		jo.put(map.remoteKey, c.getInt(columnIndex));
            		break;

            	case SyncMap.DOUBLE:
            		jo.put(map.remoteKey, c.getDouble(columnIndex));
            		break;

            	case SyncMap.BOOLEAN:
				jo.put(map.remoteKey, c.getInt(columnIndex) != 0);
				break;

            	case SyncMap.LIST_STRING:
            	case SyncMap.LIST_DOUBLE:
            	case SyncMap.LIST_INTEGER:
            	{
					final JSONArray ar = new JSONArray();
					final String joined = c.getString(columnIndex);
					if (joined == null){
						throw new NullPointerException("Local value for '" + lProp + "' cannot be null.");
					}
					if (joined.length() > 0){
						for (final String s : joined.split(TaggableItem.LIST_SPLIT)){
							switch (m2.getType()){
			            	case SyncMap.LIST_STRING:
			            		ar.put(s);
			            		break;
			            	case SyncMap.LIST_DOUBLE:
			            		ar.put(Double.valueOf(s));
			            		break;
			            	case SyncMap.LIST_INTEGER:
			            		ar.put(Integer.valueOf(s));
			            		break;
							}
						}
					}
					jo.put(map.remoteKey, ar);
            	}
				break;

            	case SyncMap.DATE:

            		jo.put(map.remoteKey,
						NetworkClient.dateFormat.format(new Date(c.getLong(columnIndex))));
				break;

            	case SyncMap.DURATION:{
            		final int durationSeconds = c.getInt(columnIndex);
            		// hh:mm:ss
            		jo.put(map.remoteKey, String.format("%02d:%02d:%02d", durationSeconds / 1200, (durationSeconds / 60) % 60, durationSeconds % 60));
            	}break;
            	}
            }
		}

		return jo;
	}



	public static abstract class SyncItem {
		private final String remoteKey;
		public static final int SYNC_BOTH = 0,
								SYNC_TO   = 1,
								SYNC_FROM = 2;
		private final int direction;
		private final boolean optional;

		public SyncItem(String remoteKey) {
			this(remoteKey, SYNC_BOTH);
		}
		public SyncItem(String remoteKey, boolean optional){
			this(remoteKey, optional, SYNC_BOTH);
		}
		public SyncItem(String remoteKey, int direction){
			this(remoteKey, false, direction);
		}
		public SyncItem(String remoteKey, boolean optional, int direction){
			this.remoteKey = remoteKey;
			this.direction = direction;
			this.optional = optional;
		}
		public String getRemoteKey(){
			return remoteKey;
		}
		public int getDirection() {
			return direction;
		}
		public boolean isOptional() {
			return optional;
		}
	}

	/**
	 * A custom sync item. Use this if the automatic field mappers aren't
	 * flexible enough to read/write from JSON.
	 *
	 * @author steve
	 *
	 */
	public static abstract class SyncCustom extends SyncItem {

		public SyncCustom(String remoteKey) {
			super(remoteKey);
		}
		public SyncCustom(String remoteKey, boolean optional){
			super(remoteKey, optional);
		}
		public abstract JSONObject toJSON(Uri localItem, Cursor c) throws JSONException;
		public abstract ContentValues fromJSON(Uri localItem, JSONObject item) throws JSONException;
	}

	/**
	 * A custom sync item. Use this if the automatic field mappers aren't
	 * flexible enough to read/write from JSON.
	 *
	 * @author steve
	 *
	 */
	public static abstract class SyncCustomArray extends SyncItem {

		public SyncCustomArray(String remoteKey) {
			super(remoteKey);
		}
		public SyncCustomArray(String remoteKey, int direction){
			super(remoteKey, direction);
		}
		public SyncCustomArray(String remoteKey, boolean optional){
			super(remoteKey, optional);
		}
		public SyncCustomArray(String remoteKey, boolean optional, int direction) {
			super(remoteKey, optional, direction);
		}
		public abstract JSONArray toJSON(Context context, Uri localItem, Cursor c) throws JSONException, NetworkProtocolException, IOException;
		public abstract ContentValues fromJSON(Context context, Uri localItem, JSONArray item) throws JSONException, NetworkProtocolException, IOException;
	}

	/**
	 * A simple field mapper. This maps a JSON object key to a local DB field.
	 * @author steve
	 *
	 */
	// TODO change 'optional' boolean to flag system.
	public static class SyncMap extends SyncItem {
		private final int type;
		public SyncMap(String remoteKey, int type) {
			this(remoteKey, type, false);
		}
		public SyncMap(String remoteKey, int type, int direction) {
			this(remoteKey, type, false, direction);
		}
		public SyncMap(String remoteKey, int type, boolean optional) {
			this(remoteKey, type, optional, SyncItem.SYNC_BOTH);
		}
		public SyncMap(String remoteKey, int type, boolean optional, int direction) {
			super(remoteKey, optional, direction);
			this.type = type;
		}

		public int getType(){
			return type;
		}

		public final static int
			STRING  = 0,
			INTEGER = 1,
			BOOLEAN = 2,
			LIST_STRING    = 3,
			DATE    = 4,
			DOUBLE  = 5,
			LIST_DOUBLE = 6,
			LIST_INTEGER = 7,
			LOCATION = 8,
			DURATION = 9;

	}

	/**
	 * An item that recursively goes into a JSON object and can map
	 * properties from that. When outputting JSON, will create the object
	 * again.
	 *
	 * @author stevep
	 *
	 */
	public static class SyncMapChain extends SyncItem {
		private final Map<String, SyncItem> chain;

		public SyncMapChain(String remoteKey, Map<String, SyncItem> chain) {
			super(remoteKey);
			this.chain = chain;
		}
		public SyncMapChain(String remoteKey, Map<String, SyncItem> chain, int direction) {
			super(remoteKey, direction);
			this.chain = chain;
		}
		public Map<String, SyncItem> getChain() {
			return chain;
		}
	}

	/**
	 * Used for outputting a literal into a JSON object. If the format requires
	 * some strange literal, like
	 *   "type": "point"
	 * this can add it.
	 *
	 * @author steve
	 *
	 */
	public static class SyncLiteral extends SyncItem {
		private final Object literal;


		public SyncLiteral(String remoteKey, Object literal) {
			super(remoteKey);
			this.literal = literal;
		}

		public Object getLiteral() {
			return literal;
		}

	}
}
