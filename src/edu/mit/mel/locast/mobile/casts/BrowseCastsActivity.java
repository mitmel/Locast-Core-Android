package edu.mit.mel.locast.mobile.casts;

import android.app.TabActivity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TabHost;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;

public class BrowseCastsActivity extends TabActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final TabHost tabHost = getTabHost();
		
		
		tabHost.addTab(tabHost.newTabSpec("mycasts")
				.setIndicator(getString(R.string.my_casts), 
						getResources().getDrawable(R.drawable.icon_browse))
				.setContent(new Intent(this, MyCastsActivity.class)));
		
		tabHost.addTab(tabHost.newTabSpec("nearby")
				.setIndicator(getString(R.string.casts_nearby))
				.setContent(new Intent(this, BrowseByMapActivity.class).setData(Cast.CONTENT_URI)));
		
		
		tabHost.addTab(tabHost.newTabSpec("bytag")
				.setIndicator(getString(R.string.casts_tags))
				.setContent(new Intent(this, BrowseByTagsActivity.class)));
	}
}
