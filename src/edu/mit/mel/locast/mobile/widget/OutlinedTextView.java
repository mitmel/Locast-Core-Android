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
/*
 * portions Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.BoringLayout;
import android.text.Layout;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.widget.TextView;

/**
 * A modified text view that puts an outline around the text.
 * Doesn't support much other than plain, single-lined colored text.
 *
 * @author Steve Pomeroy
 *
 */
public class OutlinedTextView extends TextView {
	public static String TAG = OutlinedTextView.class.getSimpleName();
	private Layout mLayoutOutline;
	private Layout mLayout;
	private int mGravity = Gravity.LEFT;
	private boolean mIncludePad = true;
	private float mSpacingMult = 1;
	private float mSpacingAdd = 0;

	private TextPaint mTextPaint;
	private TextPaint strokePaint = new TextPaint();

	public OutlinedTextView(Context context) {
		this(context, null);
	}

	public OutlinedTextView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public OutlinedTextView(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

		mGravity = getGravity();
	}

	@Override
	public void setTextAppearance(Context context, int resid) {
		super.setTextAppearance(context, resid);

		setStrokePaint();
	}

	private void setStrokePaint(){
		mTextPaint = getPaint();
		strokePaint = new TextPaint(mTextPaint);
		strokePaint.setTypeface(getTypeface());
		strokePaint.setStyle(Paint.Style.STROKE);

		// TODO make these properties that can be set from XML
		strokePaint.setARGB(255, 0, 0, 0);
		strokePaint.setStrokeWidth(4);
	}

	@Override
	public void setText(CharSequence text, BufferType type) {
		setStrokePaint();

		mGravity = getGravity();

		super.setText(text, type);


        Layout.Alignment alignment;
        switch (mGravity & Gravity.HORIZONTAL_GRAVITY_MASK) {
            case Gravity.CENTER_HORIZONTAL:
                alignment = Layout.Alignment.ALIGN_CENTER;
                break;

            case Gravity.RIGHT:
                alignment = Layout.Alignment.ALIGN_OPPOSITE;
                break;

            default:
                alignment = Layout.Alignment.ALIGN_NORMAL;
        }

        final BoringLayout.Metrics boring = BoringLayout.isBoring(text, mTextPaint);
        final BoringLayout.Metrics boringOutline = BoringLayout.isBoring(text, strokePaint);
        if (boring == null){
        	// we only support boringness here.
        	mLayout = null;
        	mLayoutOutline = null;
        	Log.e(TAG, "Text too complex for view to understand.");
        }else{

        	mLayout = BoringLayout.make(text, mTextPaint, 0, alignment, mSpacingMult, mSpacingAdd, boring, mIncludePad);
        	mLayoutOutline = BoringLayout.make(text, strokePaint, 0, alignment, mSpacingMult, mSpacingAdd, boringOutline, mIncludePad);
        }
	}

	@Override
	public void setIncludeFontPadding(boolean includepad) {
		super.setIncludeFontPadding(includepad);
		mIncludePad = includepad;
	}

	@Override
	public void setLineSpacing(float add, float mult) {
		super.setLineSpacing(add, mult);
		mSpacingAdd = add;
		mSpacingMult = mult;
	}

	@Override
	protected void onDraw(Canvas canvas) {
		// This routine explicitly does not call super.onDraw() as trying to match
		// up the drawn text with the outline is too hard.
		if (mLayoutOutline != null){

			canvas.save();
			mTextPaint.setColor(getCurrentTextColor());
			// basic offsets for padding
			canvas.translate(getCompoundPaddingLeft(), getVerticalOffset() + getExtendedPaddingTop());
			mLayoutOutline.draw(canvas);
			mLayout.draw(canvas);
			canvas.restore();
		}
	}
    private int getVerticalOffset() {
        int voffset = 0;
        final int gravity = mGravity & Gravity.VERTICAL_GRAVITY_MASK;

        final Layout l = mLayoutOutline;

        if (gravity != Gravity.TOP) {
            int boxht;
                boxht = getMeasuredHeight() - getExtendedPaddingTop() -
                        getExtendedPaddingBottom();
            final int textht = l.getHeight();

            if (textht < boxht) {
                if (gravity == Gravity.BOTTOM) {
					voffset = boxht - textht;
				} else {
					voffset = (boxht - textht) >> 1;
				}
            }
        }
        return voffset;
    }

}
