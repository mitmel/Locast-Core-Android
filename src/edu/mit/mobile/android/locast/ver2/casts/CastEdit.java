package edu.mit.mobile.android.locast.ver2.casts;

import java.util.HashSet;
import java.util.Set;

import android.content.ContentValues;
import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4_map.app.MapFragmentActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TabHost;
import android.widget.TextView;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.utils.ResourceUtils;
import edu.mit.mobile.android.widget.CheckableTabWidget;

public class CastEdit extends MapFragmentActivity implements OnClickListener {

	// stateful
	private final boolean isDraft = true;
	private Location mLocation;
	private Set<String> mTags;

	// stateless
	private Uri mCast;
	private EditText mTitleView;
	private Button mSaveButton;
	private EditText mDescriptionView;
	private Button mCenterOnMyLocation;

	private TabHost mTabHost;
	private CheckableTabWidget mTabWidget;


	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cast_edit);

		// configure tabs
		mTabHost = (TabHost) findViewById(android.R.id.tabhost);
		mTabWidget = (CheckableTabWidget) findViewById(android.R.id.tabs);
		mTabHost.setup();

		mTabHost.addTab(mTabHost.newTabSpec("location").setIndicator(getTabIndicator(R.layout.tab_indicator_left, "Location", R.drawable.ic_tab_location)).setContent(R.id.cast_edit_location));
		mTabHost.addTab(mTabHost.newTabSpec("media").setIndicator(getTabIndicator(R.layout.tab_indicator_middle, "Photos", R.drawable.ic_tab_media)).setContent(R.id.cast_edit_media));
		mTabHost.addTab(mTabHost.newTabSpec("details").setIndicator(getTabIndicator(R.layout.tab_indicator_right, "Details", R.drawable.ic_tab_details)).setContent(R.id.cast_edit_details));

		// find the other widgets
		mTitleView = (EditText) findViewById(R.id.title);
		mSaveButton = (Button) findViewById(R.id.save);
		mDescriptionView = (EditText) findViewById(R.id.description);
		mCenterOnMyLocation = (Button) findViewById(R.id.center_on_current_location);

		// hook in buttons
		mSaveButton.setOnClickListener(this);
		mCenterOnMyLocation.setOnClickListener(this);


		final Intent intent = getIntent();
		final String action = intent.getAction();

		if (Intent.ACTION_EDIT.equals(action)){

		}else if (Intent.ACTION_INSERT.equals(action)){
			setTitleFromIntent(intent);
		}
	}

	private View getTabIndicator(int layout, CharSequence title, int drawable){
		final LayoutInflater inflater = getLayoutInflater();

		final TextView ind = (TextView) inflater.inflate(layout, mTabHost, false);
		ind.setCompoundDrawablesWithIntrinsicBounds(0, drawable, 0, 0);
		ind.setText(title);
		return ind;
	}

	private void setTitleFromIntent(Intent intent){
		final Uri data = intent.getData();
		final String parentType = getContentResolver().getType(ProviderUtils.removeLastPathSegment(data));

		if (MediaProvider.TYPE_ITINERARY_ITEM.equals(parentType)){
			setTitle(ResourceUtils.getText(this, R.string.add_cast_to_x, parentType));

		}else if (MediaProvider.TYPE_CAST_DIR.equals(parentType)){
			setTitle(getString(R.string.edit_cast));
		}
	}

	@Override
	public void setTitle(int titleId) {
		((TextView)findViewById(android.R.id.title)).setText(titleId);
		super.setTitle(titleId);
	}

	@Override
	public void setTitle(CharSequence title) {
		((TextView)findViewById(android.R.id.title)).setText(title);
		super.setTitle(title);
	}

	private void initNewCast(){
		mTags = new HashSet<String>();
	}

	/**
	 * Reads from the UI and stateful variables, saving to a ContentValues.
	 *
	 * @return
	 */
	public ContentValues toContentValues(){
		final ContentValues cv = new ContentValues();

		cv.put(Cast._TITLE, mTitleView.getText().toString());
		cv.put(Cast._DRAFT, isDraft);
		cv.put(Cast._DESCRIPTION, mDescriptionView.getText().toString());

		return cv;
	}

	private boolean validateEntries(){
		if (mTitleView.getText().toString().trim().length() == 0){
			mTitleView.setError(getText(R.string.error_please_enter_a_title));
			return false;
		}

		if (mLocation == null){
			// focus tab on location
			return false;
		}

		return true;
	}


	public boolean save(){
		if (validateEntries()){

		}
		return true;
	}

	@Override
	protected boolean isRouteDisplayed() {

		return false;
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.save:
			save();
			break;

		case R.id.center_on_current_location:
			mTabWidget.setTabChecked(0, true);
			break;
		}

	}
}
