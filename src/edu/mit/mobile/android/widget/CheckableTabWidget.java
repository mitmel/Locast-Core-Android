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

	public static final int ALL_TABS_CHECKED = -1;

	/**
	 * Gets the index of the next unchecked tab
	 *
	 * @return the index of the unchecked tab or {@link #ALL_TABS_CHECKED} if all tabs are checked.
	 */
	public int getNextUncheckedTab(){
		int unchecked = ALL_TABS_CHECKED;
		final int count = getChildCount();
		for (int i = 0; unchecked == ALL_TABS_CHECKED && i < count; i++){
			final View v = getChildTabViewAt(i);
			if (v instanceof CheckableTabIndicator){
				if (! ((CheckableTabIndicator) v).isChecked()){
					unchecked = i;
				}
			}
		}

		return unchecked;
	}
}
