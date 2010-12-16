package edu.mit.mobile.android.widget;
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
/*
 * Parts Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import android.content.Context;
import android.database.DataSetObserver;
import android.util.AttributeSet;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;

/**
 * Displays a list of views representing the contents of the adapter.
 * Shows all views at once and sizes them relative to one another based on the value of
 * getRelativeSize()
 *
 * @author steve
 *
 */
public class RelativeSizeListView extends AdapterView<RelativeSizeListAdapter> {
	private RelativeSizeListAdapter mAdapter;
	private int mSelectedPosition = INVALID_POSITION;
	private long mSelectedRowId = INVALID_ROW_ID;

	private DataSetObserver mDataSetObserver;
	private int mItemCount;
	private int mRelMax;
	private float mMinWidth;

	private final LinearLayout mLinearLayout;

	public RelativeSizeListView(Context context, AttributeSet attrs,
			int defStyle) {
		super(context, attrs, defStyle);

		setFocusable(true);
		setWillNotDraw(true);
		mLinearLayout = new LinearLayout(context, attrs);
		mLinearLayout.setLayoutParams(new LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));

	}

	public RelativeSizeListView(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}

	public RelativeSizeListView(Context context) {
		this(context, null);
	}

	@Override
	public RelativeSizeListAdapter getAdapter() {
		return mAdapter;
	}

	@Override
	public View getSelectedView() {
		if (mItemCount > 0 && mSelectedPosition >= 0){
			return getChildAt(mSelectedPosition);
		}else{
			return null;
		}
	}

	@Override
	public int getCount() {
		return mItemCount;
	}

	public void setMinWidth(float minWidth){
		mMinWidth = minWidth;
		requestLayout();
	}

	@Override
	public void setAdapter(RelativeSizeListAdapter adapter) {
		if (mAdapter != null){
			mAdapter.unregisterDataSetObserver(mDataSetObserver);
			resetList();
		}

		mAdapter = adapter;

		if (mAdapter != null){
			updateCached();

			mDataSetObserver = new AdapterDataSetObserver();
			mAdapter.registerDataSetObserver(mDataSetObserver);

		}else{
			resetList();
		}
		requestLayout();
	}

	@Override
	public void setSelection(int position) {
		mSelectedPosition = position;
		requestLayout();
		invalidate();

	}

	@Override
	protected void onLayout(boolean changed, int left, int top, int right,
			int bottom) {
		super.onLayout(changed, left, top, right, bottom);

		if (mAdapter == null){
			return;
		}

		if (getChildCount() == 0){
			addViewInLayout(mLinearLayout, -1, new AdapterView.LayoutParams(LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT), true);

		}

		final int currentCount = mLinearLayout.getChildCount();

		// first remove any views
		mLinearLayout.removeViews(0, Math.max(0, currentCount - mItemCount));

		for (int i = 0; i < mItemCount; i++){
			final View existingView = i < currentCount ? mLinearLayout.getChildAt(i): null;
			final View child = mAdapter.getView(i, existingView, mLinearLayout);
			final LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LinearLayout.LayoutParams.FILL_PARENT, LinearLayout.LayoutParams.FILL_PARENT);

			child.setLayoutParams(params);
			(params).weight = mRelMax - mAdapter.getRelativeSize(i);
			if (existingView == null){
				mLinearLayout.addView(child);
			}
		}

		mLinearLayout.measure(MeasureSpec.EXACTLY | right - left, MeasureSpec.EXACTLY | bottom - top);
		mLinearLayout.layout(0, 0, right-left, bottom-top);
	}

	private void resetList(){
		mLinearLayout.removeAllViews();
		mSelectedPosition = INVALID_POSITION;
		mSelectedRowId = INVALID_ROW_ID;
		invalidate();
	}

	private void updateCached(){
		mItemCount = mAdapter.getCount();
		mRelMax = 0;
		for (int i = 0; i < mItemCount; i++){
			mRelMax += mAdapter.getRelativeSize(i);
		}
	}

	class AdapterDataSetObserver extends DataSetObserver {
		@Override
		public void onChanged() {
			updateCached();
			requestLayout();
		}

		@Override
		public void onInvalidated() {
			mItemCount = 0;

			resetList();
		}
	}
}
