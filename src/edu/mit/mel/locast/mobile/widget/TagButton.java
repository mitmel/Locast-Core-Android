package edu.mit.mel.locast.mobile.widget;

import android.content.Context;
import android.widget.Button;
import edu.mit.mel.locast.mobile.R;

/**
 * @author steve
 *
 */
public class TagButton extends Button {
	private boolean added;
	private final boolean editable;
	
	public TagButton(Context context) {
		this(context, null, false, true);
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
