package edu.mit.mobile.android.locast.sync;

import android.content.Context;
import android.net.Uri;

public class SyncEngine {
	public SyncEngine(Context context, edu.mit.mobile.android.locast.net.NetworkClient networkClient) {

	}

	public boolean sync(Uri tosync){
		return true;
	}

	/*
	 * cases:
	 * get item from remote server
	 * 	usually a list of items
	 *  sometimes an individual one
	 *
	 * upload a single item to the server
	 */

	public boolean syncItem(Uri item ){
		return true;
	}
}