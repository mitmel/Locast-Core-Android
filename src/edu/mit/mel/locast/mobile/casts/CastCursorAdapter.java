package edu.mit.mel.locast.mobile.casts;

import android.content.Context;
import android.database.Cursor;
import android.widget.ImageView;
import android.widget.SimpleCursorAdapter;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;

public class CastCursorAdapter extends SimpleCursorAdapter {
	private final static String[] from = new String[] {Cast.THUMBNAIL_URI, Cast.AUTHOR, Cast.TITLE, Cast.DESCRIPTION};
	private final static int[] to = new int[] {R.id.media_thumbnail, R.id.author, android.R.id.text1, android.R.id.text2};
	public final static int[] IMAGE_IDS = {R.id.media_thumbnail};
	
	public final static String[] projection = {
			Cast._ID,
			Cast.AUTHOR,
			Cast.TITLE,
			Cast.DESCRIPTION,
			Cast.THUMBNAIL_URI
		};
	
	public CastCursorAdapter(Context context, Cursor c) {
		super(context, R.layout.browse_content_item, c, from, to);
	}
	
	@Override
	public void setViewImage(ImageView v, String value) {
		if (value != null && value.length() > 0){
			v.setTag(value);
		}else{
			v.setTag(null);
			v.setImageResource(R.drawable.cast_placeholder);
		}
	}
}
