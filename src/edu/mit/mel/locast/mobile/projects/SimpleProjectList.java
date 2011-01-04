package edu.mit.mel.locast.mobile.projects;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Project;

public class SimpleProjectList extends ListActivity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		final Intent intent = getIntent();
		final String action = intent.getAction();
		if (Intent.ACTION_PICK.equals(action)){
			setTitle(R.string.project_pick_title);
		}

		Uri data = getIntent().getData();
		if (data == null){
			data = Project.CONTENT_URI;
		}

		final Cursor c = managedQuery(data, Project.PROJECTION, null, null, Project.SORT_ORDER_DEFAULT);
		final String[] from = {Project._TITLE, Project._DESCRIPTION};
		final int[] to = {android.R.id.text1, android.R.id.text2};
		setListAdapter(new SimpleCursorAdapter(this, android.R.layout.simple_list_item_2, c, from, to));
	}

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final Intent projectIntent = new Intent().setData(ContentUris.withAppendedId(Project.CONTENT_URI, id));

		final Intent activityIntent = getIntent();
		final String action = activityIntent.getAction();
		if (Intent.ACTION_PICK.equals(action)){
			setResult(RESULT_OK, projectIntent);
			finish();
		}else if (Intent.ACTION_VIEW.equals(action)){
			projectIntent.setAction(Intent.ACTION_VIEW);
			startActivity(projectIntent);
		}
	}
}
