/**
 * 
 */
package edu.mit.mel.locast.mobile.data;

import android.net.Uri;

/**
 * @author steve
 *
 */
public class SyncItemDeletedException extends SyncException {
	private final Uri item;
	public SyncItemDeletedException(Uri item) {
		super(item + " seems to have been deleted on the server side.");
		this.item = item;
	}
	public Uri getItem() {
		return item;
	}

	/**
	 * 
	 */
	private static final long serialVersionUID = -7815670868807412611L;

}
