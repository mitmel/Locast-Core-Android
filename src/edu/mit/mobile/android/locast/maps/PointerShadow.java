package edu.mit.mobile.android.locast.maps;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import edu.mit.mobile.android.locast.ver2.R;

public class PointerShadow extends FrameLayout {
	private Drawable mShadow, mPointer;
	private int mShadowW, mShadowH, mPointerW, mPointerH;

	private int mOrientation = ORIENTATION_RIGHT;

	// this must match the enum defined in attrs.xml
	public static final int
		ORIENTATION_TOP = 0,
		ORIENTATION_RIGHT = 1
	;

	private int mPosX, mPosY;
	private boolean mPosSet = false;

	private Context mContext;

	public PointerShadow(Context context) {
		super(context);
		init(context, null);
	}

	public PointerShadow(Context context, AttributeSet attrs) {
		super(context, attrs);
		init(context, attrs);
	}

	public PointerShadow(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		init(context, attrs);
	}

	private void init(Context context, AttributeSet attrs){
		setWillNotDraw(false);
		final TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.PointerShadow);

		final int orientation = ta.getInt(R.styleable.PointerShadow_orientation, ORIENTATION_TOP);

		mContext = context;

		ta.recycle();

		setOrientation(orientation);
	}

	public void setOrientation(int orientation){
		final Resources res = mContext.getResources();

		mOrientation = orientation;

		switch (mOrientation){
		case ORIENTATION_RIGHT:
			mShadow = res.getDrawable(
					R.drawable.map_overshadow_horizontal_right);
			mPointer = res.getDrawable(
					R.drawable.map_overshadow_pointer_right);
			break;

		default:
		case ORIENTATION_TOP:
			mShadow = res.getDrawable(
					R.drawable.map_overshadow_horizontal);
			mPointer = res.getDrawable(
					R.drawable.map_overshadow_pointer);
			break;
		}

		mShadowH = mShadow.getIntrinsicHeight();
		mShadowW = mShadow.getIntrinsicWidth();
		mPointerH = mPointer.getIntrinsicHeight();
		mPointerW = mPointer.getIntrinsicWidth();
	}

	public void setOffset(int x, int y){
		mPosX = x;
		mPosY = y;
		mPosSet = true;
		invalidate();
	}

	@Override
	protected void dispatchDraw(Canvas canvas) {
		super.dispatchDraw(canvas);

		final int w = getWidth();
		final int h = getHeight();
		// short-circuit and don't draw the pointer if the point is missing.
		if (!mPosSet) {
			switch(mOrientation){
			case ORIENTATION_TOP:
				mShadow.setBounds(0, 0,
						w, mShadowH);
				break;

			case ORIENTATION_RIGHT:
				mShadow.setBounds(w - mShadowW, 0,
						w, h);
				break;
			}

			mShadow.draw(canvas);
			return;
		}

		switch (mOrientation){
		case ORIENTATION_TOP:{

			final int pos = mPosX;
			final int halfPointer = mPointerW / 2;

			mShadow.setBounds(0, 0,
					pos - halfPointer, mShadowH);
			mShadow.draw(canvas);

			mPointer.setBounds(pos - halfPointer, 0,
					pos + halfPointer, mPointerH);
			mPointer.draw(canvas);

			mShadow.setBounds(pos + halfPointer, 0,
					w, mShadowH);
			mShadow.draw(canvas);
		}break;

		case ORIENTATION_RIGHT:{
			final int pos = mPosY;
			final int halfPointer = mPointerH / 2;
			final int shadowLeft = w - mShadowW;

			mShadow.setBounds(shadowLeft, 0,
					w, pos - halfPointer);
			mShadow.draw(canvas);

			mPointer.setBounds(w - mPointerW, pos - halfPointer,
					w, pos + halfPointer);
			mPointer.draw(canvas);

			mShadow.setBounds(shadowLeft, pos + halfPointer,
					w, h);
			mShadow.draw(canvas);
		}break;
		}
	}

}
