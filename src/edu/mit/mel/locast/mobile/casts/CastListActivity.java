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
import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.ListAdapter;
import android.widget.ListView;

import com.commonsware.cwac.cache.SimpleWebImageCache;
import com.commonsware.cwac.thumbnail.ThumbnailAdapter;
import com.commonsware.cwac.thumbnail.ThumbnailBus;
import com.commonsware.cwac.thumbnail.ThumbnailMessage;

import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;

public abstract class CastListActivity extends ListActivity {

	private ListAdapter adapter;
	
	protected SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.cast_list_header);
		super.onCreate(savedInstanceState);
		imgCache = ((Application)getApplication()).getImageCache();
		

		//getListView().addHeaderView(getLayoutInflater().inflate(R.layout.cast_list_header, getListView(), false));
	}
	
	protected void loadList(Cursor c){
		adapter = new CastCursorAdapter(this, c);

        setListAdapter(adapter);
	}
	
	@Override
	public void setListAdapter(ListAdapter adapter){
        // this defines what images need to be loaded. URLs are placed in the ImageView tag
        final int[] IMAGE_IDS = {R.id.media_thumbnail};
        this.adapter = adapter;
		super.setListAdapter(new ThumbnailAdapter(this, adapter, imgCache, IMAGE_IDS));
	}
	
	protected ListAdapter getAdapter(){
		return adapter;
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		/*final AdapterView.AdapterContextMenuInfo info 
			= (AdapterView.AdapterContextMenuInfo) menuInfo;*/
		
	    final MenuInflater inflater = getMenuInflater();
	    inflater.inflate(R.menu.cast_view, menu);
	    menu.add("test");
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position,
			long id) {
				super.onListItemClick(l, v, position, id);
				final Uri uri = ContentUris.withAppendedId(Cast.CONTENT_URI, id);
				if (Intent.ACTION_PICK.equals(getIntent().getAction())){
					setResult(RESULT_OK, new Intent().setData(uri));
					finish();
				}else {
					startActivity(new Intent(Intent.ACTION_VIEW, uri));
				}
			}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {
		switch (item.getItemId()){
		case R.id.menu_edit_cast:
			
			break;	
		}
		return true;
	}

}
