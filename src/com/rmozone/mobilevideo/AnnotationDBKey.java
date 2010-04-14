package com.rmozone.mobilevideo;

import android.app.Activity;
import android.database.Cursor;
import android.net.Uri;
import android.provider.BaseColumns;
import android.widget.ListAdapter;
import android.widget.SimpleCursorAdapter;

public class AnnotationDBKey {
	public static final class Annotation implements BaseColumns {
		public static final Uri CONTENT_URI = Uri.parse("content://com.rmozone.mobilevideo/annotations");
		public static final String CONTENT_TYPE = "annotation_list";
		public static final String CONTENT_ITEM_TYPE = "annotation";
		public static final String DEFAULT_SORT_ORDER = "time ASC";
		
		/*
		 * Refer to the video in question by a unique identifier (eg. MD5sum of video file)
		 * Type: STRING
		 */
		public static final String VIDEO_UID = "video";
		
		/*
		 * When in the video this annotation refers (ms)
		 * Type: INTEGER (long)
		 */
		public static final String ANNOTATION_TIME = "time";
		
		/*
		 * The comment itself
		 * Type: STRING
		 */
		public static final String ANNOTATION = "annotation";
		
		private static final String[] PROJECTION = new String[] {
			Annotation._ID, // 0
			Annotation.ANNOTATION_TIME, // 1
			Annotation.ANNOTATION, // 2
		};
		
		/*
		 * Get a cursor for annotations from an md5sum.
		 */
		public static Cursor md5ToCursor(Activity act, String md5) {
    		return act.managedQuery(CONTENT_URI,
    				PROJECTION,
    				VIDEO_UID + "='" + md5+"'",
    				null,
    				DEFAULT_SORT_ORDER);
		}
		
		/*
		 * An annotation adapter!
		 */
		public static ListAdapter getAnnotationAdapter(Activity act, Cursor c) {
			return new SimpleCursorAdapter(act,
    				android.R.layout.two_line_list_item,
    				c, 
    				new String[] { Annotation.ANNOTATION_TIME, Annotation.ANNOTATION},
    				new int[] {android.R.id.text1, android.R.id.text2});
		}
		
	}

}
