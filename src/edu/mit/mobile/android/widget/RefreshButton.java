package edu.mit.mobile.android.widget;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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

import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.ImageButton;
import edu.mit.mobile.android.MelAndroid;

public class RefreshButton extends ImageButton {

	private  Drawable mRefreshingDrawable;
	private  Drawable mDefaultDrawable;

	public RefreshButton(Context context) {
		super(context);
		init(null);
	}

	public RefreshButton(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(attrs);
	}

	public RefreshButton(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(attrs);
	}

	private void init(AttributeSet attrs){
		if (attrs != null){
			mRefreshingDrawable = getResources().getDrawable(attrs.getAttributeResourceValue(MelAndroid.NS, "refreshingDrawable", 0));
		}
		mDefaultDrawable = getDrawable();
	}

	@Override
	protected void onAttachedToWindow() {
		super.onAttachedToWindow();
		final Drawable d = getDrawable();
		if (d instanceof AnimationDrawable){
			((AnimationDrawable) d).start();
		}
	}

	@Override
	protected void onDetachedFromWindow() {
		super.onDetachedFromWindow();
		final Drawable d = getDrawable();
		if (d instanceof AnimationDrawable){
			((AnimationDrawable) d).stop();
		}
	}

	public void setRefreshing(boolean isRefreshing){
		if (isRefreshing){
			setEnabled(false);
			setImageDrawable(mRefreshingDrawable);
		}else{
			setEnabled(true);
			setImageDrawable(mDefaultDrawable);
		}
	}
}
