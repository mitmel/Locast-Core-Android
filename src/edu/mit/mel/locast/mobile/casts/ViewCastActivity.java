package edu.mit.mel.locast.mobile.casts;
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
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Window;
import android.widget.ImageView;
import android.widget.TabHost;
import android.widget.TextView;
import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.WebImageLoader;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.Comment;

public class ViewCastActivity extends TabActivity {
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
			tabHost.addTab(tabHost.newTabSpec("cast")
					.setContent(new Intent(Intent.ACTION_VIEW, intent.getData(),
											this, CastDetailsActivity.class))
					.setIndicator("cast"));
			
			
			
			loadFromIntent();
		}
	}
	
	private void loadFromIntent(){
		final Cursor c = managedQuery(getIntent().getData(), Cast.PROJECTION, null, null, null);
		c.moveToFirst();
		loadFromCursors(c);
	}
	
	private void loadFromCursors(Cursor c){
		
		((TextView)(getWindow().findViewById(android.R.id.title))).setText(
				c.getString(c.getColumnIndex(Cast.TITLE)));
				
		
		setTitle(c.getString(c.getColumnIndex(Cast.TITLE)));
		
		((TextView)(getWindow().findViewById(R.id.item_author)))
			.setText(c.getString(c.getColumnIndex(Cast.AUTHOR)));
		
		final String thumbUrl = c.getString(c.getColumnIndex(Cast.THUMBNAIL_URI));
		
		if (thumbUrl != null){
			Log.d("ViewCast", "found thumbnail " + thumbUrl);
			final ImageView mediaThumbView = ((ImageView)findViewById(android.R.id.icon));
			imgLoader.loadImage(mediaThumbView, thumbUrl);
		}
		if (!c.isNull(c.getColumnIndex(Cast.PUBLIC_ID))){
			
			final TabHost tabHost = getTabHost();
		tabHost.addTab(tabHost.newTabSpec("discussion")
				.setContent(new Intent(Intent.ACTION_VIEW, 
										Uri.withAppendedPath(getIntent().getData(), Comment.PATH)))
				.setIndicator("discussion"));
		}
		
	}
}
