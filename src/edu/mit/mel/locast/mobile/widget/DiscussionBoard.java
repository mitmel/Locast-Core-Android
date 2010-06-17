package edu.mit.mel.locast.mobile.widget;
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
import java.text.SimpleDateFormat;
import java.util.Date;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Parcelable;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.mit.mel.locast.mobile.Application;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.WebImageLoader;
import edu.mit.mel.locast.mobile.data.Comment;

public class DiscussionBoard extends LinearLayout implements OnClickListener, OnEditorActionListener {
	
	Context mContext;
	
	ListView discussionPostingsList;
	
	private final EditText postingTextField;
	
	private final Button addPostingButton;
    
    LinearLayout boardItems;
    
    Parcelable listState;
    
    private Uri thisThread;
    private Cursor c;
    
    WebImageLoader imageLoader;

	public DiscussionBoard(Context context) {
		this(context, null);
	}

	public DiscussionBoard(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		mContext = context;
		
		inflateLayout(context);
		
		boardItems = (LinearLayout) findViewById(R.id.discussionboard_items);
		
        postingTextField = (EditText) findViewById(R.id.discussionText);
        postingTextField.setOnEditorActionListener(this);
        
        //Initialize Button
        addPostingButton = (Button) findViewById(R.id.send);
        addPostingButton.setOnClickListener(this);
        
        if (context instanceof Activity){
        	imageLoader = ((Application)context.getApplicationContext()).getImageLoader();
        }
	}
	
	protected void inflateLayout(Context context){
		LayoutInflater.from(context).inflate(R.layout.discussionboard, this);
	}
	
	public void fillDiscussionList(Cursor c) {
		boardItems.removeAllViews();
		
		for (c.moveToFirst(); ! c.isAfterLast(); c.moveToNext()) {
			final int numberColumn = c.getColumnIndex(Comment._COMMENT_NUMBER);
			final View v = getCommentView(c.getString(c.getColumnIndex(Comment._AUTHOR)), 
					c.getString(c.getColumnIndex(Comment._AUTHOR_ICON)), 
					c.getLong(c.getColumnIndex(Comment._MODIFIED_DATE)),
					c.getString(numberColumn), 
					c.getString(c.getColumnIndex(Comment._DESCRIPTION)));
			boardItems.addView(v);
		}
	}
	
    private void savePosting() {
    	//save comment
    	final String text = postingTextField.getText().toString();
    	if (text.length() == 0) {
			return;
		}
    	
    	final ContentResolver cr = mContext.getContentResolver();
    	final ContentValues cv = new ContentValues();
		cv.put(Comment._DESCRIPTION, text);
		cr.insert(thisThread, cv);
    	
		postingTextField.setText("");
		
		//getContext().startService(new Intent(Intent.ACTION_SYNC, thisThread));
    }
    
	View getCommentView(String pUsername, String pUrl, long mDate, String pNo, String description){

		final View v = LayoutInflater.from(mContext).inflate(R.layout.discussionboardentry, boardItems, false);
		
		final TextView user = (TextView) v.findViewById(R.id.commentuser);
		user.setText(pUsername);
		
		final TextView date = (TextView) v.findViewById(R.id.commentdate);
		final SimpleDateFormat formatter = new SimpleDateFormat ("yyyy.MM.dd 'at' HH:mm:ss ");
		final Date commentTime = new Date(mDate);
		date.setText(formatter.format(commentTime));
		
		final ImageView userthumb = (ImageView) v.findViewById(R.id.commentuserthumb);
		if (pUrl != null){
			imageLoader.loadImage(userthumb, pUrl);
		}
		
		final TextView commentnumber = (TextView) v.findViewById(R.id.commentnumber);
		if(pNo != null) {
			commentnumber.setText("#" + pNo);
		}
		
		final TextView commentcontent = (TextView) v.findViewById(R.id.commentcontent);
		commentcontent.setText(description);
		
		return v;
		
	}
    
    public void setParentUri(Uri parent){
    	setUri(Uri.withAppendedPath(parent, Comment.PATH));
    }
    
    public void setUri(Uri myUri){
    	thisThread = myUri;
       	c = getContext().getContentResolver().query(thisThread,
				Comment.PROJECTION, null, null, Comment.DEFAULT_SORT_BY);

    	getContext().startService(new Intent(Intent.ACTION_SYNC, thisThread));
    	
    	c.registerContentObserver(new ContentObserver(new Handler()) {
    		@Override
    		public void onChange(boolean selfChange) {
    			c.requery();
    			fillDiscussionList(c);
    		}
		});
    	fillDiscussionList(c);
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
			break;
		}
		return false;
	}
}
