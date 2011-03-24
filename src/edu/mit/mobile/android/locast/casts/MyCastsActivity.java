package edu.mit.mobile.android.locast.casts;
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
import android.os.Bundle;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkClient;

public class MyCastsActivity extends CastListActivity {
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		
		final NetworkClient nc = NetworkClient.getInstance(getApplicationContext());
		
		loadList(managedQuery(Cast.CONTENT_URI, CastCursorAdapter.DEFAULT_PROJECTION, 
				Cast._AUTHOR+"=?", 
				new String[]{nc.getUsername()}, 
				Cast._MODIFIED_DATE+" DESC"));
	}	
}
