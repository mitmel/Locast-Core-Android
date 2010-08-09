package edu.mit.mel.locast.mobile.casts;

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
		if (!selfChange){
			final Cursor c = mObservable.getCursor();
			c.requery();
			c.moveToFirst();
		}
		mObservable.loadFromCursor();
	}

	// XXX gotta come up with a better name than this...
	public interface BasicCursorContentObserverWatcher {
		public void loadFromCursor();
		public Cursor getCursor();
	}
}
