package edu.mit.mobile.android.locast.sync;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationCompat.Builder;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.net.NetworkClient.FileTransferProgressListener;

/**
 * A progress listener that displays progress using a system notification. Uploads notifications are
 * left, so the user knows what's been uploaded.
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 *
 */
public class NotificationProgressListener implements FileTransferProgressListener {

    public static final int TYPE_UPLOAD = 100;
    public static final int TYPE_DOWNLOAD = 101;

    // //////////////////////////////

    private static final int MAX_SEGMENT_PROGRESS = 1000;

    private final Builder mNotificationBuilder;
    private int mType = TYPE_DOWNLOAD;

    private int mCompletionCount = 0;
    private int mTotal = 1;

    private int mFileProgress = 0;
    private final NotificationManager mNotificationManager;
    private final Resources mResources;
    private Context mContext;
    private boolean mShown;
    private final int mId;
    private Uri mLastContentItem;
    private CharSequence mLastTitle;
    private boolean mSuccessful = false;

    // ///////////////////////////////

    /**
     * @param context
     * @param type
     *            Either {@link #TYPE_DOWNLOAD} or {@link #TYPE_UPLOAD}
     * @param id
     *            the notification ID to be used.
     */
    public NotificationProgressListener(Context context, int type, int id) {
        mContext = context;
        mId = id;
        mType = type;

        mNotificationBuilder = new NotificationCompat.Builder(context)

        .setOngoing(true)

        .setOnlyAlertOnce(true)

        .setProgress(MAX_SEGMENT_PROGRESS, 0, false);

        mNotificationManager = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);

        mShown = false;

        mResources = context.getResources();
    }

    /**
     * Sets the total number of items expected to be transferred. Defaults to 1.
     *
     * @param totalItems
     */
    public void setTotalItems(int totalItems) {
        mTotal = totalItems;
    }

    @Override
    public void onTransferStart(Uri contentItem, CharSequence title, String contentType,
            long totalBytes) {
        mNotificationBuilder.setSmallIcon(getIcon(false)).setWhen(System.currentTimeMillis());

        if (title == null) {
            if (mTotal > 1) {
                title = (mCompletionCount + 1) + "/" + mTotal;
            } else {
                title = "";
            }
        }
        mLastTitle = title;

        mNotificationBuilder.setContentTitle(getTitle(title));
        mNotificationBuilder.setTicker(getTitle(title));
        mNotificationBuilder.setContentIntent(getPendingContentIntent(contentItem));

        mShown = true;
        mNotificationManager.notify(mId, mNotificationBuilder.build());
    }

    private int getIcon(boolean complete) {
        if (complete && !mSuccessful) {
            return android.R.drawable.stat_sys_warning;
        }

        switch (mType) {
            case TYPE_UPLOAD:
                return complete ? android.R.drawable.stat_sys_upload_done
                        : android.R.drawable.stat_sys_upload;
            default:
            case TYPE_DOWNLOAD:
                return complete ? android.R.drawable.stat_sys_download_done
                        : android.R.drawable.stat_sys_download;
        }
    }

    private int getProgress() {
        return mCompletionCount * MAX_SEGMENT_PROGRESS + mFileProgress;
    }

    private int getProgressMax() {
        return mTotal * MAX_SEGMENT_PROGRESS;
    }

    private CharSequence getTitle(CharSequence what) {
        return mResources.getString(mType == TYPE_UPLOAD ? R.string.sync_uploading
                : R.string.sync_downloading, what);
    }

    @Override
    public void onTransferProgress(Uri contentItem, long transferredBytes, long totalBytes) {

        boolean indeterminate = false;
        // total bytes should never be 0, but if it is, don't attempt to divide by it.
        if (totalBytes > 0) {
            mFileProgress = (int) ((transferredBytes * MAX_SEGMENT_PROGRESS) / totalBytes);
        } else {
            mFileProgress = 0;
            indeterminate = true;
        }

        mNotificationBuilder.setProgress(getProgressMax(), getProgress(), indeterminate);

        mShown = true;
        mNotificationManager.notify(mId, mNotificationBuilder.build());
    }

    @Override
    public void onTransferComplete(Uri contentItem) {
        mFileProgress = 0;
        mCompletionCount++;

        mLastContentItem = contentItem;

        mShown = true;

    }

    /**
     * <p>
     * Signals that all transfers have been completed. For downloads, this clears the notification.
     * For uploads, this leaves a dismissible notification that links to the content that's been
     * uploaded. Once this method has been called, this object should be discarded and no more
     * callbacks should be called.
     * </p>
     */
    public void onAllTransfersComplete() {
        if (mShown) {
            switch (mType) {

                case TYPE_DOWNLOAD:
                    mNotificationManager.cancel(mId);
                    break;

                default:
                case TYPE_UPLOAD:
                    replaceWithCompleteNotification();
                    break;
            }
        }

        mContext = null;
    }

    public void onTransfersSuccessful() {
        if (mShown) {
            mSuccessful = true;
        }
    }

    private void replaceWithCompleteNotification() {
        final Notification n = mNotificationBuilder
                .setOngoing(false)
                .setAutoCancel(true)
                .setWhen(System.currentTimeMillis())
                .setProgress(0, 0, false)

                .setContentIntent(getPendingContentIntent(mLastContentItem))
                .setContentTitle(
                        mContext.getString(mSuccessful ? R.string.sync_upload_success_message
                                : R.string.error_sync_upload_fail_message, mLastTitle))
                .setSmallIcon(getIcon(true))
                .setTicker(
                        mContext.getText(mSuccessful ? R.string.sync_upload_success
                                : R.string.error_sync_upload_fail)).build();

        mNotificationManager.notify(mId, n);

    }

    private PendingIntent getPendingContentIntent(Uri lastContentItem) {
        return PendingIntent.getActivity(mContext, 0, new Intent(Intent.ACTION_VIEW,
                lastContentItem), PendingIntent.FLAG_CANCEL_CURRENT);
    }
}