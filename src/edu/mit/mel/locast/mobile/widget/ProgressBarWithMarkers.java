package edu.mit.mel.locast.mobile.widget;

import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ProgressBar;
import edu.mit.mel.locast.mobile.R;

public class ProgressBarWithMarkers extends ProgressBar {
	private final ArrayList<Integer> markers = new ArrayList<Integer>();
	private final Drawable marker;

	public ProgressBarWithMarkers(Context context) {
		this(context, null);
	}

	public ProgressBarWithMarkers(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public ProgressBarWithMarkers(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);
		marker = getContext().getResources().getDrawable(R.drawable.progress_marker);

	}

	@Override
	protected synchronized void onDraw(Canvas canvas) {
		super.onDraw(canvas);
		final int w = canvas.getWidth();

		final int max = getMax();
		int left;
		final int myHeight = getHeight();
		final int markerWidth = (int) (marker.getIntrinsicWidth() * ((float)myHeight / marker.getIntrinsicHeight()));
		for (final Integer markerPos: markers){
			left = (int)((markerPos) * ((float)w / max));
			marker.setBounds(left - markerWidth / 2, 0, left + markerWidth / 2, myHeight);
			marker.draw(canvas);
		}

	}

	public void addMarker(int marker){
		markers.add(marker);
	}

}
