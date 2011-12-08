/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package edu.mit.mobile.android.locast.accounts;

import java.io.IOException;

import org.json.JSONException;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.SettingsActivity;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;
import edu.mit.mobile.android.locast.ver2.R;

/**
 * Activity which displays login screen to the user.
 */
public class AuthenticatorActivity extends AccountAuthenticatorActivity implements OnClickListener, OnEditorActionListener {
	private static final String TAG = AuthenticatorActivity.class.getSimpleName();

    public static final String
    	EXTRA_CONFIRMCREDENTIALS 	= "confirmCredentials",
    	EXTRA_PASSWORD 				= "password",
    	EXTRA_USERNAME 				= "username",
    	EXTRA_AUTHTOKEN_TYPE 		= "authtokenType";

    private AccountManager mAccountManager;
    private String mAuthtoken;
    private String mAuthtokenType;

    private static final int
    	DIALOG_PROGRESS = 0,
    	DIALOG_SET_BASE_URL = 1;

    /**
     * If set we are just checking that the user knows their credentials; this
     * doesn't cause the user's password to be changed on the device.
     */
    private Boolean mConfirmCredentials = false;

    private TextView mMessage;
    private String mPassword;
    private EditText mPasswordEdit;

    /** Was the original caller asking for an entirely new account? */
    protected boolean mRequestNewAccount = false;

