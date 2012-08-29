package edu.mit.mobile.android.locast;
/*
 * Copyright (C) 2010 MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.widget.DiscussionBoard;

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
