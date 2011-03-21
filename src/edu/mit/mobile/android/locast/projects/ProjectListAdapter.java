package edu.mit.mobile.android.locast.projects;
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
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.SimpleCursorAdapter;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.data.Project;

public class ProjectListAdapter extends SimpleCursorAdapter {
	private final static String[] from = {Project._TITLE, Project._DESCRIPTION};
	private final static int[]    to   = {android.R.id.text1, android.R.id.text2};
	private final OnClickListener buttonListener;
	
	public static final String[] PROJECTION = {
		Project._ID, 
		Project._TITLE, 
		Project._DESCRIPTION
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
