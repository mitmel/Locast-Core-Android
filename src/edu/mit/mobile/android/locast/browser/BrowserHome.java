package edu.mit.mobile.android.locast.browser;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Gallery;

import com.commonsware.cwac.cache.SimpleWebImageCache;
import com.commonsware.cwac.thumbnail.ThumbnailAdapter;
import com.commonsware.cwac.thumbnail.ThumbnailBus;
import com.commonsware.cwac.thumbnail.ThumbnailMessage;

import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.data.Cast;

public class BrowserHome extends Activity implements OnItemClickListener{

	protected SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		imgCache = ((Application)getApplication()).getImageCache();
		setContentView(R.layout.browser_main);

		final Gallery casts = (Gallery) findViewById(R.id.casts);

		final Cursor c = managedQuery(Cast.getTagUri(Cast.CONTENT_URI, Cast.addPrefixToTag(Cast.SYSTEM_PREFIX, "_featured")), Cast.PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT);
		final String[] from = {Cast._TITLE, Cast._AUTHOR, Cast._THUMBNAIL_URI};
		final int[] to = {R.id.cast_title, R.id.author, R.id.media_thumbnail};

		casts.setAdapter(new ThumbnailAdapter(this, new CastCursorAdapter(this, c, R.layout.cast_large_thumbnail_item, from, to), imgCache, new int[]{R.id.media_thumbnail}));
		casts.setOnItemClickListener(this);
	}

	@Override
	public void onItemClick(AdapterView<?> adapter, View v, int position, long id) {
		final Cursor c = (Cursor) adapter.getAdapter().getItem(position);
		startActivity(new Intent(Intent.ACTION_VIEW, Cast.getCanonicalUri(c)));
	}
}
