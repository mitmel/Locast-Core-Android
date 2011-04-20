package edu.mit.mobile.android.locast.itineraries;

import java.util.LinkedList;
import java.util.List;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathEffect;
import android.graphics.Point;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapView;
import com.google.android.maps.Overlay;
import com.google.android.maps.Projection;

/**
 * Shows a single path, with an optional outline.
 *
 * @author steve
 *
 */
public class PathOverlay extends Overlay {
	@SuppressWarnings("unused")
	private static final String TAG = PathOverlay.class.getSimpleName();
	private final Context mContext;
	private final Paint mPathPaint;
	private final Paint mPathPaintOutline;
	private PathEffect mPathEffect;
	private List<GeoPoint> mPathGeo = new LinkedList<GeoPoint>();
	private Path mPath;
	private final boolean animate = true;

	private boolean mShowOutline = true;


	public PathOverlay(Context context){
		this(context, null);
	}

	public PathOverlay(Context context, Paint pathPaint) {
		this.mContext = context;
		if (pathPaint != null){
			mPathPaint = pathPaint;
		}else{
			mPathPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
			mPathPaint.setColor(Color.rgb(0x2c, 0xb2, 0xb2));
			mPathPaint.setStyle(Paint.Style.STROKE);

			mPathEffect = new DashPathEffect (new float[]{20, 20}, 0);
			mPathPaint.setPathEffect(mPathEffect);
			mPathPaint.setStrokeWidth(5);
			mPathPaint.setStrokeJoin(Paint.Join.ROUND);
			mPathPaint.setStrokeCap(Paint.Cap.ROUND);
		}

		mPathPaintOutline = new Paint(Paint.ANTI_ALIAS_FLAG);
		mPathPaintOutline.setColor(Color.argb(192, 255, 255, 255));
		mPathPaintOutline.setStyle(Paint.Style.STROKE);

		mPathPaintOutline.setStrokeWidth(mPathPaint.getStrokeWidth() * 3);
		mPathPaintOutline.setStrokeJoin(Paint.Join.ROUND);
		mPathPaintOutline.setStrokeCap(Paint.Cap.ROUND);
	}

	/**
	 * Add a point to the path.
	 *
	 * @param latitudeE6
	 * @param longitudeE6
	 */
	public void addPoint(int latitudeE6, int longitudeE6){
		mPathGeo.add(new GeoPoint(latitudeE6, longitudeE6));
	}

	/**
	 * Add a point to the path.
	 *
	 * @param point
	 */
	public void addPoint(GeoPoint point){
		mPathGeo.add(point);
	}

	/**
	 * Clear the path.
	 */
	public void clearPath(){
		mPathGeo.clear();
	}

	public void setPath(List<GeoPoint> path){
		mPathGeo = path;
	}

	/**
	 * Adds a white/translucent outline around your path to improve visibility.
	 *
	 * @param show show the outline
	 */
	public void setShowOutline(boolean show){
		mShowOutline = show;
	}

	private void updatePath(Projection projection){
		mPath = new Path();
		final Point p = new Point();

		boolean first = true;
		for (final GeoPoint gp : mPathGeo){
			projection.toPixels(gp, p);
			if (first){
				mPath.moveTo(p.x, p.y);
				first = false;
			}else{

				mPath.lineTo(p.x, p.y);
			}
		}
	}

	@Override
	public void draw(Canvas canvas, MapView mapView, boolean shadow) {
		if (!shadow){
			if (animate){
				mPathEffect = new DashPathEffect (new float[]{20, 20}, ((System.currentTimeMillis() / 10) % 400)/10.0f);
				mPathPaint.setPathEffect(mPathEffect);
			}
			updatePath(mapView.getProjection());
			if (mShowOutline){
				canvas.drawPath(mPath, mPathPaintOutline);
			}
			canvas.drawPath(mPath, mPathPaint);
			if (animate) {
				mapView.postInvalidateDelayed(50);
			}
		}

	};
}