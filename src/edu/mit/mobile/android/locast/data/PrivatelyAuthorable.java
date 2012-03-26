package edu.mit.mobile.android.locast.data;

import android.content.Context;
import android.database.Cursor;
import edu.mit.mobile.android.content.column.DBColumn;
import edu.mit.mobile.android.content.column.TextColumn;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.data.JsonSyncableItem.SyncFieldMap;

public abstract class PrivatelyAuthorable extends Authorable {

	public interface Columns extends Authorable.Columns {

		@DBColumn(type = TextColumn.class)
		public static final String _PRIVACY = "privacy";

		public static final String PRIVACY_PUBLIC = "public", PRIVACY_PROTECTED = "protected",
				PRIVACY_PRIVATE = "private";
	}

	/**
	 * The current user can be gotten using {@link Authenticator#getUserUri(Context)}.
	 * 
	 * @param c
	 *            a cursor pointing at an item's row
	 * @return true if the item is editable by the specified user.
	 */
	public static boolean canEdit(String userUri, Cursor c) {
		final String privacy = c.getString(c.getColumnIndex(Columns._PRIVACY));

		return privacy == null || userUri == null || userUri.length() == 0
				|| userUri.equals(c.getString(c.getColumnIndex(Columns._AUTHOR_URI)));
	}

	/**
	 * @param c
	 * @return true if the authenticated user can change the item's privacy level.
	 */
	public static boolean canChangePrivacyLevel(Context context, Cursor c) {
		final String useruri = Authenticator.getUserUri(context);
		return useruri == null
				|| useruri.equals(c.getString(c.getColumnIndex(Columns._AUTHOR_URI)));
	}

	// the ordering of this must match the arrays.xml
	public static final String[] PRIVACY_LIST = { Columns.PRIVACY_PUBLIC, Columns.PRIVACY_PRIVATE };

	public static final SyncMap SYNC_MAP = Authorable.SYNC_MAP;

	static {

		SYNC_MAP.put(Columns._PRIVACY, new SyncFieldMap("privacy", SyncFieldMap.STRING));
	}
}
