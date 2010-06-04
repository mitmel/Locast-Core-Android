package org.jsharkey.blog.android;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import android.content.Context;
import android.database.DataSetObserver;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.SectionIndexer;

/**
 * Copyright 2009 Jeffrey Sharkey, 2010 Steve Pomeroy
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
public class SeparatedListAdapter extends BaseAdapter implements SectionIndexer {

	private final Map<String, SectionHeader> headerMap = new LinkedHashMap<String, SectionHeader>();
	private final ArrayAdapter<SectionHeader> headersAdapter;
	private final ArrayList<SectionHeader> sections = new ArrayList<SectionHeader>();
	
	public final static int TYPE_SECTION_HEADER = 0;

	public SeparatedListAdapter(Context context, int textViewResourceId) {
		headersAdapter = new ArrayAdapter<SectionHeader>(context, textViewResourceId, sections);
	}

	public void addSection(String tag, Adapter adapter) {
		addSection(tag, tag, adapter);
	}
	
	public void addSection(String tag, String title, Adapter adapter) {
		final SectionHeader header = new SectionHeader(tag, title, adapter);
		adapter.registerDataSetObserver(new SectionDataSetObserver(header));
		this.headersAdapter.add(header);
		this.headerMap.put(tag, header);
		
	}
	
	public void setSectionTitle(String tag, String title){
		headerMap.get(tag).title = title;
	}

	public Object getItem(int position) {
		for(final SectionHeader section: this.sections) {
			final Adapter adapter = section.adapter;
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
		for(final SectionHeader section: this.sections) {
			final Adapter adapter = section.adapter;
			total += adapter.getCount() + 1;
		}
		return total;
	}

	@Override
	public int getViewTypeCount() {
		// assume that headers count as one, then total all sections
		int total = 1;
		for(final SectionHeader section: this.sections) {
			final Adapter adapter = section.adapter;
			total += adapter.getViewTypeCount();
		}
		return total;
	}

	@Override
	public int getItemViewType(int position) {
		int type = 1;
		for(final SectionHeader section: this.sections) {
			final Adapter adapter = section.adapter;
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

	public View getView(int position, View convertView, ViewGroup parent) {
		int sectionnum = 0;
		for(final SectionHeader section: this.sections) {
			final Adapter adapter = section.adapter;
			final int size = adapter.getCount() + 1;

			// check if position inside this section
			if(position == 0) {
				return headersAdapter.getView(sectionnum, convertView, parent);
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
		for(final SectionHeader section : this.sections) {
			final Adapter adapter = section.adapter;
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
		for ( final SectionHeader header: sections){
			header.adapter.registerDataSetObserver(observer);
		}
	}
	
	@Override
	public void unregisterDataSetObserver(DataSetObserver observer) {
		super.unregisterDataSetObserver(observer);
		for ( final SectionHeader header: sections){
			header.adapter.unregisterDataSetObserver(observer);
		}
	}
	
	private class SectionHeader {
		public final String tag;
		public final Adapter adapter;
		public String title;
		public int sectionCount;
		
		public SectionHeader(String tag, String title, Adapter adapter) {
			this.tag = tag; 
			this.title = title;
			this.adapter = adapter;
			this.sectionCount = adapter.getCount();
		}
		
		@Override
		public String toString() {
			return title;
		}
	}

	public int getPositionForSection(int section) {
		int position = -1; // offset because we want to return the index of the section header.
		for (int i = 0; i < section; i++){
			position += sections.get(i).sectionCount + 1;
		}
		Log.d("foo", "section "+section+"; position: "+position);
		return position;
	}

	public int getSectionForPosition(int position) {
		int i = 0;
		for(final SectionHeader section : this.sections) {
			final int size = section.sectionCount;

			// check if position inside this section
			if(position == 0) {
				return 0;
			}
			if(position < size) {
				Log.d("foo", "section "+i+"; position: "+position);
				return i;
			}

			// otherwise jump into next section
			position -= size;
			i++;
		}
		
		return 0;
	}

	public Object[] getSections() {
		final String[] letters = new String[sections.size()];
		for (int i = 0; i < sections.size(); i++){
			letters[i] = sections.get(i).title.substring(0, 1);
		}
		Log.d("foo", "get sections" + letters);
		return letters;
	}
	
	private class SectionDataSetObserver extends DataSetObserver{
		private final SectionHeader header;
		
		public SectionDataSetObserver(SectionHeader header) {
			this.header = header;
		}
		
		@Override
		public void onChanged() {
			header.sectionCount = header.adapter.getCount();
		}
	}
}

