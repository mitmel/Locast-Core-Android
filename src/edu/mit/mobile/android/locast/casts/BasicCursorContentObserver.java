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
import android.app.Activity;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Handler;

/**
 * To use this, create have your Activity implement {@link BasicCursorContentObserverWatcher} and then create a {@link BasicCursorContentObserver} in your activity:
 * <blockquote><code>
 * private final BasicCursorContentObserver mContentObserver = new BasicCursorContentObserver(this);
 * </code></blockquote>
 *
 * Then in your {@link Activity#onPause onPause} and {@link Activity#onResume onResume} methods, do:
 *<pre>
 * {@code
 *	protected void onPause() {
 *		super.onPause();
 * 		mContentObserver.onPause(c);
 *	}
 * }
 *
 *</pre>
 *
 *
 */
public class BasicCursorContentObserver extends ContentObserver {
	private final BasicCursorContentObserverWatcher mObservable;
	private Cursor mCursor;

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
		final Cursor c = mCursor;
		if (!selfChange){
			c.requery();
		}

		if (!c.isClosed() && c.moveToFirst()){
			mObservable.loadFromCursor();
		}else{
			mObservable.onCursorItemDeleted();
		}
	}

	/**
	 * Call this from your activity's onPause method. This unregisters the content observer.
	 *
	 * @param c
	 */
	public void onPause(Cursor c){
		if (c != null){
			c.unregisterContentObserver(this);
			mCursor = null;
		}
	}

	/**
	 * Call this from your activity's onResume function with the cursor you wish to observe.
	 * This registers the content observer and calls your loadFromCursor method.
	 *
	 * @param c
	 */
	public void onResume(Cursor c){
		if (c != null){
			mCursor = c;
			c.registerContentObserver(this);
			if (c.moveToFirst()){
				mObservable.loadFromCursor();
			}else{
				// handle the case where this item is deleted
				mObservable.onCursorItemDeleted();
			}
		}
	}

	// XXX gotta come up with a better name than this...
	/**
	 * Implement this to load your content from the cursor. {@link #loadFromCursor} will be called each time that your content refreshes as well as on first load.
	 * @author steve
	 *
	 */
	public interface BasicCursorContentObserverWatcher {

		/**
		 * Add the code necessary to load data from the cursor.
		 * Called from the cursor's {@link ContentObserver#onChange(boolean)} method.
		 */
		public void loadFromCursor();
		/**
		 * Called when the item being observed is deleted.
		 */
		public void onCursorItemDeleted();
	}
}
