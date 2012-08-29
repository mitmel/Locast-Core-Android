package edu.mit.mobile.android.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import edu.mit.mobile.android.locast.ver2.R;

/**
 * Shows either an empty view or a progress bar.
 *
 */
public class NotificationProgressBar extends FrameLayout {
    private int emptyTextId;
    private final ProgressBar mProgressBar;
    private final TextView mTextView;

    public NotificationProgressBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(attrs);

        View.inflate(context, R.layout.notification_progress_bar, this);

        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mTextView = (TextView) findViewById(R.id.emptyTextView);
        if (emptyTextId != 0) {
            mTextView.setText(emptyTextId);
        }
    }

    public NotificationProgressBar(Context context) {
        super(context);

        View.inflate(context, R.layout.notification_progress_bar, this);

        mProgressBar = (ProgressBar) findViewById(R.id.progressBar);
        mTextView = (TextView) findViewById(R.id.emptyTextView);
    }

    private void init(AttributeSet attrs) {
        final TypedArray a = getContext().obtainStyledAttributes(attrs,
                R.styleable.NotificationProgressBar);
        emptyTextId = a.getResourceId(R.styleable.NotificationProgressBar_android_text, 0);
        a.recycle();
    }

    public void showProgressBar(boolean b) {
        mProgressBar.setVisibility((b ? View.VISIBLE : View.GONE));
        mTextView.setVisibility(((!b) ? View.VISIBLE : View.GONE));
        postInvalidate();
    }
}
