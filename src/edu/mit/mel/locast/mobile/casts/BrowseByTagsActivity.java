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
import java.util.ArrayList;
import java.util.Collection;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.TaggableItem;
import edu.mit.mel.locast.mobile.widget.TagList;
import edu.mit.mel.locast.mobile.widget.TagList.OnTagListChangeListener;

public class BrowseByTagsActivity extends CastListActivity implements OnTagListChangeListener {
	private TagList tagList;

	private Uri thisUri;

	public static String[] projection = TaggableItem.getTagProjection(CastCursorAdapter.DEFAULT_PROJECTION);

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.cast_browse_tags);
		new ArrayList<String>(CastCursorAdapter.DEFAULT_PROJECTION.length);



		tagList = (TagList)findViewById(R.id.tag_list);
		tagList.addedRecommendedTags(Cast.getPopularTags(getContentResolver()));
		final Cursor c = query(tagList.getTags());
		loadList(c);

		tagList.setOnTagListChangeListener(this);
	}

	private Cursor query(Collection<String>tags){
		Cursor c;
		if (tags.isEmpty()){
			thisUri = Cast.CONTENT_URI;
			c = managedQuery(thisUri, CastCursorAdapter.DEFAULT_PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT);
		}else{
			thisUri = Cast.getTagUri(Cast.CONTENT_URI, tags);
			c = managedQuery(thisUri, projection, null, null, Cast.SORT_ORDER_DEFAULT);
		}
		return c;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.browse_by_tags, menu);
		return true;
	}

	@Override
	public boolean onMenuItemSelected(int featureId, MenuItem item) {

		switch(item.getItemId()){
		case R.id.view_in_map:
		//startActivity(new Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(thisUri, Tag.PATH)));
		//startActivity(new Intent(getApplicationContext(), BrowseByMapActivity.class).setData(thisUri));
		return true;

		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}

	public void onTagListChange(TagList v) {
		tagList.clearRecommendedTags();
		tagList.addedRecommendedTags(Cast.getPopularTags(getContentResolver()));

		// TODO fix this
		// getListAdapter().changeCursor(query(v.getTags()));
	}
}
