package edu.mit.mel.locast.mobile.casts;
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
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;

public class BasicCursorContentObserver extends ContentObserver {
	private final BasicCursorContentObserverWatcher mObservable;

	public BasicCursorContentObserver(BasicCursorContentObserverWatcher observable) {
		super(new Handler());
		mObservable = observable;
	}

	@Override
	public boolean deliverSelfNotifications() {
		return true;
	}

	@Override
	public void onChange(boolean selfChange) {
		final Cursor c = mObservable.getCursor();
		if (!selfChange){
			c.requery();
		}

		if (!c.isClosed() && c.moveToFirst()){
			mObservable.loadFromCursor();
		}else{
			mObservable.onCursorItemDeleted();
		}
	}

	// XXX gotta come up with a better name than this...
	public interface BasicCursorContentObserverWatcher {
		public void loadFromCursor();
		public Cursor getCursor();
		public void onCursorItemDeleted();
	}
}
