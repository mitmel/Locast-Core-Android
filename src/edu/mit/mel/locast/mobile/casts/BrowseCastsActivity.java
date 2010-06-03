package edu.mit.mel.locast.mobile.casts;
/*
 * Copyright (C) 2010 MIT Mobile Experience Lab
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
