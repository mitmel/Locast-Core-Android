package edu.mit.mel.locast.mobile.net;

import android.app.NotificationManager;
import edu.mit.mel.locast.mobile.net.NetworkClient.TransferProgressListener;
import edu.mit.mel.locast.mobile.notifications.ProgressNotification;

/**
 * A progress notification that shows the progress of a network transfer. Make sure to call done() when finished.
 *
 * @author steve
 *
 */
public class NotificationProgressListener implements TransferProgressListener {

	final static int NOTIFICATION_TRANSFER = 0x2000;

	private final NotificationManager nm;
	private final ProgressNotification notification;
	private long size;
	private final int notificationId;

	/**
	 * @param nm
	 * @param notification You are responsible with creating your notification and handling errors. This just updates progress and calls done().
	 * @param size The total size of the transfer.
	 * @param id A unique ID for this particular object being transferred.
	 */
	public NotificationProgressListener(NotificationManager nm, ProgressNotification notification, long size, int id) {
		this.notification = notification;
		this.nm = nm;
		this.size = size;
		notificationId = NOTIFICATION_TRANSFER + id;
	}

	public void setSize(long size){
		this.size = size;
	}

	public void publish(long bytes) {
		final int completed = (int)((bytes * 1000) / size);
		notification.setProgress(1000, completed);
		nm.notify(notificationId, notification);
	}

	/**
	 * Show a notification that the transfer was complete.
	 */
	public void done(){
		nm.cancel(notificationId);
		notification.done(notificationId);
	}
}