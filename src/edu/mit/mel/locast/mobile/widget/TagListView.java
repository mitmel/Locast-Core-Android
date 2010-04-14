package edu.mit.mel.locast.mobile.widget;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import edu.mit.mel.locast.mobile.R;

public class TagListView extends LinearLayout {

	private final ViewGroup addedTagView;
	private final List<String> addedTags = new Vector<String>();
	private OnClickListener tagHandler;
	private final TextView noTagNotice;

	public TagListView(Context context) {
		this(context, null);
	}

	public void clearAllTags(){
		addedTags.clear();
		addedTagView.removeAllViews();
		noTagNotice.setVisibility(TextView.VISIBLE);
	}
	
	public TagListView(Context context, AttributeSet attrs) {
		super(context, attrs);
		
		inflateLayout(context, attrs);
		
		noTagNotice = (TextView)findViewById(R.id.tag_no_tags);
		addedTagView = (ViewGroup)findViewById(R.id.tag_added_tags);
	}

	protected void inflateLayout(Context context, AttributeSet attrs){
		LayoutInflater.from(context).inflate(R.layout.taglist_view, this);
	}
	/**
	 * Gets the set of tags associated with the view.
	 * 
	 * @return
	 */
	public List<String> getTags() {
		return addedTags;
	}

	/**
	 * Adds a tag to the list of tags for this view.
	 * 
	 * @param tag
	 */
	public boolean addTag(String tag) {
		boolean added = false;
		if (tag.length() == 0){
			throw new IllegalArgumentException("cannot add empty tag");
		}
		if (! addedTags.contains(tag)){
			addedTags.add(tag);
			added = true;

			Collections.sort(addedTags);
			if (noTagNotice.getVisibility() != TextView.GONE){
				noTagNotice.setVisibility(TextView.GONE);
			}
			addedTagView.addView(getTagView(tag, true), addedTags.indexOf(tag));
		}
		
		return added;
	}

	public void addTags(Collection<String> tags) {
		for (final String tag: tags){
			addTag(tag);
		}
	}

	/**
	 * Removes a tag from the view. If it's recommended, it'll remain in the recommendations.
	 * 
	 * @param tag
	 */
	public boolean removeTag(String tag) {
		boolean removed = false;
		
		if (addedTags.contains(tag)){
			addedTagView.removeViewAt(addedTags.indexOf(tag));
			addedTags.remove(tag);
			if (addedTags.size() == 0){
				if (noTagNotice.getVisibility() != TextView.VISIBLE){
					noTagNotice.setVisibility(TextView.VISIBLE);
				}
			}
			removed = true;
		}
		return removed;
	}

	TagButton getTagView(String tag, boolean added){
		final TagButton b = new TagButton(getContext(), tag, added, false);
		b.setOnClickListener(tagHandler);
		return b;
	}
	
	public void setOnTagClickListener(OnClickListener tagHandler){
		this.tagHandler = tagHandler;
	}
}