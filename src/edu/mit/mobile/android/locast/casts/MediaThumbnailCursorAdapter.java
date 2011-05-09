package edu.mit.mobile.android.locast.casts;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.support.v4.widget.SimpleCursorAdapter;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import edu.mit.mobile.android.locast.R;

public class MediaThumbnailCursorAdapter extends SimpleCursorAdapter {

	public static final int[] IMAGE_IDS = {R.id.media_thumbnail};
	protected final Drawable defaultImage;

	public MediaThumbnailCursorAdapter(Context context, int layout, Cursor c,
			String[] from, int[] to, int flags) {
		super(context, layout, c, from, to, flags);
		final View v = LayoutInflater.from(context).inflate(layout, null, false);
		final ImageView thumb = (ImageView)v.findViewById(IMAGE_IDS[0]);
		defaultImage = thumb.getDrawable();
	}

	@Override
	public void setViewImage(ImageView v, String value) {
		if (v.getId() == IMAGE_IDS[0]){
			v.setImageDrawable(defaultImage);
			if (value != null && value.length() > 0){
				v.setTag(value);
			}else{
				v.setTag(null);
			}
		}
	}
}