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
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.data.Project;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;
import edu.mit.mel.locast.mobile.widget.TagListView;

public class EditProjectActivity extends Activity implements OnClickListener {
	static final String ACTION_ADD_CAST = "edu.mit.mel.locast.mobile.ACTION_ADD_CAST",
						ACTION_TOGGLE_MEMBERSHIP = "edu.mit.mel.locast.mobile.ACTION_TOGGLE_MEMBERSHIP",
						EXTRAS_CAST_URI = "edu.mit.mel.locast.mobile.EXTRAS_CAST_URI";


	EditText title, description;

	Button fromDateEntry, toDateEntry, memberEdit;

	Spinner privacy;

	private Uri thisProject;

	private TagListView tagList;

	private final List<Long> casts = new Vector<Long>();
	private final Set<String> members = new TreeSet<String>();

	final private static int NEW_ITEM = -1;
	private int id = NEW_ITEM;

	final DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.LONG);

	static final int DATE_DIALOG_FROM = 0;
	static final int DATE_DIALOG_TO = 1;

	static final int REQUEST_ADD_CAST = 0,
					 REQUEST_PICK_PROJECT_TO_ADD = 1,
					 REQUEST_PICK_CAST_TO_ADD = 2;

	@Override
	public void onCreate(final Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.editproject);

		title = (EditText) findViewById(R.id.projectTitle);
		description = (EditText) findViewById(R.id.projectDescription);

		((Button)findViewById(R.id.done)).setOnClickListener(this);
		((Button)findViewById(R.id.cancel)).setOnClickListener(this);

		tagList = (TagListView) findViewById(R.id.new_project_tags);

		privacy = ((Spinner)findViewById(R.id.privacy));

		final Uri data = getIntent().getData();
		final String action = getIntent().getAction();

		if (Intent.ACTION_EDIT.equals(action)){
			loadFromUri(data);

		} else if (Intent.ACTION_ATTACH_DATA.equals(action)){
			startActivityForResult(new Intent(Intent.ACTION_PICK, Project.CONTENT_URI), REQUEST_PICK_PROJECT_TO_ADD);

		}else if (ACTION_TOGGLE_MEMBERSHIP.equals(action)){
			loadFromUri(data);
			final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(this);
			final String me = nc.getUsername();
			if (members.contains(me)){
				members.remove(me);
				Toast.makeText(this, getString(R.string.project_you_left, title.getText().toString()), Toast.LENGTH_LONG).show();
			}else{
				members.add(me);
				Toast.makeText(this, getString(R.string.project_you_joined, title.getText().toString()), Toast.LENGTH_LONG).show();
			}
			save();
			finish();

		} else if (ACTION_ADD_CAST.equals(action)){
			if (getIntent().getExtras() != null && getIntent().getExtras().containsKey(EXTRAS_CAST_URI)){
				addCast((Uri)getIntent().getExtras().get(EXTRAS_CAST_URI));
				loadFromUri(data);
				save();
				setResult(RESULT_OK);
				finish();
			}else{
				startActivityForResult(new Intent(Intent.ACTION_PICK, Cast.CONTENT_URI), REQUEST_ADD_CAST);
			}
		}
	}

	protected void loadFromUri(final Uri uri){
		thisProject = uri;
		final Cursor c = getContentResolver().query(uri, Project.PROJECTION, null, null, null);
		c.moveToFirst();
		loadFromCursor(uri, c);
		c.close();
	}
	protected void loadFromCursor(final Uri projectUri, final Cursor c){
		if (!Project.canEdit(c)){
			Toast.makeText(this, getText(R.string.error_cannot_edit), Toast.LENGTH_LONG).show();
			finish();
		}
		title.setText(c.getString(c.getColumnIndex(Project._TITLE)));
		((EditText)findViewById(R.id.projectDescription)).setText(c.getString(c.getColumnIndex(Project._DESCRIPTION)));

		id = c.getInt(c.getColumnIndex(Project._ID));
		tagList.addTags(Project.getTags(getContentResolver(), projectUri));

		if (! c.isNull(c.getColumnIndex(Cast._PRIVACY))){
			privacy.setSelection(Arrays.asList(Project.PRIVACY_LIST).indexOf(c.getString(c.getColumnIndex(Cast._PRIVACY))));
			privacy.setEnabled(Project.canChangePrivacyLevel(c));
		}
	}

	protected ContentValues toContentValues(){
		final ContentValues cv = new ContentValues();

		cv.put(Project._TITLE, title.getText().toString());
		cv.put(Project._DESCRIPTION, description.getText().toString());
		cv.put(Project._PRIVACY, Project.PRIVACY_LIST[privacy.getSelectedItemPosition()]);
		cv.put(Project._DRAFT, false);

		return cv;
	}

	protected void save(){
		final ContentResolver cr = getContentResolver();
		if (id == NEW_ITEM){
			thisProject = cr.insert(Project.CONTENT_URI, toContentValues());
		}else{
			cr.update(thisProject, toContentValues(), null, null);
		}
		Project.putTags(getContentResolver(), thisProject, tagList.getTags());
	}

	protected void addCast(final Uri castUri){
		casts.add(ContentUris.parseId(castUri));
	}

	public void onClick(final View v) {
		switch (v.getId()){
		case R.id.done:
			save();
			finish();
			break;

		case R.id.cancel:
			finish();
			break;

		}
	}

	@Override
	protected void onActivityResult(final int requestCode, final int resultCode, final Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch (requestCode){
		case REQUEST_ADD_CAST:
			if (resultCode == RESULT_OK){
				loadFromUri(getIntent().getData());
				casts.add(ContentUris.parseId(data.getData()));
				save();
				Toast.makeText(this, getString(R.string.added_cast_to, title.getText().toString()), Toast.LENGTH_LONG).show();
				finish();
			}else if (resultCode == RESULT_CANCELED){
				finish();
			}
			break;

		case REQUEST_PICK_PROJECT_TO_ADD:
			if (resultCode == RESULT_OK){
				loadFromUri(data.getData()); // this is slightly backwards, we didn't start knowing which project we were
				// we started with an attach intent, so we already have the cast URI
				casts.add(ContentUris.parseId(getIntent().getData()));
				save();
				Toast.makeText(this, getString(R.string.added_cast_to, title.getText().toString()), Toast.LENGTH_LONG).show();
				finish();
			}else if (resultCode == RESULT_CANCELED){
				finish();
			}
			break;
		}
	}
}
