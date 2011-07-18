package edu.mit.mobile.android.locast.ver2.casts;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.TextView;
import edu.mit.mobile.android.content.ProviderUtils;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.ver2.R;

public class CastEdit extends FragmentActivity {
	//

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.cast_edit);
		findViewById(R.id.refresh).setVisibility(View.GONE);
		setTitleFromIntent(getIntent());
	}

	private void setTitleFromIntent(Intent intent){
		final Uri data = intent.getData();
		final String parentType = getContentResolver().getType(ProviderUtils.removeLastPathSegment(data));
		if (MediaProvider.TYPE_ITINERARY_ITEM.equals(parentType)){
			//setTitle(ResourceUtils.getText(this, R.string.add_cast_to_x, parentType));
			setTitle(getText(R.string.add_cast_to_x));
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

}
