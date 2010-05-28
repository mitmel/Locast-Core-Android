package edu.mit.mel.locast.mobile.projects;

import org.jsharkey.blog.android.SeparatedListAdapter;

import android.app.ListActivity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.View.OnClickListener;
import android.widget.ListView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.SettingsActivity;
import edu.mit.mel.locast.mobile.data.Project;

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

    	final ContentResolver cr = getContentResolver();

    	final SeparatedListAdapter separatedList = new SeparatedListAdapter(this, R.layout.list_section_header);
    	
    	separatedList.addSection("featured", new ProjectListAdapter(getApplicationContext(), 
    			cr.query(Project.CONTENT_URI, ProjectListAdapter.PROJECTION, Project._ID + "=1", null, null), this));
    	
    	separatedList.addSection("nearby", new ProjectListAdapter(getApplicationContext(), 
    			cr.query(Project.CONTENT_URI, ProjectListAdapter.PROJECTION, null, null, null), this));
    	
        setListAdapter(separatedList);
        registerForContextMenu(this.getListView());
    }

	@Override
	protected void onListItemClick(ListView l, View v, int position, long id) {
		final String action = getIntent().getAction();
		final Uri projectUri = ContentUris.withAppendedId(Project.CONTENT_URI, id);
		
		if (Intent.ACTION_PICK.equals(action)){
			setResult(RESULT_OK, new Intent().setData(ContentUris.withAppendedId(getIntent().getData(), id)));
			finish();
		}else{
			startActivity(new Intent(Intent.ACTION_VIEW, projectUri));
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


	public void onClick(View v) {
		switch (v.getId()){
		case R.id.project_cast_add:
			startActivity(new Intent(EditProjectActivity.ACTION_ADD_CAST, (Uri)v.getTag()));
			break;
		}
		
	}
}