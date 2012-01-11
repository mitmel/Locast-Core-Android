package edu.mit.mobile.android.locast.accounts;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.ver2.R;

public class SigninOrSkip extends Activity implements OnClickListener {

	/**
	 * A CharSequence
	 */
	public static final String EXTRA_MESSAGE = "edu.mit.mobile.android.locast.SigninOrSkip.EXTRA_MESSAGE",
			EXTRA_SKIP_IS_CANCEL = "edu.mit.mobile.android.locast.SigninOrSkip.EXTRA_SKIP_IS_CANCEL";

	private boolean mSkipIsCancel = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.auth_signin_or_skip);

		findViewById(R.id.sign_in).setOnClickListener(this);
		final Button skip = (Button) findViewById(R.id.skip);
		skip.setOnClickListener(this);
		findViewById(R.id.refresh).setVisibility(View.GONE);

		final Bundle extras = getIntent().getExtras();
		if (extras != null) {
			final CharSequence msg = extras.getCharSequence(EXTRA_MESSAGE);
			if (msg != null) {
				((TextView) findViewById(R.id.sign_in_or_skip_notice)).setText(msg);
			}

			mSkipIsCancel = extras.getBoolean(EXTRA_SKIP_IS_CANCEL, mSkipIsCancel);
			if (mSkipIsCancel) {
				skip.setText(android.R.string.cancel);
			}
		}
	}

	private void skip(){
		if (mSkipIsCancel) {
			setResult(RESULT_CANCELED);
			finish();
			return;
		}
		Authenticator.addDemoAccount(this);
		setResult(RESULT_OK);
		finish();
	}
	public static final int REQUEST_SIGNIN = 100;

	@Override
	public void onClick(View v) {
		switch (v.getId()){
		case R.id.sign_in:
			startActivityForResult(new Intent(this, AuthenticatorActivity.class), REQUEST_SIGNIN);
			break;

		case R.id.skip:
			skip();
			break;
		}
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		switch(requestCode){
		case REQUEST_SIGNIN:
			if (resultCode == RESULT_OK){
				setResult(RESULT_OK);
				finish();
			}
			break;
		}
	}

	/**
	 * @return true if this seems to be the first time running the app
	 */
	public static final boolean checkFirstTime(Context context) {
		return Authenticator.getAccounts(context).length == 0;
	}

	public static final void startSignin(Activity context, int requestCode){
		if (Constants.REQUIRE_LOGIN) {
			context.startActivityForResult(new Intent(context, AuthenticatorActivity.class),
					requestCode);
		} else {
			context.startActivityForResult(new Intent(context, SigninOrSkip.class), requestCode);
		}
	}
}
