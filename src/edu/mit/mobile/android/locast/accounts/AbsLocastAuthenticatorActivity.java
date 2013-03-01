/*
 * Copyright (C) 2010-2012 The Android Open Source Project
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
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import edu.mit.mobile.android.locast.BuildConfig;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.net.LocastApplicationCallbacks;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

/**
 * Activity which displays login screen to the user.
 */
public abstract class AbsLocastAuthenticatorActivity extends AccountAuthenticatorActivity implements
        OnClickListener, OnEditorActionListener {
    private static final String TAG = AbsLocastAuthenticatorActivity.class.getSimpleName();

    public static final String EXTRA_CONFIRMCREDENTIALS = "confirmCredentials";
    public static final String EXTRA_PASSWORD = "password";
    public static final String EXTRA_USERNAME = "username";
    public static final String EXTRA_AUTHTOKEN_TYPE = "authtokenType";

    private AccountManager mAccountManager;
    private String mAuthtoken;
    private String mAuthtokenType;

    private static final int DIALOG_PROGRESS = 100;

    private static final int REQUEST_REGISTER = 200;

    /**
     * If set we are just checking that the user knows their credentials; this doesn't cause the
     * user's password to be changed on the device.
     */
    private Boolean mConfirmCredentials = false;

    private TextView mMessage;
    private String mPassword;
    private EditText mPasswordEdit;

    /** Was the original caller asking for an entirely new account? */
    protected boolean mRequestNewAccount = false;

    private String mUsername;
    private EditText mUsernameEdit;

    private Button mRegisterButton;

    private Intent mRegistrationComplete;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate(Bundle icicle) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "onCreate(" + icicle + ")");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB) {
            requestWindowFeature(Window.FEATURE_ACTION_BAR);
        }
        super.onCreate(icicle);

        mAccountManager = AccountManager.get(this);
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "loading data from Intent");
        }

        final Intent intent = getIntent();
        mUsername = intent.getStringExtra(EXTRA_USERNAME);
        mAuthtokenType = intent.getStringExtra(EXTRA_AUTHTOKEN_TYPE);
        mRequestNewAccount = mUsername == null;
        mConfirmCredentials = intent.getBooleanExtra(EXTRA_CONFIRMCREDENTIALS, false);

        if (BuildConfig.DEBUG) {
            Log.i(TAG, "    request new: " + mRequestNewAccount);
        }
        requestWindowFeature(Window.FEATURE_LEFT_ICON);

        final CharSequence appName = getAppName();

        // make the title based on the app name.
        setTitle(getString(R.string.login_title, appName));

        // TODO make this changeable. Maybe use fragments?
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
        mUsernameEdit.setHint(isEmailAddressLogin() ? R.string.auth_email_login_hint
                : R.string.auth_username_hint);
        mPasswordEdit = (EditText) findViewById(R.id.password);
        mPasswordEdit.setOnEditorActionListener(this);
        findViewById(R.id.login).setOnClickListener(this);
        findViewById(R.id.cancel).setOnClickListener(this);
        mRegisterButton = (Button) findViewById(R.id.register);
        mRegisterButton.setOnClickListener(this);
        final String regButton = getString(R.string.signup_text, appName);
        if (regButton.length() < 24) {
            mRegisterButton.setText(regButton);
        }

        ((TextView) findViewById(R.id.username_label)).setText(getString(R.string.username_label,
                appName));

        mUsernameEdit.setText(mUsername);

        // this will be unnecessary with fragments
        mAuthenticationTask = (AuthenticationTask) getLastNonConfigurationInstance();
        if (mAuthenticationTask != null) {
            mAuthenticationTask.attach(this);
        }
    }

    /**
     * @return the app's name
     */
    protected abstract CharSequence getAppName();

    /**
     * @return true if the user's email address is their login.
     */
    protected abstract boolean isEmailAddressLogin();

    /*
     * {@inheritDoc}
     */
    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case DIALOG_PROGRESS:

                final ProgressDialog dialog = new ProgressDialog(this);
                dialog.setMessage(getText(R.string.login_message_authenticating));
                dialog.setIndeterminate(true);
                dialog.setCancelable(true);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    public void onCancel(DialogInterface dialog) {
                        if (BuildConfig.DEBUG) {
                            Log.i(TAG, "dialog cancel has been invoked");
                        }
                        if (mAuthenticationTask != null) {
                            mAuthenticationTask.cancel(true);
                            mAuthenticationTask = null;
                            finish();
                        }
                    }
                });
                return dialog;

            default:
                return null;
        }
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.login) {
            handleLogin();
        } else if (v.getId() == R.id.cancel) {
            finish();
        } else if (v.getId() == R.id.register) {
            startActivityForResult(getSignupIntent(), REQUEST_REGISTER);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        switch (requestCode) {
            case REQUEST_REGISTER:
                if (resultCode == RESULT_OK) {
                    mRegistrationComplete = data;
                }
                break;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mRegistrationComplete != null) {
            onRegistrationComplete(mRegistrationComplete);
            mRegistrationComplete = null;
        }
    }

    /**
     * Called when registration returns successfully. Intent results are straight from the server.
     *
     * @param data
     *            this is an echo of the data sent to the server
     */
    protected void onRegistrationComplete(Intent data) {
        final String username = data.getStringExtra(EXTRA_USERNAME);
        final String password = data.getStringExtra(EXTRA_PASSWORD);
        mUsernameEdit.setText(username);
        mPasswordEdit.setText(password);

        handleLogin();
    }

    /**
     * Generate a new account.
     *
     * @return
     */
    protected abstract Account createAccount(String username);

    /**
     * @return the authority that this authenticator handles.
     */
    protected abstract String getAuthority();

    /**
     * @return an intent which will take the user to the sign up page. Started with startActivity().
     */
    protected abstract Intent getSignupIntent();

    /**
     * Handles onClick event on the Submit button. Sends username/password to the server for
     * authentication.
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
     * @deprecated this will be retrieved using {@code LocastApplicationCallbacks}
     * @return the base URL for the Locast API
     */
    @Deprecated
    public String getApiUrl() {
        final NetworkClient nc = ((LocastApplicationCallbacks) getApplication())
                .getNetworkClientForAccount(this, null);
        return nc.getBaseUrl();
    }

    /**
     * Called when response is received from the server for confirm credentials request. See
     * onAuthenticationResult(). Sets the AccountAuthenticatorResult which is sent back to the
     * caller.
     *
     * @param the
     *            confirmCredentials result.
     */
    protected void finishConfirmCredentials(boolean result) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "finishConfirmCredentials()");
        }
        final Account account = createAccount(mUsername);
        mAccountManager.setPassword(account, mPassword);
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_BOOLEAN_RESULT, result);
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    /**
     *
     * Called when response is received from the server for authentication request. See
     * onAuthenticationResult(). Sets the AccountAuthenticatorResult which is sent back to the
     * caller. Also sets the authToken in AccountManager for this account.
     *
     * @param userData
     *            TODO
     * @param the
     *            confirmCredentials result.
     */

    protected void finishLogin(Bundle userData) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "finishLogin()");
        }

        final Account account = createAccount(mUsername);

        if (mRequestNewAccount) {
            mAccountManager.addAccountExplicitly(account, mPassword, userData);
            // Automatically enable sync for this account
            ContentResolver.setSyncAutomatically(account, getAuthority(), true);
        } else {
            mAccountManager.setPassword(account, mPassword);
        }
        final Intent intent = new Intent();
        mAuthtoken = mPassword;
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, mUsername);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, getAccountType());
        if (mAuthtokenType != null && mAuthtokenType.equals(getAuthtokenType())) {
            intent.putExtra(AccountManager.KEY_AUTHTOKEN, mAuthtoken);
        }
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }

    protected abstract String getAccountType();

    protected abstract String getAuthtokenType();

    private void setLoginNoticeError(int textResID) {
        mMessage.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning, 0, 0, 0);
        mMessage.setText(textResID);
        mMessage.setVisibility(View.VISIBLE);

    }

    private void setLoginNoticeError(String text) {
        mMessage.setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_warning, 0, 0, 0);
        mMessage.setText(text);
        mMessage.setVisibility(View.VISIBLE);

    }

    /**
     * Called when the authentication process completes.
     */
    public void onAuthenticationResult(Bundle userData, String reason) {
        if (BuildConfig.DEBUG) {
            Log.i(TAG, "onAuthenticationResult(" + userData + ")");
        }

        if (userData != null) {
            if (!mConfirmCredentials) {
                finishLogin(userData);
            } else {
                finishConfirmCredentials(true);
            }
        } else {
            if (reason == null) {
                Log.e(TAG, "onAuthenticationResult: failed to authenticate");
                setLoginNoticeError(R.string.login_message_loginfail);
            } else {
                setLoginNoticeError(reason);
            }
        }
    }

    /**
     * Validates the login form.
     *
     * @return true if the form is valid.
     */
    private boolean validateEntry() {
        if (TextUtils.isEmpty(mUsername)) {
            // If no username, then we ask the user to log in using an
            // appropriate service.

            mUsernameEdit.setError(getString(R.string.login_message_login_empty_username,
                    getAppName()));
            mUsernameEdit.requestFocus();
            return false;
        } else {
            mUsernameEdit.setError(null);
        }

        if (TextUtils.isEmpty(mPassword)) {
            mPasswordEdit.setError(getText(R.string.login_message_login_empty_password));
            mPasswordEdit.requestFocus();
            return false;
        } else {
            mPasswordEdit.setError(null);
        }
        return true;
    }

    private AuthenticationTask mAuthenticationTask = null;

    @Override
    public Object onRetainNonConfigurationInstance() {
        if (mAuthenticationTask != null) {
            mAuthenticationTask.detach();
        }
        return mAuthenticationTask;
    }

    private class AuthenticationTask extends AsyncTask<String, Long, Bundle> {
        private AbsLocastAuthenticatorActivity mActivity;

        private String reason;

        public AuthenticationTask(AbsLocastAuthenticatorActivity activity) {
            mActivity = activity;
        }

        @SuppressWarnings("deprecation")
        @Override
        protected void onPreExecute() {
            mActivity.showDialog(DIALOG_PROGRESS);
        }

        @Override
        protected Bundle doInBackground(String... userPass) {

            try {
                final NetworkClient nc = ((LocastApplicationCallbacks) getApplication())
                        .getNetworkClientForAccount(AbsLocastAuthenticatorActivity.this, null);
                return nc.authenticate(userPass[0], userPass[1]);

            } catch (final IOException e) {
                reason = mActivity.getString(R.string.auth_error_could_not_contact_server);
                e.printStackTrace();
            } catch (final JSONException e) {
                reason = mActivity.getString(R.string.auth_error_server_returned_invalid_data);
                e.printStackTrace();
            } catch (final NetworkProtocolException e) {
                reason = mActivity.getString(R.string.auth_error_network_protocol_error,
                        e.getHttpResponseMessage());
                e.printStackTrace();
            }
            return null;
        }

        @SuppressWarnings("deprecation")
        @Override
        protected void onPostExecute(Bundle userData) {
            mActivity.dismissDialog(DIALOG_PROGRESS);
            mActivity.onAuthenticationResult(userData, reason);
        }

        public void detach() {
            mActivity = null;
        }

        public void attach(AbsLocastAuthenticatorActivity activity) {
            mActivity = activity;
        }
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (v.getId() == R.id.password) {
            handleLogin();
            return true;
        }
        return false;
    }

    /**
     * Handles the removal of an account when the logout button is pressed. Also provides a callback
     * for when an account is successfully removed.
     *
     * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
     *
     */
    public static abstract class LogoutHandler implements DialogInterface.OnClickListener {

        private final Context mContext;
        private final AccountManagerCallback<Boolean> mAccountManagerCallback = new AccountManagerCallback<Boolean>() {

            @Override
            public void run(AccountManagerFuture<Boolean> amf) {
                boolean success = false;
                try {
                    success = amf.getResult();
                } catch (final OperationCanceledException e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);

                } catch (final AuthenticatorException e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);

                } catch (final IOException e) {
                    Log.e(TAG, e.getLocalizedMessage(), e);
                }

                onAccountRemoved(success);

            }
        };
        private final String mAccountType;
        private final Account mAccount;

        /**
         * @param context
         * @param accountType
         * @deprecated please pass in the account using {@link #LogoutHandler(Context, Account)}
         */
        @Deprecated
        public LogoutHandler(Context context, String accountType) {
            mContext = context;
            mAccountType = accountType;
            mAccount = null;

        }

        public LogoutHandler(Context context, Account account) {
            mContext = context;
            mAccount = account;
            mAccountType = null;
        }

        private Account getAccount() {
            return mAccount != null ? mAccount : AbsLocastAuthenticator.getFirstAccount(mContext,
                    mAccountType);
        }

        @Override
        public void onClick(DialogInterface dialog, int which) {
            switch (which) {
                case AlertDialog.BUTTON_POSITIVE:

                    AccountManager.get(mContext).removeAccount(getAccount(),
                            mAccountManagerCallback,
                            null);
                    break;
            }
        }

        /**
         * This will be called after the account removal completes. If there is an error, success
         * will be false.
         *
         * @param success
         *            true if the account was successfully removed.
         */
        public abstract void onAccountRemoved(boolean success);

    };

    /**
     * Given a logout handler and information about the app, create a standard logout dialog box
     * that prompts the user if they want to logout.
     *
     * @param context
     * @param appName
     *            the name of your app. This is integrated into the text using the
     *            {@code auth_logout_title} and {@code auth_logout_message} string resources.
     * @param onLogoutHandler
     * @return
     */
    public static Dialog createLogoutDialog(Context context, CharSequence appName,
            LogoutHandler onLogoutHandler) {

        final AlertDialog.Builder b = new AlertDialog.Builder(context);
        b.setTitle(context.getString(R.string.auth_logout_title, appName));
        b.setMessage(context.getString(R.string.auth_logout_message, appName));
        b.setCancelable(true);
        b.setPositiveButton(R.string.auth_logout, onLogoutHandler);
        b.setNegativeButton(android.R.string.cancel, null);

        return b.create();
    }
}