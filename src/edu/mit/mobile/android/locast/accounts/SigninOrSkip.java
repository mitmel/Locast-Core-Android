package edu.mit.mobile.android.locast.accounts;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.View.OnClickListener;
import edu.mit.mobile.android.locast.R;

public class SigninOrSkip extends Activity implements OnClickListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.auth_signin_or_skip);

		findViewById(R.id.sign_in).setOnClickListener(this);
		findViewById(R.id.skip).setOnClickListener(this);

	}

	private void skip(){
		final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		prefs.edit().putBoolean(Authenticator.PREF_SKIP_AUTH, true).commit();
		setResult(RESULT_OK);
		finish();
	}
	private static final int REQUEST_SIGNIN = 0;

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
}
