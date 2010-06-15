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
import java.util.Calendar;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.Dialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;
import edu.mit.mel.locast.mobile.ListUtils;
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
	private Set<String> members = new TreeSet<String>();
	
	final private static int NEW_ITEM = -1;
	private int id = NEW_ITEM;

	private Calendar fromDate = null;
	private Calendar toDate = null;
	
	final DateFormat dateFormat = SimpleDateFormat.getDateInstance(SimpleDateFormat.LONG);
	
	static final int DATE_DIALOG_FROM = 0;
	static final int DATE_DIALOG_TO = 1;
	
	static final int REQUEST_ADD_CAST = 0,
					 REQUEST_PICK_PROJECT_TO_ADD = 1,
					 REQUEST_PICK_CAST_TO_ADD = 2;

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.editproject);

		title = (EditText) findViewById(R.id.projectTitle);
		description = (EditText) findViewById(R.id.projectDescription);
		
		// title.extendSelection(title.length());$
		fromDateEntry = (Button) findViewById(R.id.projectStartDate);

		fromDateEntry.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showDialog(DATE_DIALOG_FROM);
			}
		});

		toDateEntry = (Button) findViewById(R.id.projectEndDate);

		toDateEntry.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				showDialog(DATE_DIALOG_TO);
			}
		});

		((Button)findViewById(R.id.done)).setOnClickListener(this);
		((Button)findViewById(R.id.cancel)).setOnClickListener(this);
		
		tagList = (TagListView) findViewById(R.id.new_project_tags);
		memberEdit = ((Button)findViewById(R.id.people));
		memberEdit.setOnClickListener(this);

		privacy = ((Spinner)findViewById(R.id.privacy));
		
		// display the current date
		// updateDisplay();
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

	protected void loadFromUri(Uri uri){
		thisProject = uri;
		final Cursor c = getContentResolver().query(uri, Project.PROJECTION, null, null, null);
		c.moveToFirst();
		loadFromCursor(uri, c);
		c.close();
	}
	protected void loadFromCursor(Uri projectUri, Cursor c){
		if (!Project.canEdit(c)){
			Toast.makeText(this, getText(R.string.error_cannot_edit), Toast.LENGTH_LONG).show();
			finish();
		}
		title.setText(c.getString(c.getColumnIndex(Project._TITLE)));
		((EditText)findViewById(R.id.projectDescription)).setText(c.getString(c.getColumnIndex(Project._DESCRIPTION)));
		/*
		if (! c.isNull(c.getColumnIndex(Project._START_DATE))){
			fromDate = Calendar.getInstance();
			fromDate.setTimeInMillis(c.getLong(c.getColumnIndex(Project._START_DATE)));
		}
		if (! c.isNull(c.getColumnIndex(Project._END_DATE))){
			toDate = Calendar.getInstance();
			toDate.setTimeInMillis(c.getLong(c.getColumnIndex(Project._END_DATE)));
		}
		updateDates();*/
		id = c.getInt(c.getColumnIndex(Project._ID));
		tagList.addTags(Project.getTags(getContentResolver(), projectUri));
		//casts = Project.getListLong(c.getColumnIndex(Project._CASTS), c);
		/*final List<String> memberList = Project.getList(c.getColumnIndex(Project._MEMBERS), c);
		members = new TreeSet<String>(memberList);
		updateMembers();*/
		
		if (! c.isNull(c.getColumnIndex(Cast._PRIVACY))){
			privacy.setSelection(Arrays.asList(Project.PRIVACY_LIST).indexOf(c.getString(c.getColumnIndex(Cast._PRIVACY))));
			privacy.setEnabled(Project.canChangePrivacyLevel(c));
		}
	}
	
	protected ContentValues toContentValues(){
		final ContentValues cv = new ContentValues();
		
		cv.put(Project._TITLE, title.getText().toString());
		cv.put(Project._DESCRIPTION, description.getText().toString());
		/*
		cv.put(Project._START_DATE, (fromDate != null) ? fromDate.getTimeInMillis(): null);
		cv.put(Project._END_DATE, (toDate != null) ? toDate.getTimeInMillis() : null);
		
		Project.putList(Project._CASTS, cv, casts);
		Project.putList(Project._MEMBERS, cv, new Vector<String>(members));
		*/
		cv.put(Project._PRIVACY, Project.PRIVACY_LIST[privacy.getSelectedItemPosition()]);
		
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
	
	protected void addCast(Uri castUri){
		casts.add(ContentUris.parseId(castUri));
	}
	
	@Override
	protected void onPrepareDialog(int id, Dialog dialog) {
		super.onPrepareDialog(id, dialog);
		switch (id){
		case DATE_DIALOG_FROM:
			if (fromDate != null){
				((DatePickerDialog)dialog).updateDate(fromDate.get(Calendar.YEAR), fromDate.get(Calendar.MONTH), fromDate.get(Calendar.DAY_OF_MONTH));
			}
			break;
		case DATE_DIALOG_TO:
			if (toDate != null){
				((DatePickerDialog)dialog).updateDate(toDate.get(Calendar.YEAR), toDate.get(Calendar.MONTH), toDate.get(Calendar.DAY_OF_MONTH));
			}
			break;
		}
	}
	@Override
	protected Dialog onCreateDialog(int id) {
		Calendar init;
		switch (id) {
		case DATE_DIALOG_FROM:
			if (fromDate != null){
				init = fromDate;
			}else{
				 init = Calendar.getInstance();
			}
			return new DatePickerDialog(this, mDateSetListener, init.get(Calendar.YEAR), init.get(Calendar.MONTH), init.get(Calendar.DAY_OF_MONTH));

		case DATE_DIALOG_TO:
			if (toDate != null){
				init = toDate;
			}else{
				init = Calendar.getInstance();
			}
			return new DatePickerDialog(this, mDateSetListener2, init.get(Calendar.YEAR), init.get(Calendar.MONTH), init.get(Calendar.DAY_OF_MONTH));
		}
		return null;
	}

	private final DatePickerDialog.OnDateSetListener mDateSetListener = new DatePickerDialog.OnDateSetListener(){

		public void onDateSet(DatePicker view, int year, int monthOfYear,
				int dayOfMonth) {
			if (fromDate == null){
				fromDate = Calendar.getInstance();
			}
			fromDate.set(year, monthOfYear, dayOfMonth, 12, 00);
			updateDates();
		}
	};

	private final DatePickerDialog.OnDateSetListener mDateSetListener2 = new DatePickerDialog.OnDateSetListener() {

		public void onDateSet(DatePicker view, int year, int monthOfYear,
				int dayOfMonth) {
			if (toDate == null){
				toDate = Calendar.getInstance();
			}
			toDate.set(year, monthOfYear, dayOfMonth, 12, 00);
			updateDates();
		}
	};

	public void onClick(View v) {
		switch (v.getId()){
		case R.id.done:
			save();
			finish();
			break;
			
		case R.id.cancel:
			finish();
			break;
			
		case R.id.people:
			final AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
			final EditText memText = new EditText(this);
			memText.setText(ListUtils.join(members, " "));
			alertBuilder.setView(memText);
			
			/* this doesn't work, as the widget wasn't very well made
			
			final TagList personTagList = new TagList(this);
			personTagList.addTags(members);
			final LayoutParams lp = new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT);
			d.setContentView(personTagList);
			*/
			alertBuilder.setCancelable(true);
			alertBuilder.setTitle("Select group members");
			alertBuilder.setMessage("Enter a space-separated list of usernames");
			final DialogInterface.OnClickListener dOnClick = new DialogInterface.OnClickListener() {
				
				public void onClick(DialogInterface dialog, int which) {
					switch (which){
					case DialogInterface.BUTTON_POSITIVE:
						final Set<String> v = new TreeSet<String>();
						for (final String member:memText.getText().toString().split("\\s+")){
							if (member.length() == 0){
								continue;
							}
							v.add(member);
						}
						members = v;
						updateMembers();
						dialog.dismiss();
						break;
						
					case DialogInterface.BUTTON_NEGATIVE:
						dialog.cancel();
						break;
					}
					
				}
			};
			alertBuilder.setPositiveButton(R.string.done, dOnClick);
			alertBuilder.setNegativeButton(android.R.string.cancel, dOnClick);
			
			alertBuilder.show();
		}
	}
	
	private void updateDates(){
		fromDateEntry.setText((fromDate != null) ? dateFormat.format(fromDate.getTime()) : "" );
		toDateEntry.setText((toDate != null) ? dateFormat.format(toDate.getTime()) : "" );
	}
	
	private void updateMembers(){
		memberEdit.setText(ListUtils.join(members, ", "));
	}
	
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
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
