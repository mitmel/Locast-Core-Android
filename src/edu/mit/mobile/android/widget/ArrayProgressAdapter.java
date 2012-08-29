package edu.mit.mobile.android.widget;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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
import java.util.ArrayList;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ProgressBar;

public class ArrayProgressAdapter<T> extends BaseAdapter implements RelativeSizeListAdapter{
    private final Context mContext;


    private final ArrayList<ProgressItem> items = new ArrayList<ProgressItem>();
    public ArrayProgressAdapter(Context context) {
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        final ProgressBar progress;

        if (convertView == null){
             progress = new ProgressBar(mContext, null, android.R.attr.progressBarStyleHorizontal);
             progress.setIndeterminate(false);
        }else{
            progress = (ProgressBar) convertView;
        }

        final ProgressItem item = getItem(position);
        progress.setMax((int) item.max);
        progress.setProgress((int) item.value);
        return progress;
    }

    @Override
    public float getRelativeSize(int position) {
        final ProgressItem item = getItem(position);
        return item.max;
    }

    public void clear(){
        items.clear();
    }

    public void add(T data, float value, float max){
        items.add(new ProgressItem(data, value, max));
        notifyDataSetChanged();
    }

    public void update(int position, T data, float value, float max){
        final ProgressItem item = getItem(position);
        item.data = data;
        item.value = value;
        item.max = max;
        notifyDataSetChanged();
    }


    @Override
    public int getCount() {
        return items.size();
    }


    @Override
    public ProgressItem getItem(int position) {
        return items.get(position);
    }


    @Override
    public long getItemId(int position) {
        return position;
    }

    public class ProgressItem{
        public T data;
        public float value;
        public float max;

        public ProgressItem(T data, float value, float max){
            this.data = data;
            this.value = value;
            this.max = max;
        }
    }
}