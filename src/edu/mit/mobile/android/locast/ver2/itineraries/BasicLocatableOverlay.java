package edu.mit.mobile.android.locast.ver2.itineraries;

import android.database.Cursor;
import android.graphics.drawable.Drawable;

import com.google.android.maps.OverlayItem;

public class BasicLocatableOverlay extends LocatableItemOverlay {

	public BasicLocatableOverlay(Drawable marker) {
		super(marker);
	}

	public BasicLocatableOverlay(Drawable marker, Cursor c) {
		super(marker, c);
	}

	@Override
	protected OverlayItem createItem(int i) {
		this.mLocatableItems.moveToPosition(i);
		return new OverlayItem(getItemLocation(mLocatableItems), "", "");
	}
}
