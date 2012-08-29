package edu.mit.mobile.android.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.CompoundButton;

public class CheckableTabIndicator extends CompoundButton {

    public CheckableTabIndicator(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);

    }

    public CheckableTabIndicator(Context context, AttributeSet attrs) {
        super(context, attrs);

    }

    public CheckableTabIndicator(Context context) {
        super(context);

    }

    @Override
    public boolean performClick() {
        toggle(); // hack! this resets the state before the click happens to prevent it from happening.
        return super.performClick();
    }

}
