package edu.mit.mobile.android.locast.test;

import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.test.ApplicationTestCase;
import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.Project;

public class ApplicationTest extends ApplicationTestCase<Application> {

	public ApplicationTest() {
		super(Application.class);
	}


	public void testActivityIntentFilters(){
		final PackageManager pm = mContext.getPackageManager();
		final Uri[] itemIndexes = new Uri[]{
				Itinerary.CONTENT_URI,
				Project.CONTENT_URI,
				//Itinerary.getCastsUri(ContentUris.withAppendedId(Itinerary.CONTENT_URI, 1))
				};
		for (final Uri item: itemIndexes){
			Intent intent = new Intent(Intent.ACTION_VIEW, item);
			intent.addCategory(Intent.CATEGORY_DEFAULT);
			assertTrue(intent + " does not match any intent filters", pm.queryIntentActivities(intent, 0).size() > 0);

			final Uri itemChild = ContentUris.withAppendedId(item, 1);
			intent = new Intent(Intent.ACTION_VIEW, itemChild);
			intent.addCategory(Intent.CATEGORY_DEFAULT);
			assertTrue(intent + " does not match any intent filters", pm.queryIntentActivities(intent, 0).size() > 0);
		}
	}
}
