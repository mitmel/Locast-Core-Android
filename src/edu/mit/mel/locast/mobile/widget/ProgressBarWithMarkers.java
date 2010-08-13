package edu.mit.mel.locast.mobile.widget;
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
import java.util.ArrayList;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.Paint.Align;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ProgressBar;
import edu.mit.mel.locast.mobile.R;

public class ProgressBarWithMarkers extends ProgressBar {
	private final ArrayList<Integer> markers = new ArrayList<Integer>();
	private final Drawable marker;
	private static final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private static final Paint circleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	static {
		circlePaint.setColor(Color.RED);

		circleTextPaint.setColor(Color.BLACK);
		circleTextPaint.setTextAlign(Align.CENTER);
		circleTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
	}


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
		final int width = canvas.getWidth();

		final int max = getMax();

		final int myHeight = getHeight();
		final float textHeight = myHeight * 0.9f;
		circleTextPaint.setTextSize(textHeight);

		final float circleR = myHeight / 2;

		for (int i = 0; i < markers.size(); i++){
			final int markerPos = markers.get(i);
			float left = (markerPos) * ((float)width / max);

			// ensure that the whole circle can be seen on the screen.
			if (left - circleR < 0){
				left = circleR;
			}else if(left + circleR > width){
				left = width - circleR;
			}
			canvas.drawCircle(left, circleR, circleR, circlePaint);

			final String t = Integer.toString(i+1);
			// TODO figure out what the "- 2" is below.
			canvas.drawText(t, left, myHeight / 2 + textHeight / 2 - 2, circleTextPaint);
		}

	}

	public void addMarker(int marker){
		markers.add(marker);
	}

}
