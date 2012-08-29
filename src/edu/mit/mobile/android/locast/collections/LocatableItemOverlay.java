package edu.mit.mobile.android.locast.collections;

/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
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
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.util.Log;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.ItemizedOverlay;
import com.google.android.maps.MapView;
import com.google.android.maps.OverlayItem;
import com.readystatesoftware.mapviewballoons.BalloonItemizedOverlay;

import edu.mit.mobile.android.locast.data.Locatable;
import edu.mit.mobile.android.locast.maps.MapsUtils;

abstract public class LocatableItemOverlay extends BalloonItemizedOverlay<OverlayItem> {
    private static final String TAG = LocatableItemOverlay.class.getSimpleName();

    protected Cursor mLocatableItems;
    private int mLatCol, mLonCol;
    private boolean mShowBalloon = true;

    private ComparableOverlayItem mFocused;

    public static final String[] LOCATABLE_ITEM_PROJECTION = { Locatable.Columns._LATITUDE,
            Locatable.Columns._LONGITUDE };

    private final ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            populateFromCursor();
        }
    };
    private int mSize;

    private OverlayItem mBalloonFocused;

    private boolean mPopulating;

    public LocatableItemOverlay(Drawable marker, MapView mapview) {
        this(marker, mapview, null);
    }

    public LocatableItemOverlay(Drawable marker, MapView mapview, Cursor items) {
        super(marker, mapview);

        mLocatableItems = items;

        populateFromCursor();
    }

    private void populateFromCursor() {
        onPrePopulate();
        populate();
        onPostPopulate();
    }

    public static Drawable boundCenterBottom(Drawable drawable) {
        // why isn't this visible?
        return ItemizedOverlay.boundCenterBottom(drawable);
    }

    public void swapCursor(Cursor locatableItems) {
        mLocatableItems = locatableItems;
        updateCursorCols();
        populateFromCursor();
    }

    public void setShowBalloon(boolean showBalloon) {
        mShowBalloon = showBalloon;
    }

    @Override
    public boolean onTap(GeoPoint p, MapView mapView) {
        if (mShowBalloon) {
            try {
                return super.onTap(p, mapView);

            } catch (final ArrayIndexOutOfBoundsException e) {
                // XXX this error kept cropping up, but no source of the error could be found
                Log.w(TAG, "caught and discarded OOB exception", e);
                return false;
            }
        } else {
            return false;
        }
    }

    protected void onPrePopulate() {
        // updateCursorCols();
        updateSize();
        mFocused = null;
        mBalloonFocused = getFocus();
        mPopulating = true;
    }

    protected void onCreateItem(ComparableOverlayItem i) {
        if (mPopulating) {
            if (mFocused == null && i.equals(mBalloonFocused)) {
                mFocused = i;
            }
        }
    }

    protected void onPostPopulate() {
        if (mFocused == null) {
            if (mBalloonFocused != null) {
                hideBalloon();
            }
        } else {
            try {
                setFocus(mFocused);
            } catch (final ArrayIndexOutOfBoundsException e) {
                // XXX we shouldn't be getting this exception, but at least we're not crashing.
                Log.w(TAG, "error setting focus", e);
            }
        }
        mPopulating = false;
    }

    public void onPause() {
        if (mLocatableItems != null) {
            mLocatableItems.unregisterContentObserver(mContentObserver);
        }
    }

    public void onResume() {
        if (mLocatableItems != null) {
            mLocatableItems.registerContentObserver(mContentObserver);
        }
    }

    public void changeCursor(Cursor locatableItems) {
        final Cursor oldCursor = mLocatableItems;
        mLocatableItems = locatableItems;
        updateCursorCols();
        populateFromCursor();

        if (oldCursor != null && !oldCursor.isClosed()) {
            oldCursor.close();
        }
    }

    protected void updateCursorCols() {
        if (mLocatableItems != null) {
            mLatCol = mLocatableItems.getColumnIndex(Locatable.Columns._LATITUDE);
            mLonCol = mLocatableItems.getColumnIndex(Locatable.Columns._LONGITUDE);
        }
    }

    protected GeoPoint getItemLocation(Cursor item) {
        return MapsUtils.getGeoPoint(item, mLatCol, mLonCol);
    }

    /**
     * this does not work properly when crossing -180/180 boundaries.
     *
     * @see com.google.android.maps.ItemizedOverlay#getCenter()
     */
    @Override
    public GeoPoint getCenter() {
        int maxLat, minLat;
        int maxLon, minLon;

        mLocatableItems.moveToFirst();
        final double[] latLon = new double[2];
        Locatable.toLocationArray(mLocatableItems, mLatCol, mLonCol, latLon);
        maxLat = minLat = (int) (latLon[0] * 1E6);
        maxLon = minLon = (int) (latLon[1] * 1E6);
        mLocatableItems.moveToNext();
        for (; !mLocatableItems.isAfterLast(); mLocatableItems.moveToNext()) {
            Locatable.toLocationArray(mLocatableItems, mLatCol, mLonCol, latLon);
            maxLat = Math.max(maxLat, (int) (latLon[0] * 1E6));
            minLat = Math.min(minLat, (int) (latLon[0] * 1E6));

            maxLon = Math.max(maxLon, (int) (latLon[1] * 1E6));
            minLon = Math.min(minLon, (int) (latLon[1] * 1E6));
        }

        return new GeoPoint((maxLat - minLat) / 2 + minLat, (maxLon - minLon) / 2 + minLon);
    }

    private void updateSize() {

        mSize = mLocatableItems != null ? mLocatableItems.getCount() : 0;
    }

    @Override
    public int size() {
        return mSize;
    }

    public static class ComparableOverlayItem extends OverlayItem {
        private final long mId;

        public ComparableOverlayItem(GeoPoint point, String title, String snippet, long id) {
            super(point, title, snippet);
            mId = id;
        }

        public long getId(){
            return mId;
        }

        @Override
        public boolean equals(Object o) {
            if (o instanceof ComparableOverlayItem) {
                return mId == ((ComparableOverlayItem) o).getId();
            } else {
                return super.equals(o);
            }
        }
    }
}
