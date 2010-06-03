package org.jsharkey.blog.android;

import java.util.LinkedHashMap;
import java.util.Map;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;

/**
 * Copyright 2009 Jeffrey Sharkey
 *   Permission is granted to copy, distribute and/or modify this document
 *   under the terms of the GNU Free Documentation License, Version 1.3
 *   or any later version published by the Free Software Foundation;
 *   with no Invariant Sections, no Front-Cover Texts, and no Back-Cover Texts.
 *   A copy of the license is included in the section entitled "GNU
 *   Free Documentation License".
 *   
 * @author Jeffrey Sharkey
 * @author Steve Pomeroy
 * @see http://jsharkey.org/blog/2008/08/18/separating-lists-with-headers-in-android-09/
 *
 */
public class SeparatedListAdapter extends BaseAdapter {

	public final Map<String,Adapter> sections = new LinkedHashMap<String,Adapter>();
	public final ArrayAdapter<String> headers;
	public final static int TYPE_SECTION_HEADER = 0;

	public SeparatedListAdapter(Context context, int textViewResourceId) {
		headers = new ArrayAdapter<String>(context, textViewResourceId);
	}

	public void addSection(String section, Adapter adapter) {
		this.headers.add(section);
		this.sections.put(section, adapter);
	}

	public Object getItem(int position) {
		for(final Object section : this.sections.keySet()) {
			final Adapter adapter = sections.get(section);
			final int size = adapter.getCount() + 1;

			// check if position inside this section
			if(position == 0) {
				return section;
			}
			if(position < size) {
				return adapter.getItem(position - 1);
			}

			// otherwise jump into next section
			position -= size;
		}
		return null;
	}

	public int getCount() {
		// total together all sections, plus one for each section header
		int total = 0;
		for(final Adapter adapter : this.sections.values()) {
			total += adapter.getCount() + 1;
		}
		return total;
	}

	@Override
	public int getViewTypeCount() {
		// assume that headers count as one, then total all sections
		int total = 1;
		for(final Adapter adapter : this.sections.values()) {
			total += adapter.getViewTypeCount();
		}
		return total;
	}

	@Override
	public int getItemViewType(int position) {
		int type = 1;
		for(final Object section : this.sections.keySet()) {
			final Adapter adapter = sections.get(section);
			final int size = adapter.getCount() + 1;

			// check if position inside this section
			if(position == 0) {
				return TYPE_SECTION_HEADER;
			}
			if(position < size) {
				return type + adapter.getItemViewType(position - 1);
			}

			// otherwise jump into next section
			position -= size;
			type += adapter.getViewTypeCount();
		}
		return -1;
	}

	public boolean areAllItemsSelectable() {
		return false;
	}

	@Override
	public boolean isEnabled(int position) {
		return (getItemViewType(position) != TYPE_SECTION_HEADER);
	}

	// TODO optimize by storing sub-adapter sizes and registering dataset listeners.
	public View getView(int position, View convertView, ViewGroup parent) {
		int sectionnum = 0;
		for(final Object section : this.sections.keySet()) {
			final Adapter adapter = sections.get(section);
			final int size = adapter.getCount() + 1;

			// check if position inside this section
			if(position == 0) {
				return headers.getView(sectionnum, convertView, parent);
			}
			if(position < size) {
				return adapter.getView(position - 1, convertView, parent);
			}

			// otherwise jump into next section
			position -= size;
			sectionnum++;
		}
		return null;
	}

	public long getItemId(int position) {
		for(final Object section : this.sections.keySet()) {
			final Adapter adapter = sections.get(section);
			final int size = adapter.getCount() + 1;

			// check if position inside this section
			if(position == 0) {
				return -1;
			}
			if(position < size) {
				return adapter.getItemId(position - 1);
			}

			// otherwise jump into next section
			position -= size;
		}
		return -1;
	}
	
	@Override
	public void registerDataSetObserver(DataSetObserver observer) {
		super.registerDataSetObserver(observer);
		for ( final Adapter adapter: sections.values()){
			adapter.registerDataSetObserver(observer);
		}
	}
	
	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		super.unregisterDataSetObserver(observer);
		for ( final Adapter adapter: sections.values()){
			adapter.unregisterDataSetObserver(observer);
		}
	}
}

