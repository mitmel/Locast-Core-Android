package edu.mit.mel.locast.mobile.casts;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.Tag;
import edu.mit.mel.locast.mobile.data.TaggableItem;
import edu.mit.mel.locast.mobile.widget.TagList;
import edu.mit.mel.locast.mobile.widget.TagList.OnTagListChangeListener;

public class BrowseByTagsActivity extends CastListActivity implements OnTagListChangeListener {
	private TagList tagList;
	
	private Uri thisUri;
	
	//private static List<String> projection = null;
	public static String[] projection;
	static {
		final List<String> l = new ArrayList<String>(CastCursorAdapter.projection.length);
		// horrible hack to get around duplicate column names.
		for (final String col: CastCursorAdapter.projection){
			if (TaggableItem._ID.equals(col)){
				l.add("c."+col +" AS "+TaggableItem._ID);
			}else{
				l.add(col);
			}
		}
		projection = l.toArray(new String[]{});
	}
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		setContentView(R.layout.cast_browse_tags);
		new ArrayList<String>(CastCursorAdapter.projection.length);


		
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
			c = managedQuery(thisUri, CastCursorAdapter.projection, null, null, Cast.DEFAULT_SORT);
		}else{
			final String tagstring = Tag.toTagString(tags);
			thisUri = Uri.withAppendedPath(Uri.withAppendedPath(Cast.CONTENT_URI, Tag.PATH), tagstring);
			c = managedQuery(thisUri, projection, null, null, Cast.DEFAULT_SORT);
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
		startActivity(new Intent(getApplicationContext(), BrowseByMapActivity.class).setData(thisUri));
		return true;
		
		default:
			return super.onMenuItemSelected(featureId, item);
		}
	}
	
	public void onTagListChange(TagList v) {
		tagList.clearRecommendedTags();
		tagList.addedRecommendedTags(Cast.getPopularTags(getContentResolver()));
		
		getAdapter().changeCursor(query(v.getTags()));
	}
}
