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
import android.content.Context;
import android.util.AttributeSet;
import android.widget.Button;
import edu.mit.mel.locast.mobile.R;

/**
 * @author steve
 *
 */
public class TagButton extends Button {
	private boolean added;
	private boolean editable = false;

	public TagButton(Context context) {
		this(context, null, false, true);
	}


    public TagButton(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        updateResource();
    }

    public TagButton(Context context, AttributeSet attrs) {
        super(context, attrs);
        updateResource();
    }


	public TagButton(Context context, String text, boolean added, boolean editable){
		super(context);
		this.setText(text);
		this.setTag(text);

		this.editable = editable;
		this.added = added;

		updateResource();
	}

	/**
	 * Sets the state of the button to reflect whether or not the tag
	 * has been added to the set of tags on an item.
	 *
	 * @param added
	 */
	public void setAdded(boolean added){

		this.added = added;
		updateResource();
	}

	private void updateResource(){
		if (isEditable()){
			if (isAdded()){
				this.setBackgroundResource(R.drawable.btn_tag_remove);
			}else{
				this.setBackgroundResource(R.drawable.btn_tag_add);
			}
		}else{
			this.setBackgroundResource(R.drawable.btn_tag_normal);
		}
	}

	public boolean isAdded(){
		return added;
	}

	public boolean isEditable() {
		return editable;
	}

	public void setTagName(String name){
		setText(name);
		setTag(name);
	}
}
