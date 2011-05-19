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
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.EditText;
import android.widget.TextView;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

/**
 * Activity which displays login screen to the user.
 */
public class AuthenticatorActivity extends AccountAuthenticatorActivity implements OnClickListener {
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
    	DIALOG_PROGRESS = 0;

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
        findViewById(R.id.login).setOnClickListener(this);
        findViewById(R.id.cancel).setOnClickListener(this);

        mUsernameEdit.setText(mUsername);
        mMessage.setText(getMessage());

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
    }

	@Override
	public void onClick(View v) {
		switch(v.getId()){
		case R.id.login:
			handleLogin(v);
			break;

		case R.id.cancel:
			finish();
			break;

		}
	}

    /**
     * Handles onClick event on the Submit button. Sends username/password to
     * the server for authentication.
     *
     * @param view The Submit button for which this method is invoked
     */
    private void handleLogin(View view) {
        if (mRequestNewAccount) {
            mUsername = mUsernameEdit.getText().toString();
        }
        mPassword = mPasswordEdit.getText().toString();
        if (TextUtils.isEmpty(mUsername) || TextUtils.isEmpty(mPassword)) {
            mMessage.setText(getMessage());
        } else {
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
     *
     * @param the confirmCredentials result.
     */

    protected void finishLogin() {
        Log.i(TAG, "finishLogin()");
        final Account account = new Account(mUsername, AuthenticationService.ACCOUNT_TYPE);

        if (mRequestNewAccount) {
            mAccountManager.addAccountExplicitly(account, mPassword, null);
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

    /**
     * Called when the authentication process completes (see attemptLogin()).
     */
    public void onAuthenticationResult(boolean result) {
        Log.i(TAG, "onAuthenticationResult(" + result + ")");

        if (result) {
            if (!mConfirmCredentials) {
                finishLogin();
            } else {
                finishConfirmCredentials(true);
            }
        } else {
            Log.e(TAG, "onAuthenticationResult: failed to authenticate");
            if (mRequestNewAccount) {
                // "Please enter a valid username/password.
                mMessage
                    .setText(getText(R.string.login_message_loginfail));
            } else {
                // "Please enter a valid password." (Used when the
                // account is already in the database but the password
                // doesn't work.)
                mMessage
                    .setText(getText(R.string.login_message_loginfail));
            }
        }
    }

    /**
     * Returns the message to be displayed at the top of the login dialog box.
     */
    private CharSequence getMessage() {
        //getString(R.string.label);
        if (TextUtils.isEmpty(mUsername)) {
            // If no username, then we ask the user to log in using an
            // appropriate service.
            final CharSequence msg =
                getText(R.string.login_message_login_empty_username);
            return msg;
        }
        if (TextUtils.isEmpty(mPassword)) {
            // We have an account but no password
            return getText(R.string.login_message_login_empty_password);
        }
        return null;
    }

    private AuthenticationTask mAuthenticationTask = null;

    @Override
    public Object onRetainNonConfigurationInstance() {
    	if (mAuthenticationTask != null){
    		mAuthenticationTask.detach();
    	}
    	return mAuthenticationTask;
    }

    private class AuthenticationTask extends AsyncTask<String, Long, Boolean>{
    	private AuthenticatorActivity mActivity;

    	public AuthenticationTask(AuthenticatorActivity activity) {
    		mActivity = activity;
		}

    	@Override
    	protected void onPreExecute() {
    		mActivity.showDialog(DIALOG_PROGRESS);
    	}

		@Override
		protected Boolean doInBackground(String... userPass) {
			try {
				return NetworkClient.authenticate(AuthenticatorActivity.this, userPass[0], userPass[1]);

			} catch (final IOException e) {
				e.printStackTrace();
			} catch (final JSONException e) {
				e.printStackTrace();
			} catch (final NetworkProtocolException e) {
				e.printStackTrace();
			}
			return false;
		}
    	@Override
    	protected void onPostExecute(Boolean result) {
    		mActivity.dismissDialog(DIALOG_PROGRESS);
    		mActivity.onAuthenticationResult(result);
    	}

    	public void detach(){
    		mActivity = null;
    	}
    	public void attach(AuthenticatorActivity activity){
    		mActivity = activity;
    	}
    }
}