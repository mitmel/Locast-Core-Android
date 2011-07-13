package edu.mit.mobile.android.locast.ver2.browser;

import java.io.File;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Toast;
import edu.mit.mobile.android.locast.accounts.Authenticator;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.Comment;
import edu.mit.mobile.android.locast.data.Event;
import edu.mit.mobile.android.locast.data.Itinerary;
import edu.mit.mobile.android.locast.data.Sync;
import edu.mit.mobile.android.locast.ver2.R;

public class ResetActivity extends Activity implements OnClickListener {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.reset_activity);

		startService(new Intent(Sync.ACTION_CANCEL_SYNC));

		findViewById(R.id.reset).setOnClickListener(this);
		findViewById(R.id.cancel).setOnClickListener(this);
	}

	@Override
	public void onClick(View v) {
		switch (v.getId()) {
		case R.id.reset:
			resetEverything();
			setResult(RESULT_OK);
			finish();
			break;

		case R.id.cancel:
			setResult(RESULT_CANCELED);
			finish();
			break;
		}
	}

	private void resetEverything(){
		final AccountManager am = (AccountManager) getSystemService(ACCOUNT_SERVICE);

		for (final Account account : Authenticator.getAccounts(this)){
			am.removeAccount(account, null, null);
		}

		// clear cache
		for (final File file : getCacheDir().listFiles()){
			file.delete();
		}

		final ContentResolver cr = getContentResolver();

		cr.delete(Cast.CONTENT_URI, null, null);
		cr.delete(Comment.CONTENT_URI, null, null);
		cr.delete(Event.CONTENT_URI, null, null);
		cr.delete(Itinerary.CONTENT_URI, null, null);

		Toast.makeText(getApplicationContext(), R.string.notice_databases_reset, Toast.LENGTH_LONG).show();
	}
}
