package edu.mit.mel.locast.mobile.projects;
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
import android.app.TabActivity;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.WebImageLoader;
import edu.mit.mel.locast.mobile.data.Comment;
import edu.mit.mel.locast.mobile.data.Project;

public class ViewProjectActivity extends TabActivity {
	private WebImageLoader imgLoader;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		getWindow().requestFeature(Window.FEATURE_NO_TITLE);
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.window_title_thick);
		
		final TabHost tabHost = getTabHost();
		final Intent intent = getIntent();
		final String action = intent.getAction();
		
		imgLoader = ((Application)getApplication()).getImageLoader();
		
		if (Intent.ACTION_VIEW.equals(action)){

			
			loadFromIntent();
		}
	}
	
	private void loadFromIntent(){
		final Cursor c = managedQuery(getIntent().getData(), Project.PROJECTION, null, null, null);
		c.moveToFirst();
		loadFromCursors(c);
	}
	
	private void loadFromCursors(Cursor c){
		final TabHost tabHost = getTabHost();
		final int currentTab = tabHost.getCurrentTab();
		// workaround for TabWidget bug: http://code.google.com/p/android/issues/detail?id=2772
		tabHost.setCurrentTab(0);
		tabHost.clearAllTabs();
		
		((TextView)(getWindow().findViewById(android.R.id.title))).setText(
				c.getString(c.getColumnIndex(Project._TITLE)));
				
		
		setTitle(c.getString(c.getColumnIndex(Project._TITLE)));
		
		((TextView)(getWindow().findViewById(R.id.item_author)))
			.setText(c.getString(c.getColumnIndex(Project._AUTHOR)));
		
		//final String thumbUrl = c.getString(c.getColumnIndex(Project.THUMBNAIL_URI));
		
//		if (thumbUrl != null){
//			Log.d("ViewProject", "found thumbnail " + thumbUrl);
//			final ImageView mediaThumbView = ((ImageView)findViewById(android.R.id.icon));
//			imgLoader.loadImage(mediaThumbView, thumbUrl);
//		}
		
		final ImageView mediaThumbView = ((ImageView)findViewById(android.R.id.icon));
		mediaThumbView.setImageResource(R.drawable.app_icon);
		
		final Resources r = getResources();
		final Intent intent = getIntent();
		
		tabHost.addTab(tabHost.newTabSpec("Project")
				.setContent(new Intent(Intent.ACTION_VIEW, intent.getData(),
										this, ProjectDetailsActivity.class))
				.setIndicator(r.getString(R.string.tab_project), r.getDrawable(R.drawable.icon_project)));
		
		if (!c.isNull(c.getColumnIndex(Project._PUBLIC_ID))){
			
		tabHost.addTab(tabHost.newTabSpec("discussion")
				.setContent(new Intent(Intent.ACTION_VIEW, 
										Uri.withAppendedPath(getIntent().getData(), Comment.PATH)))
				.setIndicator(r.getString(R.string.tab_discussion), r.getDrawable(R.drawable.icon_discussion)));
		
		tabHost.setCurrentTab(currentTab);
		}
		
	}
}
