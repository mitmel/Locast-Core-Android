package edu.mit.mel.locast.mobile.projects;

import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.SimpleCursorAdapter;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Project;

public class ProjectListAdapter extends SimpleCursorAdapter {
	private final static String[] from = {Project.TITLE, Project.DESCRIPTION};
	private final static int[]    to   = {android.R.id.text1, android.R.id.text2};
	private final OnClickListener buttonListener;
	
	public static final String[] PROJECTION = {
		Project._ID, 
		Project.TITLE, 
		Project.DESCRIPTION
		};
	
    public ProjectListAdapter(Context context, Cursor cursor, OnClickListener buttonListener) {
        super(context, R.layout.project_list_item, cursor, from, to);
        this.buttonListener = buttonListener;
    }

	@Override
	public void bindView(View view, Context context, Cursor c) {
		super.bindView(view, context, c);
		final View addCastToProject = view.findViewById(R.id.project_cast_add);
		addCastToProject.setTag(ContentUris.withAppendedId(Project.CONTENT_URI, c.getLong(c.getColumnIndex(Project._ID))));
		addCastToProject.setOnClickListener(buttonListener);
	}
	
}