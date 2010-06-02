package edu.mit.mel.locast.mobile;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import edu.mit.mel.locast.mobile.widget.DiscussionBoard;

public class DiscussionActivity extends Activity {
	DiscussionBoard discussionBoard;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.discussion_activity_main);
		
		discussionBoard = (DiscussionBoard)findViewById(R.id.discussion);
		final Intent i = getIntent();
		final String action = i.getAction();
		if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_EDIT.equals(action)){
			discussionBoard.setUri(i.getData());		
		}
	}
}
