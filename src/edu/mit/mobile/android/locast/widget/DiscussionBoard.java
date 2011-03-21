package edu.mit.mobile.android.locast.widget;
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
import java.sql.Date;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.mit.mobile.android.locast.Application;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.WebImageLoader;
import edu.mit.mobile.android.locast.data.Comment;
import edu.mit.mobile.android.locast.data.MediaProvider;

public class DiscussionBoard extends ListView implements OnClickListener, OnEditorActionListener {
	public static final String TAG = DiscussionBoard.class.getSimpleName();
	private final Context mContext;

	private final EditText postingTextField;

	private final Button addPostingButton;

    private Uri thisThread;
    private Cursor c;

    private WebImageLoader imageLoader;

	public DiscussionBoard(Context context) {
		this(context, null);
	}

	public DiscussionBoard(Context context, AttributeSet attrs) {
		super(context, attrs);

		mContext = context;

		addHeaderView(LayoutInflater.from(context).inflate(R.layout.discussionboard, this, false));

        postingTextField = (EditText) findViewById(R.id.discussionText);
        postingTextField.setOnEditorActionListener(this);

        //Initialize Button
        addPostingButton = (Button) findViewById(R.id.send);
        addPostingButton.setOnClickListener(this);

        if (context instanceof Activity){
        	imageLoader = ((Application)context.getApplicationContext()).getImageLoader();
        }
	}

	private static final String[] ADAPTER_FROM = {Comment._AUTHOR, Comment._AUTHOR_ICON,  Comment._DESCRIPTION, Comment._MODIFIED_DATE, Comment._COMMENT_NUMBER};
	private static final int[] ADAPTER_TO      = {R.id.username,   R.id.userthumb,        R.id.text,            R.id.date,              R.id.commentnumber};

	public class DiscussionBoardAdapter extends SimpleCursorAdapter {
		public DiscussionBoardAdapter(Context context, Cursor c) {
			super(context, R.layout.discussionboardentry, c, ADAPTER_FROM, ADAPTER_TO);
		}

		@Override
		public void setViewImage(ImageView v, String value) {
			if (value != null && value.length() > 0){
				imageLoader.loadImage(v, value);
			}
		}

		@Override
		public void setViewText(TextView v, String text) {
			switch (v.getId()){
			case R.id.date:
				final Date d = new Date(Long.parseLong(text));
				v.setText(d.toLocaleString());
				break;

				default:
					super.setViewText(v, text);
			}

		}
	}

    private void savePosting() {
    	//save comment
    	final String text = postingTextField.getText().toString().trim();

    	if (text.length() == 0) {
			return;
		}


    	final ContentResolver cr = mContext.getContentResolver();
    	final ContentValues cv = new ContentValues();
		cv.put(Comment._DESCRIPTION, text);
		cr.insert(thisThread, cv);

		postingTextField.setText("");
    }



    public void setParentUri(Uri parent){
    	setUri(Uri.withAppendedPath(parent, Comment.PATH));
    }

    public void setUri(Uri myUri){
    	Log.d(TAG, "Loading comments for "+myUri);
    	thisThread = myUri;
       	c = getContext().getContentResolver().query(thisThread,
				Comment.PROJECTION, null, null, Comment.DEFAULT_SORT_BY);

       	for (c.moveToFirst(); !c.isAfterLast(); c.moveToNext()){
       		MediaProvider.dumpCursorToLog(c, Comment.PROJECTION);
       	}

    	getContext().startService(new Intent(Intent.ACTION_SYNC, thisThread));

    	c.registerContentObserver(new ContentObserver(new Handler()) {
    		@Override
    		public void onChange(boolean selfChange) {
    			c.requery();
    		}
		});
    	setAdapter(new DiscussionBoardAdapter(getContext(), c));
    }

    @Override
    protected void onDetachedFromWindow() {
    	c.close();
    	super.onDetachedFromWindow();
    }

	public void onClick(View v) {
		switch (v.getId()){
		case R.id.send:
			savePosting();
			break;
		}

	}

	public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
		switch (v.getId()){
		case R.id.discussionText:
			savePosting();
			return true;
		}
		return false;
	}
}
