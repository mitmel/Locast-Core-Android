package edu.mit.mobile.android.locast.casts;
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
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;
import android.widget.ListAdapter;
import android.widget.ListView;
import edu.mit.mobile.android.imagecache.ImageCache;
import edu.mit.mobile.android.imagecache.ImageLoaderAdapter;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.ver2.R;

/**
 * A list of casts.
 *
 * To use, call setListAdapter with a CastCursorAdaptor or something that wraps one.
 *
 * @author steve
 *
 */
public abstract class CastListActivity extends ListActivity {

	private static final String TAG = CastListActivity.class.getSimpleName();

	protected ListAdapter adapter;
	private Uri data;

	private ImageCache mImageCache;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setContentView(getContentView());
		super.onCreate(savedInstanceState);

		mImageCache = ImageCache.getInstance(this);

		data = getIntent().getData();
		if (data == null){
			data = Cast.CONTENT_URI;
		}
	}

	protected int getContentView() {
		return R.layout.cast_list;
	}

	@Override
	public void setContentView(int layoutResID) {
		super.setContentView(layoutResID);

		registerForContextMenu(getListView());
	}

	@Override
	public void setContentView(View view) {
		super.setContentView(view);
		registerForContextMenu(getListView());
	}

	@Override
	public void setContentView(View view, LayoutParams params) {
		super.setContentView(view, params);
		registerForContextMenu(getListView());
	}

	protected void loadList(Cursor c){
		adapter = getAdapter(c);

        setListAdapter(adapter);
	}
	
	protected ListAdapter getAdapter(Cursor c) {
		return new CastCursorAdapter(this, c);
	}

	protected void setCastsUri(Uri casts){
		this.data = casts;
	}

	@Override
	public void setListAdapter(ListAdapter adapter){
        // this defines what images need to be loaded. URLs are placed in the ImageView tag
        final int[] IMAGE_IDS = {R.id.media_thumbnail};
        this.adapter = adapter;
		super.setListAdapter(new ImageLoaderAdapter(this, adapter, mImageCache, IMAGE_IDS, 100, 100, ImageLoaderAdapter.UNIT_DIP));
	}

	protected ListAdapter getAdapter(){
		return adapter;
	}


	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
       } catch (final ClassCastException e) {
           Log.e(TAG, "bad menuInfo", e);
           return;
       }

       final Cursor c = (Cursor) getListAdapter().getItem(info.position);
       if (c == null){
    	   return;
       }

       // load the base menus.
		final MenuInflater menuInflater = getMenuInflater();
	    menuInflater.inflate(R.menu.cast_context, menu);
	    menuInflater.inflate(R.menu.cast_options, menu);

       menu.setHeaderTitle(c.getString(c.getColumnIndex(Cast._TITLE)));

       final boolean canEdit = Cast.canEdit(this, c);
       menu.findItem(R.id.cast_edit).setVisible(canEdit);
       menu.findItem(R.id.cast_delete).setVisible(canEdit);

	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
       } catch (final ClassCastException e) {
           Log.e(TAG, "bad menuInfo", e);
           return false;
       }

       final Uri cast = Cast.getCanonicalUri(this, ContentUris.withAppendedId(data, info.id));

       switch (item.getItemId()){
       case R.id.cast_view:
    	   startActivity(new Intent(Intent.ACTION_VIEW, cast));
    	   return true;

       case R.id.cast_edit:
    	   startActivity(new Intent(Intent.ACTION_EDIT, cast));
    	   return true;

       case R.id.cast_delete:
    	   startActivity(new Intent(Intent.ACTION_DELETE, cast));
    	   return true;

//       case R.id.cast_play:
//    	   startActivity(new Intent(CastDetailsActivity.ACTION_PLAY_CAST, cast));
//    	   return true;

       default:
    	   return super.onContextItemSelected(item);
       }
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position,
			long id) {
		super.onListItemClick(l, v, position, id);
		final Uri uri =  Cast.getCanonicalUri(this, ContentUris.withAppendedId(data, id));

		final String action = getIntent().getAction();

		if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)){
			setResult(RESULT_OK, new Intent().setData(uri));
			finish();
		}else {
			startActivity(new Intent(Intent.ACTION_VIEW, uri));
		}
	}
}
