package edu.mit.mel.locast.mobile.casts;

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
import android.widget.ListView;

import com.commonsware.cwac.cache.SimpleWebImageCache;
import com.commonsware.cwac.thumbnail.ThumbnailAdapter;
import com.commonsware.cwac.thumbnail.ThumbnailBus;
import com.commonsware.cwac.thumbnail.ThumbnailMessage;

import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;

public abstract class CastListActivity extends ListActivity {

	private CastCursorAdapter adapter;
	
	protected SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		imgCache = ((Application)getApplication()).getImageCache();
	}
	
	protected void loadList(Cursor c){
		adapter = new CastCursorAdapter(this, c);
        
        // this defines what images need to be loaded. URLs are placed in the ImageView tag
        final int[] IMAGE_IDS = {R.id.media_thumbnail};
        setListAdapter(new ThumbnailAdapter(this, adapter, imgCache, IMAGE_IDS));
	}
	protected CastCursorAdapter getAdapter(){
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