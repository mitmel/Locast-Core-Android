package edu.mit.mobile.android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TabWidget;

public class CheckableTabWidget extends TabWidget {
	public CheckableTabWidget(Context context) {
		super(context);
	}

	public CheckableTabWidget(Context context, AttributeSet attrs) {
		super(context, attrs);

	}

	public CheckableTabWidget(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);

	}

	/**
	 * Sets the checked state for the given tab. Tab indicator must be a {@link CheckableTabIndicator}.
	 *
	 * @param index
	 * @param checked
	 */
	public void setTabChecked(int index, boolean checked){
		final View v = getChildTabViewAt(index);
		if (v instanceof CheckableTabIndicator){
			((CheckableTabIndicator) v).setChecked(checked);
		}else{
			throw new RuntimeException("tab indicator at index "+index+" is not an instance of CheckableTabIndicator");
		}
	}
}
