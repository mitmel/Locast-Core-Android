package edu.mit.mobile.android.locast.data;
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
import java.util.Map.Entry;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.sync.LocastSyncService;
import edu.mit.mobile.android.utils.ListUtils;

/**
 * This type of object row can be serialized to/from JSON and synchronized to a server.
 *
 * @author stevep
 *
 */
public abstract class JsonSyncableItem implements BaseColumns {
	public static final String
		_PUBLIC_URI      = "uri",
		_MODIFIED_DATE  = "modified",
		_CREATED_DATE 	= "created";

	public static final String[] SYNC_PROJECTION = {
		_ID,
		_PUBLIC_URI,
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

	private static String[] PUB_URI_PROJECTION = {_ID, _PUBLIC_URI};
	/**
	 * Given a public Uri fragment, finds the local item representing it. If there isn't any such item, null is returned.
	 *
	 * @param context
	 * @param dirUri the base local URI to search.
	 * @param pubUri A public URI fragment that represents the given item. This must match the result from the API.
	 * @return a local URI matching the item or null if none were found.
	 */
	public static Uri getItemByPubIUri(Context context, Uri dirUri, String pubUri){
		Uri uri = null;
		final ContentResolver cr = context.getContentResolver();

		final String[] selectionArgs = {pubUri};
		final Cursor c = cr.query(dirUri, PUB_URI_PROJECTION, _PUBLIC_URI+"=?", selectionArgs, null);
		if (c.moveToFirst()){
			uri = ContentUris.withAppendedId(dirUri, c.getLong(c.getColumnIndex(_ID)));
		}

		c.close();
		return uri;
	}

	/**
	 * @return A mapping of server↔local DB items.
	 */
	public SyncMap getSyncMap(){
		return SYNC_MAP;
	};

	public static class ItemSyncMap extends SyncMap {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;

		public ItemSyncMap() {
			super();

			put(_PUBLIC_URI, 		new SyncFieldMap("uri", SyncFieldMap.STRING, SyncItem.SYNC_FROM));
			put(_MODIFIED_DATE,		new SyncFieldMap("modified", SyncFieldMap.DATE, SyncItem.SYNC_FROM));
			put(_CREATED_DATE,		new SyncFieldMap("created", SyncFieldMap.DATE, SyncItem.SYNC_FROM | SyncItem.FLAG_OPTIONAL));
		}
	}

	public static final ItemSyncMap SYNC_MAP = new ItemSyncMap();

	public static final String LIST_DELIM = "|";
	// the below splits "tag1|tag2" but not "tag1\|tag2"
	public static final String LIST_SPLIT = "(?<!\\\\)\\|";

	/**
	 * Gets a list for the current item in the cursor.
	 *
	 * @param column column number
	 * @param c cursor pointing to a row
	 * @return
	 * @see #getList(int, Cursor)
	 */
	public static List<String> getList(int column, Cursor c){
		final String t = c.getString(column);
		return getList(t);
	}

	/**
	 * Given a string made by {@link JsonSyncableItem#putList(String, ContentValues, Collection) putList()},
	 * return a List containing all the items.
	 *
	 * @param listString
	 * @return a new list representing all the items in the list
	 * @see #putList(String, ContentValues, Collection)
	 */
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
	 * @param columnName the name of the key in cv to store the resulting list
	 * @param cv a {@link ContentValues} to store the resulting list in
	 * @param list
	 * @return the same ContentValues that were passed in
	 * @see #toListString(Collection)
	 */
	public static ContentValues putList(String columnName, ContentValues cv, Collection<String> list){
		cv.put(columnName, toListString(list));
		return cv;
	}

	/**
	 * Turns a collection of strings into a delimited string
	 *
	 * @param list a list of strings
	 * @return a string representing the list, delimited by LIST_DELIM with any existing instances escaped.
	 * @see #getList(String)
	 */
	public static String toListString(Collection<String> list){
		final List<String> tempList = new Vector<String>(list.size());

		for (String s : list){
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
	public final static ContentValues fromJSON(Context context, Uri localItem, JSONObject item, SyncMap mySyncMap) throws JSONException, IOException,
			NetworkProtocolException {
		final ContentValues cv = new ContentValues();

		for (final String propName: mySyncMap.keySet()){
			final SyncItem map = mySyncMap.get(propName);
			if (!map.isDirection(SyncItem.SYNC_FROM)){
				continue;
			}
			if (map.isOptional() &&
					(!item.has(map.remoteKey) || item.isNull(map.remoteKey))){
				continue;
			}
			final ContentValues cv2 = map.fromJSON(context, localItem, item, propName);
			if (cv2 != null){
				cv.putAll(cv2);
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
	public final static JSONObject toJSON(Context context, Uri localItem, Cursor c, SyncMap mySyncMap) throws JSONException, NetworkProtocolException, IOException {
		final JSONObject jo = new JSONObject();

		for (final String lProp: mySyncMap.keySet()){
			final SyncItem map = mySyncMap.get(lProp);

			if (!map.isDirection(SyncItem.SYNC_TO)){
				continue;
			}

			final int colIndex = c.getColumnIndex(lProp);
			// if it's a real property that's optional and is null on the local side
			if (!lProp.startsWith("_") && map.isOptional()){
				if (colIndex == -1){
					throw new RuntimeException("Programming error: Cursor does not have column '"+lProp+"', though sync map says it should. Sync Map: "+mySyncMap );
				}
				if (c.isNull(colIndex)){
					continue;
				}
			}

			final Object jsonObject = map.toJSON(context, localItem, c, lProp);
			if (jsonObject instanceof MultipleJsonObjectKeys){
				for (final Entry<String, Object> entry :((MultipleJsonObjectKeys) jsonObject).entrySet()){
					jo.put(entry.getKey(), entry.getValue());
				}

			}else{
				jo.put(map.remoteKey, jsonObject);
			}
		}
		return jo;
	}

	/**
	 * Return this from the toJson() method in order to have the mapper insert multiple
	 * keys into the parent JSON object. Use the standard put() method to add keys.
	 *
	 * @author steve
	 *
	 */
	public static class MultipleJsonObjectKeys extends HashMap<String, Object>{

		/**
		 *
		 */
		private static final long serialVersionUID = 6639058165035918704L;

	}

	public static abstract class SyncItem {

		protected final String remoteKey;
		public static final int SYNC_BOTH = 0x3,
								SYNC_TO   = 0x1,
								SYNC_FROM = 0x2,
								SYNC_NONE = 0x4,
								FLAG_OPTIONAL = 0x10;

		private static final int DEFAULT_SYNC_DIRECTION = SYNC_BOTH;

		private final int flags;

		public SyncItem(String remoteKey) {
			this(remoteKey, DEFAULT_SYNC_DIRECTION);
		}
		public SyncItem(String remoteKey, int flags){
			this.remoteKey = remoteKey;
			this.flags = flags;
		}
		public String getRemoteKey(){
			return remoteKey;
		}
		/**
		 * @return SYNC_BOTH, SYNC_NONE, SYNC_TO, or SYNC_FROM
		 */
		public int getDirection() {
			final int directionFlags = flags & 0x3;

			if ((flags & SYNC_NONE) != 0){
				return SYNC_NONE;
			}

			if (directionFlags == 0){
				return DEFAULT_SYNC_DIRECTION;
			}

			return directionFlags;
		}

		public boolean isDirection(int syncDirection){
			return (getDirection() & syncDirection) != 0;
		}

		public boolean isOptional() {
			return (flags & FLAG_OPTIONAL) != 0;
		}

		@Override
		public String toString() {
			final StringBuilder sb = new StringBuilder();
			sb.append('"');
			sb.append(remoteKey);
			sb.append('"');

			sb.append("; direction: ");
			switch (getDirection()){
			case SYNC_BOTH:
				sb.append("SYNC_BOTH");
				break;
			case SYNC_FROM:
				sb.append("SYNC_FROM");
				break;
			case SYNC_TO:
				sb.append("SYNC_TO");
				break;
			case SYNC_NONE:
				sb.append("SYNC_NONE");
				break;
			}
			sb.append(isOptional() ? "; optional ": "; not optional ");
			sb.append(String.format("(flags %x)", flags));
			return sb.toString();
		}

		/**
		 * Translate a local database entry into JSON.
		 * @param context Android context
		 * @param localItem uri of the local item
		 * @param c cursor pointing to the item
		 * @param lProp the local property where the data should be stored in the CV
		 * @return JSONObject, JSONArray or any other type that JSONObject.put() supports.
		 * @throws JSONException
		 * @throws NetworkProtocolException
		 * @throws IOException
		 */
		public abstract Object toJSON(Context context, Uri localItem, Cursor c, String lProp) throws JSONException, NetworkProtocolException, IOException;
		/**
		 * @param context Android context
		 * @param localItem uri of the local item or null if it is new
		 * @param item the JSONObject of the item. It's your job to pull out the desired field(s) here.
		 * @param lProp the local property where the data should be stored in the CV
		 * @return a new ContentValues, that will be merged into the new ContentValues object
		 * @throws JSONException
		 * @throws NetworkProtocolException
		 * @throws IOException
		 */
		public abstract ContentValues fromJSON(Context context, Uri localItem, JSONObject item, String lProp) throws JSONException, NetworkProtocolException, IOException;

		public void onPostSyncItem(Context context, Uri uri, JSONObject item, boolean updated)
			throws SyncException, IOException {}

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
		public SyncCustom(String remoteKey, int flags){
			super(remoteKey, flags);
		}
	}

	/**
	 * A simple field mapper. This maps a JSON object key to a local DB field.
	 * @author steve
	 *
	 */
	public static class SyncFieldMap extends SyncItem {
		private final int type;
		public SyncFieldMap(String remoteKey, int type) {
			this(remoteKey, type, SyncItem.SYNC_BOTH);
		}
		public SyncFieldMap(String remoteKey, int type, int flags) {
			super(remoteKey, flags);
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

		@Override
		public ContentValues fromJSON(Context context, Uri localItem,
				JSONObject item, String lProp) throws JSONException,
				NetworkProtocolException, IOException {
			final ContentValues cv = new ContentValues();

			switch (getType()){
			case SyncFieldMap.STRING:
				cv.put(lProp, item.getString(remoteKey));
				break;

			case SyncFieldMap.INTEGER:
				cv.put(lProp, item.getInt(remoteKey));
				break;

			case SyncFieldMap.DOUBLE:
				cv.put(lProp, item.getDouble(remoteKey));
				break;

			case SyncFieldMap.BOOLEAN:
				cv.put(lProp, item.getBoolean(remoteKey));
				break;

			case SyncFieldMap.LIST_INTEGER:
			case SyncFieldMap.LIST_STRING:
			case SyncFieldMap.LIST_DOUBLE:{
				final JSONArray ar = item.getJSONArray(remoteKey);
				final List<String> l = new Vector<String>(ar.length());
				for (int i = 0; i < ar.length(); i++){
					switch (getType()){
					case SyncFieldMap.LIST_STRING:
						l.add(ar.getString(i));
						break;

					case SyncFieldMap.LIST_DOUBLE:
						l.add(String.valueOf(ar.getDouble(i)));
						break;

					case SyncFieldMap.LIST_INTEGER:
						l.add(String.valueOf(ar.getInt(i)));
						break;
					}
				}
				cv.put(lProp, ListUtils.join(l, LIST_DELIM));
			}
				break;

			case SyncFieldMap.DATE:
				try {
					cv.put(lProp, NetworkClient.parseDate(item.getString(remoteKey)).getTime());
				} catch (final ParseException e) {
					final NetworkProtocolException ne = new NetworkProtocolException("bad date format");
					ne.initCause(e);
					throw ne;
				}
				break;

			case SyncFieldMap.DURATION:{
				final Matcher m = durationPattern.matcher(item.getString(remoteKey));
				if (! m.matches()){
					throw new NetworkProtocolException("bad duration format");
				}
				final int durationSeconds = 1200 * Integer.parseInt(m.group(1)) + 60 * Integer.parseInt(m.group(2)) + Integer.parseInt(m.group(3));
				cv.put(lProp, durationSeconds);
			} break;
			}
			return cv;
		}
		@Override
		public Object toJSON(Context context, Uri localItem, Cursor c, String lProp)
				throws JSONException, NetworkProtocolException, IOException {

			Object retval;
			final int columnIndex = c.getColumnIndex(lProp);
        	switch (getType()){
        	case SyncFieldMap.STRING:
        		retval = c.getString(columnIndex);
        		break;

        	case SyncFieldMap.INTEGER:
        		retval = c.getInt(columnIndex);
        		break;

        	case SyncFieldMap.DOUBLE:
        		retval = c.getDouble(columnIndex);
        		break;

        	case SyncFieldMap.BOOLEAN:
			retval = c.getInt(columnIndex) != 0;
			break;

        	case SyncFieldMap.LIST_STRING:
        	case SyncFieldMap.LIST_DOUBLE:
        	case SyncFieldMap.LIST_INTEGER:
        	{
				final JSONArray ar = new JSONArray();
				final String joined = c.getString(columnIndex);
				if (joined == null){
					throw new NullPointerException("Local value for '" + lProp + "' cannot be null.");
				}
				if (joined.length() > 0){
					for (final String s : joined.split(TaggableItem.LIST_SPLIT)){
						switch (getType()){
		            	case SyncFieldMap.LIST_STRING:
		            		ar.put(s);
		            		break;
		            	case SyncFieldMap.LIST_DOUBLE:
		            		ar.put(Double.valueOf(s));
		            		break;
		            	case SyncFieldMap.LIST_INTEGER:
		            		ar.put(Integer.valueOf(s));
		            		break;
						}
					}
				}
				retval = ar;
        	}
			break;

        	case SyncFieldMap.DATE:

        		retval =
					NetworkClient.dateFormat.format(new Date(c.getLong(columnIndex)));
			break;

        	case SyncFieldMap.DURATION:{
        		final int durationSeconds = c.getInt(columnIndex);
        		// hh:mm:ss
        		retval = String.format("%02d:%02d:%02d", durationSeconds / 1200, (durationSeconds / 60) % 60, durationSeconds % 60);
        	}break;
        	default:
        		throw new IllegalArgumentException(this.toString() + " has an invalid type.");
        	}
			return retval;
		}
	}

	/**
	 * Given a URI as the value, resolves the item(s) and stores the relations in the database.
	 * Each URI represents a list of items that should be related to the parent object.
	 *
	 * The type of the child is defined by the type of the URI that is returned in the Relationship.
	 * Synchronization of this field simply stores the public URI in the given local field as a string
	 * and then calls a synchronization on that URI with the context of the destination URI (as defined
	 * by the Relationship).
	 *
	 * For example, one could define a simple comment system to allow commenting on a Cast. In the Cast's sync map,
	 * <pre>
	 * SYNC_MAP.put(_COMMENTS_URI, new SyncChildRelation("comments", new SyncChildRelation.SimpleRelationship("castcomments"), SyncFieldMap.SYNC_FROM | SyncFieldMap.FLAG_OPTIONAL));
	 * </pre>
	 *
	 * This stores the public URI that's in the JSON object's "comments" value in the Cast's _COMMENTS_URI field.
	 * Additionally, it starts a sync on that same public URI with the extra information of {@code content://casts/1/castcomments}
	 * so that the results of the public URI sync will be stored in the local URI provided. The type of the child object is entirely
	 * determined by the type of the local URI.
	 *
	 * @author steve
	 *
	 */
	public static class SyncChildRelation extends SyncItem {
		private final Relationship mRelationship;
		private final boolean mStartChildSync;

		/**
		 *
		 * @param remoteKey the key in the JSON
		 * @param relationship how this field relates to the local database
		 * @param startChildSync if true, requests that the child be sync'd when the parent is sync'd. Defaults to true.
		 * @param flags standard {@link SyncItem} flags
		 */
		public SyncChildRelation(String remoteKey, Relationship relationship, boolean startChildSync, int flags) {
			super(remoteKey, flags);
			mRelationship = relationship;
			mStartChildSync = startChildSync;
		}

		/**
		 * By default, starts a child sync.
		 *
		 * @param remoteKey the key in the JSON
		 * @param relationship how this field relates to the local database
		 * @param flags standard {@link SyncItem} flags
		 */
		public SyncChildRelation(String remoteKey, Relationship relationship, int flags) {
			this(remoteKey, relationship, true, flags);
		}

		@Override
		public Object toJSON(Context context, Uri localItem, Cursor c,
				String lProp) throws JSONException, NetworkProtocolException,
				IOException {
			// This doesn't need to do anything, as the sync framework will automatically handle JSON creation.
			return null;
		}

		@Override
		public ContentValues fromJSON(Context context, Uri localItem,
				JSONObject item, String lProp) throws JSONException,
				NetworkProtocolException, IOException {
			final ContentValues cv = new ContentValues();
			final String childPubUri = item.getString(remoteKey);
			// store the URI so we can refresh from it later
			cv.put(lProp, childPubUri);

			return cv;
		}

		@Override
		public void onPostSyncItem(Context context, Uri uri, JSONObject item,
				boolean updated) throws SyncException, IOException {
			if (!mStartChildSync){
				return;
			}
			final Uri childDir = mRelationship.getChildDirUri(uri);
			try {
				final String childPubUri = item.getString(remoteKey);
				// TODO optimize so it doesn't need to create a whole new instance
				final NetworkClient nc = NetworkClient.getInstance(context);
				final Uri serverUri = nc.getFullUri(childPubUri);

				LocastSyncService.startSync(context, serverUri, childDir, false);

			} catch (final JSONException e) {
				final IOException ioe = new IOException("JSON encoding error");
				ioe.initCause(e);
				throw ioe;
			}
		}

		/**
		 * A simple relationship where the local URI path is the relationship name. eg.
		 *
		 * Given the parent {@code content://itinerary/1} with a relationship of "casts", the child items are all at {@code content://itinerary/1/casts}
		 *
		 * @author steve
		 *
		 */
		public static class SimpleRelationship extends Relationship {

			/**
			 * Create a new simple relationship.
			 *
			 * @param relationship local uri path suffix for the item.
			 */
			public SimpleRelationship(String relationship) {
				super(relationship);
			}

			@Override
			public Uri getChildDirUri(Uri parent) {
				return Uri.withAppendedPath(parent, getRelation());
			}
		}

		/**
		 * Defines a relationship between one object and another.
		 * For most cases, you'll want to use {@link SimpleRelationship}.
		 *
		 * @author steve
		 *
		 */
		public static abstract class Relationship {
			private final String mRelation;
			public Relationship(String relationship) {
				mRelation = relationship;
			}

			public abstract Uri getChildDirUri(Uri parent);
			public String getRelation(){
				return mRelation;
			}
		}
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
		private final SyncMap chain;

		public SyncMapChain(String remoteKey, SyncMap chain) {
			super(remoteKey);
			this.chain = chain;
		}
		public SyncMapChain(String remoteKey, SyncMap chain, int direction) {
			super(remoteKey, direction);
			this.chain = chain;
		}
		public SyncMap getChain() {
			return chain;
		}
		@Override
		public ContentValues fromJSON(Context context, Uri localItem,
				JSONObject item, String lProp) throws JSONException,
				NetworkProtocolException, IOException {

			return JsonSyncableItem.fromJSON(context, localItem, item.getJSONObject(remoteKey), getChain());
		}
		@Override
		public Object toJSON(Context context, Uri localItem, Cursor c,
				String lProp) throws JSONException, NetworkProtocolException,
				IOException {

			return JsonSyncableItem.toJSON(context, localItem, c, getChain());
		}

		@Override
		public void onPostSyncItem(Context context, Uri uri, JSONObject item,
				boolean updated) throws SyncException, IOException {
			super.onPostSyncItem(context, uri, item, updated);
			chain.onPostSyncItem(context, uri, item, updated);
		}
	}

	/**
	 * One-directional sync of a child item.
	 * @author steve
	 *
	 */
	public static class SyncChildField extends SyncMapChain {
		public SyncChildField(String localKey, String remoteKey, String remoteField, int fieldType) {
			this(localKey, remoteKey, remoteField, fieldType, 0);

		}

		public SyncChildField(String localKey, String remoteKey, String remoteField, int fieldType, int fieldFlags) {
			super(remoteKey, new SyncMap(), SyncMapChain.SYNC_FROM);

			getChain().put(localKey, new SyncFieldMap(remoteField, fieldType, fieldFlags));
		}
	}

	/**
	 * Store multiple remote fields into one local field.
	 *
	 * @author steve
	 *
	 */
	public static abstract class SyncMapJoiner extends SyncItem {
		private final SyncItem[] children;



		/**
		 * @param children The SyncItems that you wish to join. These should probably be of the same type,
		 * but don't need to be. You'll have to figure out how to join them by defining your joinContentValues().
		 */
		public SyncMapJoiner(SyncItem ... children) {
			super("_syncMapJoiner", SyncItem.SYNC_BOTH);
			this.children = children;

		}

		public SyncItem getChild(int index){
			return children[index];
		}

		public int getChildCount(){
			return children.length;
		}

		@Override
		public ContentValues fromJSON(Context context, Uri localItem,
				JSONObject item, String lProp) throws JSONException,
				NetworkProtocolException, IOException {
			final ContentValues[] cvArray = new ContentValues[children.length];
			for (int i = 0; i < children.length; i++){
				if (children[i].isDirection(SYNC_FROM)){
					cvArray[i] = children[i].fromJSON(context, localItem, item, lProp);
				}
			}
			return joinContentValues(cvArray);
		}

		@Override
		public Object toJSON(Context context, Uri localItem, Cursor c,
				String lProp) throws JSONException, NetworkProtocolException,
				IOException {
			final Object[] jsonObjects = new Object[children.length];
			for (int i = 0; i < children.length; i++){
				if (children[i].isDirection(SYNC_TO)){
					jsonObjects[i] = children[i].toJSON(context, localItem, c, lProp);
				}
			}
			return joinJson(jsonObjects);
		}

		@Override
		public void onPostSyncItem(Context context, Uri uri, JSONObject item,
				boolean updated) throws SyncException, IOException {
			super.onPostSyncItem(context, uri, item, updated);
			for (final SyncItem child : children){
				child.onPostSyncItem(context, uri, item, updated);
			}
		}

		/**
		 * Implement this to tell the joiner how to join the result of the children's fromJson()
		 * into the same ContentValues object.
		 * @param cv all results from fromJson()
		 * @return a joined version of the ContentValues
		 */
		public abstract ContentValues joinContentValues(ContentValues[] cv);

		public Object joinJson(Object[] jsonObjects) {
			final MultipleJsonObjectKeys multKeys = new MultipleJsonObjectKeys();
			for (int i = 0; i < getChildCount(); i++){
				multKeys.put(getChild(i).remoteKey, jsonObjects[i]);
			}
			return multKeys;
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

		@Override
		public ContentValues fromJSON(Context context, Uri localItem,
				JSONObject item, String lProp) throws JSONException,
				NetworkProtocolException, IOException {
			return null;
		}

		@Override
		public Object toJSON(Context context, Uri localItem, Cursor c,
				String lProp) throws JSONException, NetworkProtocolException,
				IOException {
			return literal;
		}

	}
}
