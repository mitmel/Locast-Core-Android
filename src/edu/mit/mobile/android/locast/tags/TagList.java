package edu.mit.mobile.android.locast.tags;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.CursorLoader;
import android.support.v4.content.Loader;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.stackoverflow.ArrayUtils;

import edu.mit.mobile.android.locast.data.Tag;
import edu.mit.mobile.android.locast.data.TaggableItem;
import edu.mit.mobile.android.locast.ver2.R;

public class TagList extends FragmentActivity implements LoaderCallbacks<Cursor>,
		OnItemClickListener {

	private static final String[] FROM = new String[] { Tag._NAME };

	private static final int[] TO = new int[] { android.R.id.text1 };

	private SimpleCursorAdapter mAdapter;

	private Uri mUri;

	@Override
	protected void onCreate(Bundle arg0) {
		super.onCreate(arg0);

		setContentView(R.layout.simple_list_activity);

		setTitle(getTitle());

		final ListView list = (ListView) findViewById(android.R.id.list);

		mAdapter = new SimpleCursorAdapter(this, getTagItemLayout(), null, getTagDisplay(),
				getTagLayoutIds(), 0);

		list.setAdapter(mAdapter);
		list.setOnItemClickListener(this);

		mUri = getIntent().getData();

		if (mUri == null) {
			mUri = Tag.CONTENT_URI;
		}

		getSupportLoaderManager().initLoader(0, null, this);
	}

	@Override
	public void setTitle(CharSequence title) {
		super.setTitle(title);
		final TextView titleView = (TextView) findViewById(android.R.id.title);
		if (titleView != null) {
			titleView.setText(title);
		}
	}

	@Override
	public void setTitle(int titleId) {

		super.setTitle(titleId);
		final TextView titleView = (TextView) findViewById(android.R.id.title);
		if (titleView != null) {
			titleView.setText(titleId);
		}
	}

	public String[] getTagDisplay() {
		return FROM;
	}

	public String[] getTagProjection() {
		return ArrayUtils.concat(new String[] { Tag._ID }, getTagDisplay());
	}

	public int[] getTagLayoutIds() {
		return TO;
	}

	public int getTagItemLayout() {
		return android.R.layout.simple_list_item_1;
	}

	@Override
	public Loader<Cursor> onCreateLoader(int id, Bundle args) {

		return new CursorLoader(this, mUri, getTagProjection(), null, null,
				null);
	}

	@Override
	public void onLoadFinished(Loader<Cursor> loader, Cursor c) {
		mAdapter.swapCursor(c);
	}

	@Override
	public void onLoaderReset(Loader<Cursor> loader) {
		mAdapter.swapCursor(null);
	}

	@Override
	public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		final Cursor c = mAdapter.getCursor();
		c.moveToPosition(position);
		final String tag = c.getString(c.getColumnIndex(Tag._NAME));

		startActivity(new Intent(Intent.ACTION_VIEW, TaggableItem.getTagUri(mUri, tag)));

	}
}
