package edu.mit.mel.locast.mobile.casts;

import android.os.Bundle;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;

public class MyCastsActivity extends CastListActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(getApplicationContext());
		
		loadList(managedQuery(Cast.CONTENT_URI, CastCursorAdapter.projection, 
				Cast.AUTHOR+"=?", 
				new String[]{nc.getUsername()}, 
				Cast.MODIFIED_DATE+" DESC"));
	}	
}
