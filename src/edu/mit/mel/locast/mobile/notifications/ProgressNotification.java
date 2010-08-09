package edu.mit.mel.locast.mobile.notifications;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.widget.RemoteViews;
import edu.mit.mel.locast.mobile.R;


/**
 * A notification that shows a progress bar as an ongoing event, optionally leaving
 * a time-stamped notification when the progress is complete.
 *
 * @author steve
 *
 */
public class ProgressNotification extends Notification {
	// public attributes
	public PendingIntent doneIntent;
	public CharSequence doneTitle;
	public CharSequence doneText;
	public int doneIcon = 0;
	public boolean successful = true;

	// static constants
	private static final int NOTIFICATION_BASE = 0x1000;
	public static final int
		TYPE_GENERIC = 0,
		TYPE_UPLOAD = 1,
		TYPE_DOWNLOAD = 2;

	private final boolean mLeaveWhenDone;
	private final NotificationManager nm;
	private final Context mContext;

	public ProgressNotification(Context context, int icon){
		this(context, icon, null, false);
	}

	public ProgressNotification(Context context, int icon, CharSequence tickerText, boolean leaveWhenDone) {
		super(icon, null, System.currentTimeMillis());
		flags |= Notification.FLAG_ONGOING_EVENT;

		contentView = new RemoteViews(context.getPackageName(), R.layout.notification_progress);
		mLeaveWhenDone = leaveWhenDone;
		nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		setType(TYPE_GENERIC);
		setTitle(tickerText);
		mContext = context;
	}

	public void setProgress(int max, int complete){
		contentView.setProgressBar(R.id.progress, max, complete, max == 0);
	}

	public void setTitle(CharSequence title){
		contentView.setTextViewText(R.id.title, title);
	}

	/**
	 * Set the iconography to a given set. Call notify() after this.
	 *
	 * @param type
	 */
	public void setType(int type){
		switch (type){
		case TYPE_UPLOAD:
			//doneIcon = android.R.drawable.stat_sys_upload_done;
			doneIcon = R.drawable.stat_notify_success;
			icon = android.R.drawable.stat_sys_upload;
			break;

		case TYPE_DOWNLOAD:
			doneIcon = android.R.drawable.stat_sys_download_done;
			icon = android.R.drawable.stat_sys_download;
			break;

		}
		contentView.setImageViewResource(R.id.icon, icon);
	}

	/**
	 * Mark the progress done.
	 * @param id an ID that's unique for the set of things being done.
	 */
	public void done(int id){
		if (mLeaveWhenDone){
			int doneIcon;
			if (successful){
				doneIcon = this.doneIcon != 0 ? this.doneIcon : icon;
			}else{
				doneIcon = android.R.drawable.stat_notify_error;
			}
			final Notification doneNotification = new Notification(doneIcon, doneTitle, System.currentTimeMillis());
			doneNotification.flags |= Notification.FLAG_SHOW_LIGHTS | Notification.FLAG_AUTO_CANCEL;
			doneNotification.setLatestEventInfo(mContext, doneTitle, doneText, doneIntent);
			nm.notify(NOTIFICATION_BASE + id, doneNotification);
		}
	}
}
