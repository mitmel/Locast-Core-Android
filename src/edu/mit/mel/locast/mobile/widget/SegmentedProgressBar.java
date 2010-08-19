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
import android.util.AttributeSet;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

/**
 * A linear horizontal progress bar consisting of multiple numbered segments.
 * Each segment has its own progress bar with independent primary and secondary
 * progress setting. The segments scale so that they have equal-sized increments
 * when taken as a whole. Eg. a SegmentedProgressBar with two segments, one max
 * 5 and one max 10 will have a combined maximum of 15. The first one will be
 * 1/3 the total widget size.
 *
 * Note, currently displayed segment numbering starts at 1, but segment indexes
 * start at 0.
 *
 * @author Steve Pomeroy
 */
public class SegmentedProgressBar extends LinearLayout {
	private final ArrayList<Integer> segments = new ArrayList<Integer>();
	private static final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	private static final Paint circleTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
	static {
		circlePaint.setColor(Color.RED);

		circleTextPaint.setColor(Color.BLACK);
		circleTextPaint.setTextAlign(Align.CENTER);
		circleTextPaint.setTypeface(Typeface.DEFAULT_BOLD);
	}

	public SegmentedProgressBar(Context context) {
		this(context, null);
	}

	public SegmentedProgressBar(Context context, AttributeSet attrs) {
		super(context, attrs);
		setOrientation(HORIZONTAL);
		setWillNotDraw(false);
	}

	private boolean maxInvalid = true;
	private int mMax = 0;

	/**
	 * Add a progress bar segment.
	 * @param segmentMax
	 */
	public void addSegment(int segmentMax){
		segments.add(segmentMax);
		final ProgressBar pb = new ProgressBar(getContext(), null, android.R.attr.progressBarStyleHorizontal);
		pb.setIndeterminate(false);
		this.addView(pb, new LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT));
		setMax(getChildCount() - 1, segmentMax);

	}

	/**
	 * @return The max of all the segments combined.
	 */
	public int getMax(){
		if (maxInvalid){
			mMax = 0;
			for (int i = 0; i < getChildCount(); i++){
				final ProgressBar bar = (ProgressBar) getChildAt(i);
				mMax += bar.getMax();
			}
			maxInvalid = false;
		}
		return mMax;
	}

	/**
	 * @param segment segment index.
	 * @return the maximum of the given segment
	 */
	public int getMax(int segment){
		final ProgressBar bar = (ProgressBar) getChildAt(segment);
		return bar.getMax();
	}

	/**
	 * @param segment segment index.
	 * @param max
	 */
	public void setMax(int segment, int max){
		final ProgressBar bar = (ProgressBar) getChildAt(segment);
		bar.setMax(max);
		maxInvalid = true;
		updateProgressBarLayouts();
	}

	/**
	 * @param segment segment index
	 * @param progress
	 */
	public void setProgress(int segment, int progress){
		final ProgressBar bar = (ProgressBar) getChildAt(segment);
		bar.setProgress(progress);
	}

	/**
	 * @param segment segment index
	 * @param secondaryProgress
	 */
	public void setSecondaryProgress(int segment, int secondaryProgress){
		final ProgressBar bar = (ProgressBar) getChildAt(segment);
		bar.setSecondaryProgress(secondaryProgress);
	}

	private void updateProgressBarLayouts(){
		final int max = getMax();

		for (int i = 0; i < getChildCount(); i++){
			final ProgressBar bar = (ProgressBar) getChildAt(i);
			final float weight = 1 - (bar.getMax() / (float)max);
			((LinearLayout.LayoutParams)bar.getLayoutParams()).weight = weight;
		}
		postInvalidate();
	}

	private void drawSegmentMarkers(Canvas canvas){
		final int width = getWidth();

		final int max = getMax();

		final int myHeight = getHeight();
		final float textHeight = myHeight * 0.9f;
		circleTextPaint.setTextSize(textHeight);

		final float circleR = myHeight / 2;
		int segmentSum = 0;
		for (int i = 0; i < segments.size(); i++){
			float left = (segmentSum) * ((float)width / max);
			segmentSum += segments.get(i);
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

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);
		drawSegmentMarkers(canvas);
	}
}
