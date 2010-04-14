package edu.mit.mel.locast.mobile.casts;

import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapController;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.OverlayItem;
import com.google.android.maps.Projection;
import com.readystatesoftware.mapviewballoons.BalloonItemizedOverlay;

import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;

public class BrowseByMapActivity extends MapActivity {
	
	//public static String ACTION_VIEW_ON_MAP = 
	MapView myMapView;
	MyLocationOverlay myLoc;
	CursorOverlay overlay;
	
	MapController mapController;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		

        setContentView(R.layout.cast_browse_map);

        myMapView = (MapView) findViewById(R.id.main_map);
        mapController = myMapView.getController();

        final Uri data = getIntent().getData();
        final Drawable marker = getResources().getDrawable(R.drawable.icon_map_marker);

		overlay = new CursorOverlay(marker);
		overlay.addCursor(managedQuery(data, Cast.PROJECTION, null, null, null));
		
		myLoc = new MyLocationOverlay(this, myMapView);
		mapController.setZoom(17); // default zoom
		myLoc.runOnFirstFix(new Runnable() {
			public void run() {
                mapController.animateTo(myLoc.getMyLocation());
			}
		});

        myMapView.getOverlays().add(myLoc);

		myMapView.getOverlays().add(overlay);
		
	}

	@Override
	protected void onResume() {
		myLoc.enableMyLocation();
		super.onResume();
	}
	
	@Override
	protected void onPause() {
		myLoc.disableMyLocation();
		super.onPause();
	}
	
	@Override
	protected boolean isRouteDisplayed() {
		// TODO Auto-generated method stub
		return false;
	}
	
	public class CursorOverlay extends BalloonItemizedOverlay<OverlayItem>{
		int size;
		private Cursor c;

		Paint strokePaint = new Paint();
		Paint textPaint = new Paint();
		
		final Point p = new Point();
		
		final float labelSize = 14;
		public CursorOverlay(Drawable defaultMarker) {
			super(boundCenterBottom(defaultMarker), myMapView);
			
			strokePaint.setARGB(255, 0, 0, 0);
		    strokePaint.setTextAlign(Paint.Align.CENTER);
		    strokePaint.setTextSize(labelSize);
		    strokePaint.setTypeface(Typeface.DEFAULT_BOLD);
		    strokePaint.setStyle(Paint.Style.STROKE);
		    strokePaint.setStrokeWidth(4);
		    strokePaint.setAntiAlias(true);

		    textPaint.setARGB(255, 255, 255, 255);
		    textPaint.setTextAlign(Paint.Align.CENTER);
		    textPaint.setTextSize(labelSize);
		    textPaint.setTypeface(Typeface.DEFAULT_BOLD);
		    textPaint.setAntiAlias(true);

		}

		@Override
		public void draw(Canvas canvas, MapView mapView, boolean shadow) {
			super.draw(canvas, mapView, shadow);
			final Projection proj = mapView.getProjection();
			if (shadow){
				for (int i = 0; i < size; i++){
					final OverlayItem item = getItem(i);
					proj.toPixels(item.getPoint(), p);
					
					canvas.drawText(item.getTitle(), p.x, p.y+labelSize, strokePaint);
					canvas.drawText(item.getTitle(), p.x, p.y+labelSize, textPaint);
				}
			}
		}
		
		@Override
		protected boolean onBalloonTap(int index) {
			
			c.moveToPosition(index);
			final int id = c.getInt(c.getColumnIndex(Cast._ID));
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.withAppendedPath(Cast.CONTENT_URI, String.valueOf(id))));
			return true;
		}

		@Override
		protected OverlayItem createItem(int i) {
			c.moveToPosition(i);
			final int lat = (int)(c.getFloat(c.getColumnIndex(Cast.LATITUDE)) * 1E6);
			final int lon = (int)(c.getFloat(c.getColumnIndex(Cast.LONGITUDE)) * 1E6);
			final GeoPoint geo = new GeoPoint(lat, lon);
			return new OverlayItem(geo, c.getString(c.getColumnIndex(Cast.TITLE)), c.getString(c.getColumnIndex(Cast.DESCRIPTION)));
		}

		@Override
		public int size() {
			size = c.getCount();
			return size;
		}
		
		public void addCursor(Cursor c){
			this.c = c;
			c.registerContentObserver(contentObserver);
			refreshFromCursor();
			setBalloonBottomOffset(35); // based on the icon_map_marker
		}
		
		protected void refreshFromCursor(){
			populate();
		}
		
		private final ContentObserver contentObserver = new ContentObserver(new Handler()){
			@Override
			public void onChange(boolean selfChange) {
				super.onChange(selfChange);
				refreshFromCursor();
			}
		};
	};
	
	
}
