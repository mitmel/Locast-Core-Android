package edu.mit.mobile.android.locast.projects;
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

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.casts.BasicCursorContentObserver;
import edu.mit.mobile.android.locast.casts.BasicCursorContentObserver.BasicCursorContentObserverWatcher;
import edu.mit.mobile.android.locast.casts.CastCursorAdapter;
import edu.mit.mobile.android.locast.casts.CastListActivity;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Project;
import edu.mit.mobile.android.locast.data.ShotList;
import edu.mit.mobile.android.locast.templates.TemplateActivity;
import edu.mit.mobile.android.locast.widget.TagListView;

	public class ProjectDetailsActivity extends CastListActivity
		implements OnClickListener, OnCreateContextMenuListener, BasicCursorContentObserverWatcher {
		private TagListView tagList;

		private final static int MENU_ITEM_VIEW_CAST = 0,
								 MENU_ITEM_REMOVE_CAST = 1;

		private BaseAdapter castAdapter;
		private Cursor c;
		private Uri mProjectUri;
		private Uri mProjectCasts;

		private final BasicCursorContentObserver mContentObserver = new BasicCursorContentObserver(this);

		@Override
		public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);

	        mProjectCasts = Uri.withAppendedPath(getIntent().getData(), Cast.PATH);
	        castAdapter = new CastCursorAdapter(this,
	        				managedQuery(mProjectCasts,
	        						Cast.PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT));

	        setListAdapter(castAdapter);
	        setCastsUri(mProjectCasts);

	        // this needs to get called after setListAdapter()
	        setContentView(R.layout.projectdetails);

	        final ListView castList = getListView();

	        castList.setVerticalScrollBarEnabled(false);

	        castList.setEmptyView(findViewById(R.id.empty));

	        tagList = ((TagListView)findViewById(R.id.tags));

	        startService(new Intent(Intent.ACTION_SYNC, mProjectCasts));

	        /////////// intent handling //////////////
	        final String action = getIntent().getAction();

	        if (Intent.ACTION_VIEW.equals(action)){
	        	mProjectUri = getIntent().getData();
	        	c = managedQuery(mProjectUri, Project.PROJECTION, null, null, null);
	        	c.moveToFirst();
	        	loadFromCursor();
	        }
	    }

		@Override
		protected void onPause() {
			super.onPause();
			mContentObserver.onPause(c);
		}

		@Override
		protected void onResume() {
			super.onResume();
			mContentObserver.onResume(c);
		}

		public void loadFromCursor(){
			((TextView)findViewById(R.id.item_title)).setText(c.getString(c.getColumnIndex(Project._TITLE)));
			((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Project._DESCRIPTION)));

			tagList.addTags(Project.getTags(getContentResolver(), mProjectUri));
		}

		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
	        final MenuInflater inflater = getMenuInflater();
	        inflater.inflate(R.menu.projectsdetails_menu, menu);

	        if (c != null && !c.isClosed()){
	        	c.moveToFirst(); // XXX this shouldn't be necessary. Maybe an observer is confused?
	        	final MenuItem editItem = menu.findItem(R.id.project_edit);
	        	editItem.setEnabled(Project.canEdit(this, c));
	        }
	        return true;
	    }

		@Override
		public boolean onOptionsItemSelected(MenuItem item) {
			switch (item.getItemId()) {
				case R.id.project_new_cast: {
					startActivity(new Intent(TemplateActivity.ACTION_RECORD_TEMPLATED_VIDEO, Uri.withAppendedPath(getIntent().getData(), ShotList.PATH)));

					break;
				}
				case R.id.project_edit: {
					startActivity(new Intent(Intent.ACTION_EDIT,
							getIntent().getData()));
					break;
				}
			}
			return super.onOptionsItemSelected(item);
		}

		public void onClick(View v) {
			switch (v.getId()){
			case R.id.project_cast_add:
				startActivity(new Intent(TemplateActivity.ACTION_RECORD_TEMPLATED_VIDEO, Uri.withAppendedPath(getIntent().getData(), ShotList.PATH)));
				break;
			/*case R.id.project_join:
				startActivity(new Intent(EditProjectActivity.ACTION_TOGGLE_MEMBERSHIP,
						getIntent().getData()));
				break;
				*/
			}

		}

		@Override
		public void onCursorItemDeleted() {
			finish();
		}
	}


