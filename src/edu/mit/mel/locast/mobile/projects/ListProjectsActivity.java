package edu.mit.mel.locast.mobile.projects;
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
import java.util.ArrayList;

import org.jsharkey.blog.android.SeparatedListAdapter;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import edu.mit.mel.locast.mobile.MainActivity;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.SettingsActivity;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.data.ShotList;
import edu.mit.mel.locast.mobile.data.TaggableItem;
import edu.mit.mel.locast.mobile.templates.TemplateActivity;

public class ListProjectsActivity extends ListActivity implements OnClickListener {
	public final static String TAG = ListProjectsActivity.class.getSimpleName();

	//Selection of columns that we want in the cursor

	private static final int MENU_ADD_CAST = 0;
	private static final int MENU_VIEW_PROJECT = 1;
	private static final int MENU_EDIT_PROJECT = 2;

    @Override
	public void onCreate(Bundle savedInstanceState) {
    	//requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    	super.onCreate(savedInstanceState);

    	final SeparatedListAdapter separatedList = new SeparatedListAdapter(this, R.layout.list_section_header);
/*    	separatedList.addSection("unpublished", "Unpublished", new ProjectListAdapter(getApplicationContext(),
    			managedQuery(Project.CONTENT_URI, ProjectListAdapter.PROJECTION, Project._PUBLIC_ID+"=null", null, null), this));
    			*/
    	final ArrayList<String> featuredTag = new ArrayList<String>();
    	featuredTag.add(TaggableItem.addPrefixToTag(TaggableItem.SYSTEM_PREFIX, "_featured"));
    	separatedList.addSection("featured", getString(R.string.section_featured), new ProjectListAdapter(getApplicationContext(),
    			managedQuery(TaggableItem.getTagUri(Project.CONTENT_URI, featuredTag), ProjectListAdapter.PROJECTION, null, null, Project.SORT_ORDER_DEFAULT), this));
    	// TODO need nearby
    	separatedList.addSection("all", getString(R.string.section_all), new ProjectListAdapter(getApplicationContext(),
    			managedQuery(Project.CONTENT_URI, ProjectListAdapter.PROJECTION, null, null, Project.SORT_ORDER_DEFAULT), this));


        setListAdapter(separatedList);
        registerForContextMenu(this.getListView());

    }

    @Override
    protected void onStart() {
    	super.onStart();
        startService(new Intent(Intent.ACTION_SYNC, Project.CONTENT_URI));
    }

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final String action = getIntent().getAction();
		final Uri projectUri = ContentUris.withAppendedId(Project.CONTENT_URI, id);

		if (Intent.ACTION_GET_CONTENT.equals(action)){
			setResult(RESULT_OK, new Intent().setData(projectUri));
			finish();
		}else{
			startActivity(new Intent(Intent.ACTION_VIEW, projectUri));
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.projects_menu, menu);
        return true;
    }

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		Log.d(TAG, "onOptionsItemSelected");
		switch (item.getItemId()) {
			case R.id.addProjectMenuItem: {
				final Intent i = new Intent(Intent.ACTION_INSERT, Project.CONTENT_URI);
				startActivity(i);
				break;
			}
			case R.id.settingsMenuItem: {
				final Intent intent = new Intent(this, SettingsActivity.class);
				startActivity(intent);
				break;
			}

			case R.id.reset: {
				MainActivity.resetDBWithConfirmation(this);
			} break;

			default:
				return super.onOptionsItemSelected(item);
		}
		return true;
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, MENU_VIEW_PROJECT, 0, "View Project");
		menu.add(0, MENU_ADD_CAST, 0, "Add Cast");
		//TODO check permission menu.add(0, MENU_EDIT_PROJECT, 0, "Edit Project");
	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		  final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		  final Uri projectUri = ContentUris.withAppendedId(Project.CONTENT_URI, info.id);

		  switch (item.getItemId()) {
		  case MENU_ADD_CAST:
			  startActivity(new Intent(TemplateActivity.ACTION_RECORD_TEMPLATED_VIDEO, Uri.withAppendedPath(projectUri, ShotList.PATH)));
			    return true;
		  case MENU_VIEW_PROJECT:
			  startActivity(new Intent(Intent.ACTION_VIEW, projectUri));
			    return true;
		  case MENU_EDIT_PROJECT:
			  startActivity(new Intent(Intent.ACTION_EDIT, projectUri));
		    return true;
		  default:
		    return super.onContextItemSelected(item);
		  }
	}


	public void onClick(View v) {
		switch (v.getId()){
		case R.id.project_cast_add:
			//startActivity(new Intent(EditProjectActivity.ACTION_ADD_CAST, (Uri)v.getTag()));
			startActivity(new Intent(TemplateActivity.ACTION_RECORD_TEMPLATED_VIDEO, Uri.withAppendedPath((Uri)v.getTag(), ShotList.PATH)));
			break;
		}

	}
}
