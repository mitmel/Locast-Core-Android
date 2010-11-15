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

/**
 * A list of casts.
 *
 * To use, call setListAdapter with a CastCursorAdaptor or something that wraps one.
 *
 * @author steve
 *
 */
public abstract class CastListActivity extends ListActivity {

	private ListAdapter adapter;
	private Uri data;

	protected SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(R.layout.cast_list);
		super.onCreate(savedInstanceState);
		imgCache = ((Application)getApplication()).getImageCache();
		data = getIntent().getData();
		if (data == null){
			data = Cast.CONTENT_URI;
		}
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
		super.onCreateContextMenu(menu, v, menuInfo);
	    getMenuInflater().inflate(R.menu.cast_view, menu);
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position,
			long id) {
		super.onListItemClick(l, v, position, id);
		final Uri uri = ContentUris.withAppendedId(data, id);

		if (Intent.ACTION_PICK.equals(getIntent().getAction())){
			setResult(RESULT_OK, new Intent().setData(uri));
			finish();
		}else {
			startActivity(new Intent(Intent.ACTION_VIEW, uri));
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		switch (item.getItemId()){

		}
		return super.onContextItemSelected(item);
	}
}
