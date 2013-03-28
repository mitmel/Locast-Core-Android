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
import edu.mit.mobile.android.locast.R;

public class TagListView extends LinearLayout {

    private final ViewGroup addedTagView;
    private final List<String> addedTags = new Vector<String>();
    private OnClickListener tagHandler;
    private final TextView noTagNotice;

    public TagListView(Context context) {
        super(context);

        inflateLayout(context, null);

        noTagNotice = (TextView) findViewById(R.id.tag_no_tags);
        addedTagView = (ViewGroup) findViewById(R.id.tag_added_tags);
    }

    public TagListView(Context context, AttributeSet attrs) {
        super(context, attrs);

        inflateLayout(context, attrs);

        noTagNotice = (TextView) findViewById(R.id.tag_no_tags);
        addedTagView = (ViewGroup) findViewById(R.id.tag_added_tags);
    }

    protected void inflateLayout(Context context, AttributeSet attrs) {
        LayoutInflater.from(context).inflate(R.layout.taglist_view, this);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        addedTagView.setEnabled(enabled);
    }

    /**
     * Gets the set of tags associated with the view.
     *
     * @return
     */
    public List<String> getTags() {
        return addedTags;
    }

    public void clearAllTags() {
        addedTags.clear();
        addedTagView.removeAllViews();
        noTagNotice.setVisibility(TextView.VISIBLE);
    }

    /**
     * Adds a tag to the list of tags for this view.
     *
     * @param tag
     *            A short tag, preferably one word. Can contain whitespace.
     * @throws IllegalArgumentException
     *             if provided an empty tag.
     * @return true if the tag was successfully added. Returns false if the tag is already present.
     */
    public boolean addTag(String tag) {
        boolean added = false;
        if (tag.length() == 0) {
            throw new IllegalArgumentException("cannot add empty tag");
        }
        if (!addedTags.contains(tag)) {
            addedTags.add(tag);
            added = true;

            Collections.sort(addedTags);
            if (noTagNotice.getVisibility() != TextView.GONE) {
                noTagNotice.setVisibility(TextView.GONE);
            }
            addedTagView.addView(getTagView(tag, true), addedTags.indexOf(tag));
        }

        return added;
    }

    public void addTags(Collection<String> tags) {
        for (final String tag : tags) {
            addTag(tag);
        }
    }

    /**
     * Removes a tag from the view. If it's recommended, it'll remain in the recommendations.
     *
     * @param tag
     *            A short tag, preferably one word. Can contain whitespace.
     * @throws IllegalArgumentException
     *             if provided an empty tag.
     * @return true if the tag was successfully removed; false if the tag has already been removed.
     */
    public boolean removeTag(String tag) {
        boolean removed = false;
        if (tag.length() == 0) {
            throw new IllegalArgumentException("cannot add empty tag");
        }

        if (addedTags.contains(tag)) {
            addedTagView.removeViewAt(addedTags.indexOf(tag));
            addedTags.remove(tag);
            if (addedTags.size() == 0) {
                if (noTagNotice.getVisibility() != TextView.VISIBLE) {
                    noTagNotice.setVisibility(TextView.VISIBLE);
                }
            }
            removed = true;
        }
        return removed;
    }

    TagButton getTagView(String tag, boolean added) {
        final TagButton b = (TagButton) LayoutInflater.from(getContext()).inflate(
                R.layout.tagbutton, null);

        b.setAdded(added);
        b.setTagName(tag);
        b.setOnClickListener(tagHandler);
        return b;
    }

    public void setOnTagClickListener(OnClickListener tagHandler) {
        this.tagHandler = tagHandler;
    }
}
