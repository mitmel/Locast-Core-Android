package edu.mit.mel.locast.mobile.projects;
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

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.view.View.OnCreateContextMenuListener;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

import com.commonsware.cwac.thumbnail.ThumbnailAdapter;

import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.casts.BasicCursorContentObserver;
import edu.mit.mel.locast.mobile.casts.CastCursorAdapter;
import edu.mit.mel.locast.mobile.casts.BasicCursorContentObserver.BasicCursorContentObserverWatcher;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.data.ShotList;
import edu.mit.mel.locast.mobile.templates.TemplateActivity;
import edu.mit.mel.locast.mobile.widget.TagListView;

	public class ProjectDetailsActivity extends ListActivity
		implements OnClickListener, OnItemClickListener, OnCreateContextMenuListener, BasicCursorContentObserverWatcher {
		private TagListView tagList;

		private final static int MENU_ITEM_VIEW_CAST = 0,
								 MENU_ITEM_REMOVE_CAST = 1;

		private BaseAdapter castAdapter;
		private Cursor c;
		private Uri mProjectUri;

		private final BasicCursorContentObserver mContentObserver = new BasicCursorContentObserver(this);

		@Override
		public void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);

	        setContentView(R.layout.projectdetails);
	        final ListView castList = getListView();

	        //castList.addHeaderView(LayoutInflater.from(this).inflate(R.layout.projectdetails, castList, false));

	        castList.setVerticalScrollBarEnabled(false);

	        castList.setEmptyView(findViewById(R.id.empty));
	        //castList.setEmptyView(getLayoutInflater().inflate(R.layout.project_no_casts, castList, false));

	        tagList = ((TagListView)findViewById(R.id.tags));

	        // this defines what images need to be loaded. URLs are placed in the ImageView tag
	        final int[] IMAGE_IDS = {R.id.thumbnail};
	        final Uri projectCasts = Uri.withAppendedPath(getIntent().getData(), Cast.PATH);
	        castAdapter = new ThumbnailAdapter(this,
	        		new CastCursorAdapter(this,
	        				managedQuery(projectCasts,
	        						Cast.PROJECTION, null, null, Cast.SORT_ORDER_DEFAULT)),
	        		((Application)getApplication()).getImageCache(), IMAGE_IDS);

	        setListAdapter(castAdapter);

	        castList.setOnItemClickListener(this);
	        castList.setOnCreateContextMenuListener(this);

	        startService(new Intent(Intent.ACTION_SYNC, projectCasts));

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
			c.unregisterContentObserver(mContentObserver);
		}

		@Override
		protected void onResume() {
			c.registerContentObserver(mContentObserver);
			super.onResume();
		}

		public Cursor getCursor() {
			return c;
		}

		public void loadFromCursor(){
			((TextView)findViewById(R.id.item_title)).setText(c.getString(c.getColumnIndex(Project._TITLE)));
			((TextView)findViewById(R.id.description)).setText(c.getString(c.getColumnIndex(Project._DESCRIPTION)));

			/*
			Calendar fromDate = null, toDate = null;
			if (! c.isNull(c.getColumnIndex(Project._START_DATE))){
				fromDate = Calendar.getInstance();
				fromDate.setTimeInMillis(c.getLong(c.getColumnIndex(Project._START_DATE)));
			}
			if (! c.isNull(c.getColumnIndex(Project._END_DATE))){
				toDate = Calendar.getInstance();
				toDate.setTimeInMillis(c.getLong(c.getColumnIndex(Project._END_DATE)));
			}
			final DateFormat df = SimpleDateFormat.getDateInstance(SimpleDateFormat.MEDIUM);
			final String dateString = ((fromDate != null) ? df.format(fromDate.getTime()) : "")
				+ (( fromDate != null && toDate != null) ? " - " : "")
				+ ((toDate != null) ? df.format(toDate.getTime()) : "");
			((TextView)findViewById(R.id.date)).setText(dateString);
			*/
			tagList.addTags(Project.getTags(getContentResolver(), mProjectUri));

			//castAdapter.notifyDataSetChanged();

			/* membership members = new TreeSet<String>(Project.getList(c.getColumnIndex(Project._MEMBERS), c));
			final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(this);
			if (members.contains(nc.getUsername())){
				mJoinButton.setText(R.string.project_leave);
			}else{
				mJoinButton.setText(R.string.project_join);
			}
			((TextView)findViewById(R.id.people)).setText(Project.getMemberList(getApplicationContext(), c));
			 *
			 */
		}

		@Override
		public boolean onCreateOptionsMenu(Menu menu) {
	        final MenuInflater inflater = getMenuInflater();
	        inflater.inflate(R.menu.projectsdetails_menu, menu);

	        if (c != null && !c.isClosed()){
	        	c.moveToFirst(); // XXX this shouldn't be necessary. Maybe an observer is confused?
	        	final MenuItem editItem = menu.findItem(R.id.project_edit);
	        	editItem.setEnabled(Project.canEdit(c));
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

		@Override
		public void onCreateContextMenu(ContextMenu menu, View v,
				ContextMenuInfo menuInfo) {
			menu.add(Menu.NONE, MENU_ITEM_VIEW_CAST,   Menu.NONE, R.string.view_cast);
			//menu.add(Menu.NONE, MENU_ITEM_REMOVE_CAST, Menu.NONE, R.string.remove_cast);

		}

		@Override
		public boolean onContextItemSelected(MenuItem item) {
			final AdapterView.AdapterContextMenuInfo info;
			switch (item.getItemId()){
			case MENU_ITEM_VIEW_CAST:
				info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
				startActivity(new Intent(Intent.ACTION_VIEW,
						ContentUris.withAppendedId(Cast.CONTENT_URI, info.id)));
				break;

			case MENU_ITEM_REMOVE_CAST:
				info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
				//casts.remove(info.position);
				//updateCasts();
				break;

			default:
				return false;
			}
			return true;
		}

	    /*public class CastListAdapter extends BaseAdapter {
	        int mGalleryItemBackground;
	        final LayoutInflater inflater = getLayoutInflater();

	        public CastListAdapter(Context context) {
	            final TypedArray a = obtainStyledAttributes(R.styleable.Gallery1);
	            mGalleryItemBackground = a.getResourceId(
	                    R.styleable.Gallery1_android_galleryItemBackground, 0);
	            a.recycle();
	        }

	        public int getCount() {
	            return casts.size();
	        }

	        public Object getItem(int position) {
	            return position;
	        }

	        public long getItemId(int position) {
	            return casts.get(position);
	        }

	        public View getView(int position, View convertView, ViewGroup parent) {

	        	ImageView i;
	            if (convertView != null){
	            	i = (ImageView) convertView;
	            }else {
	            	i = (ImageView)inflater.inflate(R.layout.thumbimage, parent, false);

	                // The preferred Gallery item background
	                i.setBackgroundResource(mGalleryItemBackground);
	            }
	            final ContentResolver cr = getContentResolver();
	            final Cursor c = cr.query(ContentUris.withAppendedId(Cast.CONTENT_URI, getItemId(position)), Cast.PROJECTION, null, null, null);
	            if (c.moveToFirst()){

	            	i.setTag(c.getString(c.getColumnIndex(Cast._THUMBNAIL_URI)));
	            	i.setImageResource(R.drawable.cast_placeholder);
	            }
	            c.close();

	            return i;
	        }
	    }*/

	   /* private void updateCasts(){
			final ContentValues cv = new ContentValues();
			Project.putList(Project._CASTS, cv, casts);
			getContentResolver().update(getIntent().getData(), cv, null, null);
	    }*/

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

		public void onItemClick(AdapterView<?> adapter, View v, int position, long itemId) {
			switch (adapter.getId()){
			case android.R.id.list:

				/*startActivity(new Intent(Intent.ACTION_VIEW,
						ContentUris.withAppendedId(Uri.withAppendedPath(getIntent().getData(),Cast.PATH), itemId)));*/
				startActivity(new Intent(Intent.ACTION_VIEW,
						ContentUris.withAppendedId(Cast.CONTENT_URI, itemId)));
			}
		}
	}


