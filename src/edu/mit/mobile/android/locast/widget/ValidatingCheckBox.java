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
import android.content.Context;
import android.os.AsyncTask;
import android.util.AttributeSet;
import android.widget.CheckBox;

/**
 * Like CheckBox, but lets you verify that the internal state was
 * properly set upon checking. For example, this could make a network call
 * and verify that the remote state was changed before showing the new check state.
 * While the performClick() handler is being called, shows an intermediate pressed state.
 *
 * @author steve
 *
 */
public class ValidatingCheckBox extends CheckBox {
	private ValidatedClickHandler mValidatedClickHandler;
	public ValidatingCheckBox(Context context) {
		this(context, null);
	}
	public ValidatingCheckBox(Context context, AttributeSet attrs) {
		this(context, attrs, 0);
	}
	public ValidatingCheckBox(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);


	}

	@Override
	public boolean performClick() {
		if (mValidatedClickHandler != null){
			new ValidatedClickTask().execute();
			return true;
		}else{
			return super.performClick();
		}
	}

	public void setValidatedClickHandler(
			ValidatedClickHandler mValidatedClickHandler) {
		this.mValidatedClickHandler = mValidatedClickHandler;
	}

	/**
	 * Like CheckBox, but lets you set the state of the checkbox after
	 * validating that it has been properly set on whatever model backs the state.
	 *
	 * @author steve
	 *
	 */
	public interface ValidatedClickHandler {
		/**
		 * This will get called on a background thread, so you can block here if you
		 * want.
		 *
		 * @param checkBox
		 * @return the new state of the checkbox or null if you wish to leave it unchanged.
		 */
		public Boolean performClick(ValidatingCheckBox checkBox);
	}

	private class ValidatedClickTask extends AsyncTask<Void, Void, Boolean> {

		@Override
		protected void onPreExecute() {
			setEnabled(false);
		}

		@Override
		protected Boolean doInBackground(Void... params) {
			return mValidatedClickHandler.performClick(ValidatingCheckBox.this);
		}

		@Override
		protected void onPostExecute(Boolean result) {
			setEnabled(true);

			if (result != null){
				setChecked(result);
			}
		}
	}
}
