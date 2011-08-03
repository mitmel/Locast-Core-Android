package edu.mit.mobile.android.locast.test;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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
