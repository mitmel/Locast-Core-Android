package edu.mit.mobile.android.locast.data;

import android.net.Uri;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncItem;

public class Favoritable {
	public interface Columns {
		public static final String
			_FAVORITED = "favorited";
	}

	public static final String QUERY_PARAM = "favorited";

	private static final String
		TRUE = "true",
		FALSE = "false";

	public static final SyncMap SYNC_MAP = new SyncMap();

	public static Uri getFavoritedUri(Uri uri, boolean favorited){
		return uri.buildUpon().appendQueryParameter(QUERY_PARAM, favorited ? TRUE : FALSE).build();
	}

	public static Boolean decodeFavoritedUri(Uri uri){
		final String favorited = uri.getQueryParameter(QUERY_PARAM);
		if (favorited == null){
			return null;

		}else if (TRUE.equals(favorited)){
			return true;

		}else if (FALSE.equals(favorited)){
			return false;
		}else{
			throw new IllegalArgumentException("unhandled value for favorited paramter");
		}
	}

	static {
		SYNC_MAP.put(Columns._FAVORITED, new JsonSyncableItem.SyncFieldMap("is_favorite", SyncFieldMap.BOOLEAN, SyncItem.FLAG_OPTIONAL | SyncFieldMap.SYNC_FROM));
	}
}
