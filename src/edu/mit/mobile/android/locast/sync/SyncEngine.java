package edu.mit.mobile.android.locast.sync;
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

import android.content.Context;
import android.net.Uri;

public class SyncEngine {
	public SyncEngine(Context context, edu.mit.mobile.android.locast.net.NetworkClient networkClient) {

	}

	public boolean sync(Uri tosync){
		return true;
	}

	/*
	 * cases:
	 * get item from remote server
	 * 	usually a list of items
	 *  sometimes an individual one
	 *
	 * upload a single item to the server
	 */

	public boolean syncItem(Uri item ){
		return true;
	}
}
