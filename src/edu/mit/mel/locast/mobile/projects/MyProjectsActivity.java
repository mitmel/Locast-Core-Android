package edu.mit.mel.locast.mobile.projects;

import android.app.ListActivity;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.CursorAdapter;
import android.widget.ListView;
import android.widget.TwoLineListItem;
import android.widget.AdapterView.AdapterContextMenuInfo;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.SettingsActivity;
import edu.mit.mel.locast.mobile.data.Project;

public class MyProjectsActivity extends ListActivity {
	
	//Selection of columns that we want in the cursor

	
	private static final int MENU_ADD_CAST = 0;
	private static final int MENU_VIEW_PROJECT = 1;
	private static final int MENU_EDIT_PROJECT = 2;

    @Override
	public void onCreate(Bundle savedInstanceState) {
    	requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
    	super.onCreate(savedInstanceState);
    	
    	final Cursor cursor = getContentResolver().query(Project.CONTENT_URI, Project.PROJECTION, null, null, null);
        // Use our own list adapter
        setListAdapter(new MyProjectsListAdapter(getApplicationContext(), cursor));
        registerForContextMenu(this.getListView());
    }


    //List Adapter for Projects List
    private class MyProjectsListAdapter extends CursorAdapter {
    	
        public MyProjectsListAdapter(Context context, Cursor cursor) {
            super(context,cursor);
        }

		@Override
		public void bindView(View view, Context context, Cursor c) {
			final TwoLineListItem  v = (TwoLineListItem)view;
			v.getText1().setText(c.getString(c.getColumnIndex(Project.TITLE)));
			
			v.getText2().setText(Project.getMemberList(getApplicationContext(), c));
		}

		@Override
		public View newView(Context context, Cursor cursor, ViewGroup parent) {
			final LayoutInflater mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
			final TwoLineListItem  v = (TwoLineListItem)mInflater.inflate(R.layout.project_list_item, parent, false);
			
			bindView(v, context, cursor);

			return v;
		}
	
    } 	

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		super.onListItemClick(l, v, position, id);
		final String action = getIntent().getAction();
		final Uri projectUri = ContentUris.withAppendedId(Project.CONTENT_URI, id);
		if (Intent.ACTION_VIEW.equals(action)){
			startActivity(new Intent(Intent.ACTION_VIEW, projectUri));
			
		}else if (Intent.ACTION_PICK.equals(action)){
			setResult(RESULT_OK, new Intent().setData(ContentUris.withAppendedId(getIntent().getData(), id)));
			finish();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
        final MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.projects_menu, menu);
        return true;
    }
    
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
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
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		menu.add(0, MENU_VIEW_PROJECT, 0, "View Project");
		menu.add(0, MENU_ADD_CAST, 0, "Add Cast");
		menu.add(0, MENU_EDIT_PROJECT, 0, "Edit Project");
	}
	
	@Override
	public boolean onContextItemSelected(MenuItem item) {
		  final AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		  final Uri projectUri = ContentUris.withAppendedId(Project.CONTENT_URI, info.id);
		  
		  switch (item.getItemId()) {
		  case MENU_ADD_CAST:
			  startActivity(new Intent(EditProjectActivity.ACTION_ADD_CAST, projectUri));
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
}