    private String mUsername;
    private EditText mUsernameEdit;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle icicle) {
        Log.i(TAG, "onCreate(" + icicle + ")");
        super.onCreate(icicle);

        mAccountManager = AccountManager.get(this);
        Log.i(TAG, "loading data from Intent");

        final Intent intent = getIntent();
        mUsername = intent.getStringExtra(EXTRA_USERNAME);
        mAuthtokenType = intent.getStringExtra(EXTRA_AUTHTOKEN_TYPE);
        mRequestNewAccount = mUsername == null;
        mConfirmCredentials =
            intent.getBooleanExtra(EXTRA_CONFIRMCREDENTIALS, false);

        Log.i(TAG, "    request new: " + mRequestNewAccount);
        requestWindowFeature(Window.FEATURE_LEFT_ICON);
        // make the title based on the app name.
        setTitle(getString(R.string.login_title, getString(R.string.app_name)));

        setContentView(R.layout.login);
        // this is done this way, so the associated icon is managed in XML.
        try {
			getWindow().setFeatureDrawable(Window.FEATURE_LEFT_ICON,
			    getPackageManager().getActivityIcon(getComponentName()));
		} catch (final NameNotFoundException e) {
			e.printStackTrace();
		}



        mMessage = (TextView) findViewById(R.id.message);
        mUsernameEdit = (EditText) findViewById(R.id.username);
        mPasswordEdit = (EditText) findViewById(R.id.password);
        mPasswordEdit.setOnEditorActionListener(this);
        findViewById(R.id.login).setOnClickListener(this);
        findViewById(R.id.cancel).setOnClickListener(this);
        ((Button)findViewById(R.id.register)).setOnClickListener(this);

        mUsernameEdit.setText(mUsername);

        mAuthenticationTask = (AuthenticationTask) getLastNonConfigurationInstance();
        if (mAuthenticationTask != null){
        	mAuthenticationTask.attach(this);
        }
    }

    /*
     * {@inheritDoc}
     */
    @Override
    protected Dialog onCreateDialog(int id) {
    	switch (id){
    	case DIALOG_PROGRESS:

        final ProgressDialog dialog = new ProgressDialog(this);
        dialog.setMessage(getText(R.string.login_message_authenticating));
        dialog.setIndeterminate(true);
        dialog.setCancelable(true);
        dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {
                Log.i(TAG, "dialog cancel has been invoked");
                if (mAuthenticationTask != null) {
                    mAuthenticationTask.cancel(true);
                    mAuthenticationTask = null;
                    finish();
                }
            }
        });
        return dialog;

    	case DIALOG_SET_BASE_URL:

    		final EditText baseUrl = new EditText(this);
    		baseUrl.setText(getString(R.string.default_api_url));
    		final AlertDialog.Builder db = new AlertDialog.Builder(this);
    		return db.create();

        default:
        	return null;
    	}
    }

	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.login:
			handleLogin();
			break;

		case R.id.cancel:
			finish();
			break;

		case R.id.register:
			startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.signup_url))));
			break;
		}
	}

    /**
     * Handles onClick event on the Submit button. Sends username/password to
     * the server for authentication.
     */
    private void handleLogin() {
        if (mRequestNewAccount) {
            mUsername = mUsernameEdit.getText().toString();
        }
        mPassword = mPasswordEdit.getText().toString();
        if (validateEntry()) {
        	mAuthenticationTask = new AuthenticationTask(this);
        	mAuthenticationTask.execute(mUsername, mPassword);
        }
    }

    /**
     * Called when response is received from the server for confirm credentials
     * request. See onAuthenticationResult(). Sets the
     * AccountAuthenticatorResult which is sent back to the caller.
     *
     * @param the confirmCredentials result.
     */
    protected void finishConfirmCredentials(boolean result) {
        Log.i(TAG, "finishConfirmCredentials()");
        final Account account = new Account(mUsername, AuthenticationService.ACCOUNT_TYPE);
        mAccountManager.setPassword(account, mPassword);
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_BOOLEAN_RESULT, result);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    /**
     *
     * Called when response is received from the server for authentication
     * request. See onAuthenticationResult(). Sets the
     * AccountAuthenticatorResult which is sent back to the caller. Also sets
     * the authToken in AccountManager for this account.
     * @param userData TODO
     * @param the confirmCredentials result.
     */

    protected void finishLogin(Bundle userData) {
        Log.i(TAG, "finishLogin()");
        final Account account = new Account(mUsername, AuthenticationService.ACCOUNT_TYPE);

        if (mRequestNewAccount) {
            mAccountManager.addAccountExplicitly(account, mPassword, userData);
            // Automatically enable sync for this account
            ContentResolver.setSyncAutomatically(account,
                MediaProvider.AUTHORITY, true);
        } else {
            mAccountManager.setPassword(account, mPassword);
        }
        final Intent intent = new Intent();
        mAuthtoken = mPassword;
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mUsername);
        intent
            .putExtra(AccountManager.KEY_ACCOUNT_TYPE, AuthenticationService.ACCOUNT_TYPE);
        if (mAuthtokenType != null
            && mAuthtokenType.equals(AuthenticationService.AUTHTOKEN_TYPE)) {
            intent.putExtra(AccountManager.KEY_AUTHTOKEN, mAuthtoken);
        }
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    private void setLoginNoticeError(int textResID){
    	mMessage.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning, 0, 0, 0);
        mMessage.setText(textResID);
        mMessage.setVisibility(View.VISIBLE);

    }

    private void setLoginNoticeError(String text){
    	mMessage.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning, 0, 0, 0);
        mMessage.setText(text);
        mMessage.setVisibility(View.VISIBLE);

    }

    private void setLoginNoticeInfo(int textResID){
    	mMessage.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_info, 0, 0, 0);
        mMessage.setText(textResID);
        mMessage.setVisibility(View.VISIBLE);
    }

    /**
     * Called when the authentication process completes (see attemptLogin()).
     */
    public void onAuthenticationResult(Bundle userData, String reason) {
        Log.i(TAG, "onAuthenticationResult(" + userData + ")");

        if (userData != null) {
            if (!mConfirmCredentials) {
                finishLogin(userData);
            } else {
                finishConfirmCredentials(true);
            }
        } else {
        	if (reason == null){
        		Log.e(TAG, "onAuthenticationResult: failed to authenticate");
            	setLoginNoticeError(R.string.login_message_loginfail);
        	}else{
        		setLoginNoticeError(reason);
        	}
        }
    }

    /**
     * Validates the login form.
     * @return true if the form is valid.
     */
    private boolean validateEntry() {
        if (TextUtils.isEmpty(mUsername)) {
            // If no username, then we ask the user to log in using an
            // appropriate service.

        	mUsernameEdit.setError(getText(R.string.login_message_login_empty_username));
            mUsernameEdit.requestFocus();
            return false;
        }else{
        	mUsernameEdit.setError(null);
        }

        if (TextUtils.isEmpty(mPassword)) {
        	mPasswordEdit.setError(getText(R.string.login_message_login_empty_password));
        	mPasswordEdit.requestFocus();
        	return false;
        }else{
        	mPasswordEdit.setError(null);
        }
        return true;
    }

    private AuthenticationTask mAuthenticationTask = null;

    @Override
    public Object onRetainNonConfigurationInstance() {
    	if (mAuthenticationTask != null){
    		mAuthenticationTask.detach();
    	}
    	return mAuthenticationTask;
    }

    private class AuthenticationTask extends AsyncTask<String, Long, Bundle>{
    	private AuthenticatorActivity mActivity;

    	private String reason;
    	public AuthenticationTask(AuthenticatorActivity activity) {
    		mActivity = activity;
		}

    	@Override
    	protected void onPreExecute() {
    		mActivity.showDialog(DIALOG_PROGRESS);
    	}

		@Override
		protected Bundle doInBackground(String... userPass) {
			try {
				return NetworkClient.authenticate(AuthenticatorActivity.this, userPass[0], userPass[1]);

			} catch (final IOException e) {
				reason = mActivity
						.getString(R.string.auth_error_could_not_contact_server);
				e.printStackTrace();
			} catch (final JSONException e) {
				reason = mActivity
						.getString(R.string.auth_error_server_returned_invalid_data);
				e.printStackTrace();
			} catch (final NetworkProtocolException e) {
				reason = mActivity.getString(R.string.auth_error_network_protocol_error, e.getHttpResponseMessage());
				e.printStackTrace();
			}
			return null;
		}
    	@Override
    	protected void onPostExecute(Bundle userData) {
    		mActivity.dismissDialog(DIALOG_PROGRESS);
    		mActivity.onAuthenticationResult(userData, reason);
    	}

    	public void detach(){
    		mActivity = null;
    	}
    	public void attach(AuthenticatorActivity activity){
    		mActivity = activity;
    	}
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	super.onCreateOptionsMenu(menu);
    	getMenuInflater().inflate(R.menu.login_options, menu);
    	if (Constants.DEBUG){
    		menu.findItem(R.id.set_base_url).setVisible(true);
    	}
    	return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch (item.getItemId()){
    	case R.id.set_base_url:
    		startActivity(new Intent(this, SettingsActivity.class));
    		return true;

    		default:
    			return super.onOptionsItemSelected(item);
    	}
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
    	switch (v.getId()){
    	case R.id.password:
    		handleLogin();
    		return true;
    	}
    	return false;
    }
}