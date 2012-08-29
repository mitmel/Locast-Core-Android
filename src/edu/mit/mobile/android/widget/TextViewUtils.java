package edu.mit.mobile.android.widget;

import android.app.Activity;
import android.widget.TextView;

public class TextViewUtils {
    public static void makeUppercase(Activity activity, int... ids) {
        TextView tv;
        for (final int id : ids) {
            tv = (TextView) activity.findViewById(id);
            tv.setText(tv.getText().toString().toUpperCase());
        }
    }
}
