/**
 *
 */
package edu.mit.mobile.android.locast.widget;

import java.io.IOException;

import org.apache.http.HttpResponse;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Favoritable;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.net.AndroidNetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.widget.ValidatingCheckBox.ValidatedClickHandler;

public class FavoriteClickHandler implements ValidatedClickHandler {
	private final Uri data;
	private final Context context;

	public FavoriteClickHandler(Context context, Uri favoritableItem) {
		this.data = favoritableItem;
		this.context = context;
	}

	public Boolean performClick(ValidatingCheckBox checkBox) {
		final boolean currentState = checkBox.isChecked();
		final String extraPath = currentState ? "unfavorite/" : "favorite/";
		final ContentResolver cr = context.getContentResolver();
		try {
			final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(context);
			final HttpResponse hr = nc.post(MediaProvider.getPublicPath(cr, data) + extraPath, currentState ? "false" : "true");
			hr.getEntity().consumeContent();
			final boolean newState = !currentState;

			final ContentValues cv = new ContentValues();
			cv.put(Favoritable.Columns._FAVORITED, newState);
			cv.put(MediaProvider.CV_FLAG_DO_NOT_MARK_DIRTY, true);
			cr.update(data, cv, null, null);

			// instead of returning the new value, use DB listeners to update this. The DB will always contain
			// the latest state if the network succeeded.
			return null;


		} catch (final IOException e) {
			e.printStackTrace();
			return null;

		} catch (final NetworkProtocolException e) {
			e.printStackTrace();
			return null;
		}
	}
	/**
	 * Sets the star to the current state from the _FAVORITED column in the DB
	 * and sets up a FavoriteClickHandler() on it.
	 *
	 * @param activity An activity that contains a ValidatingCheckBox with an id of R.id.star
	 * @param c cursor pointing to an active row that has a Favoritable.Columns._FAVORITED
	 * @param item uri of the item that the cursor is pointing to.
	 */
	public static void setStarred(Activity activity, Cursor c, Uri item){
		final int favIdx = c.getColumnIndex(Favoritable.Columns._FAVORITED);
		final boolean favorited = !c.isNull(favIdx) && c.getInt(favIdx) >  0;

		final ValidatingCheckBox cb = (ValidatingCheckBox)activity.findViewById(R.id.star);
		cb.setValidatedClickHandler(new FavoriteClickHandler(activity, item));

		((ValidatingCheckBox)activity.findViewById(R.id.star)).setChecked(favorited);
	}
}