package edu.mit.mobile.android.widget;

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
