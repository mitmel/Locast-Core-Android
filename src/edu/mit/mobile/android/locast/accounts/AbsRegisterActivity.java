package edu.mit.mobile.android.locast.accounts;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONException;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import edu.mit.mobile.android.locast.R;
import edu.mit.mobile.android.locast.net.ClientResponseException;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

/**
 * <p>
 * A generic registration form, so that users can create a new account.
 * </p>
 *
 * <p>
 * This activity should be started with {@link Activity#startActivityForResult(Intent, int)} if you
 * wish to get the result of registration. The data passed back is a Bundle version of the JSON data
 * returned from the server. This generally includes the username, password, user URL, ID, and
 * display name.
 * </p>
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 *
 */
public abstract class AbsRegisterActivity extends FragmentActivity {

    private static final String TAG = AbsRegisterActivity.class.getSimpleName();
    private EditText mPassword;
    private EditText mPasswordConfirm;
    private EditText mYourName;
    private EditText mEmail;
    private EditText mUsername;
    private Button mRegister;

    @Override
    protected void onCreate(Bundle arg0) {
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        super.onCreate(arg0);
        setContentView(getContentView());

        initContentView();
        onContentViewCreated();
    }

    protected int getContentView() {
        return R.layout.activity_register;
    }

    protected void initContentView() {
        mYourName = (EditText) findViewById(R.id.your_name);
        mEmail = (EditText) findViewById(R.id.email_address);
        mUsername = (EditText) findViewById(R.id.username);
        mPassword = (EditText) findViewById(R.id.password);
        mPasswordConfirm = (EditText) findViewById(R.id.password_confirm);
        mRegister = (Button) findViewById(R.id.register);
    }

    protected abstract CharSequence getAppName();

    protected void onContentViewCreated() {
        final CharSequence appName = getAppName();

        setTitle(getString(R.string.auth_register_title, appName));

        final TextView usernameLabel = (TextView) findViewById(R.id.username_label);
        usernameLabel.setText(getString(R.string.username_label, appName));

        mYourName.addTextChangedListener(mTextWatcher);
        mEmail.addTextChangedListener(mTextWatcher);
        mUsername.addTextChangedListener(mUsernameTextWatcher);

        mRegister.setOnClickListener(mOnClickListener);
    }

    protected CharSequence getYourName() {
        return mYourName.getText();
    }

    protected void setYourNameError(CharSequence errorMessage) {
        mYourName.setError(errorMessage);
        mYourName.requestFocus();
    }

    protected CharSequence getEmail() {
        return mEmail.getText();
    }

    protected void setEmailError(CharSequence errorMessage) {
        mEmail.setError(errorMessage);
        mEmail.requestFocus();
    }

    protected CharSequence getUsername() {
        return mUsername.getText();
    }

    protected void setUsername(CharSequence username) {
        mChangingUsername = true;
        mUsername.setText(username);
        mChangingUsername = false;
    }

    protected void setUsernameError(CharSequence errorMessage) {
        mUsername.setError(errorMessage);
        mUsername.requestFocus();
    }

    protected CharSequence getPassword() {
        return mPassword.getText();
    }

    protected void setPasswordError(CharSequence errorMessage) {
        mPassword.setError(errorMessage);
        mPassword.requestFocus();
    }

    protected CharSequence getPasswordConfirm() {
        return mPasswordConfirm.getText();
    }

    protected void setPasswordConfirmError(CharSequence errorMessage) {
        mPasswordConfirm.setError(errorMessage);
        mPasswordConfirm.requestFocus();
    }

    private static Pattern NAME_TO_USERNAME = Pattern.compile("\\s*(\\w+)\\s+(\\w)\\w+\\s*");
    private static Pattern EMAIL_TO_USERNAME = Pattern.compile("\\s*([\\w-._]+)@.+\\..+");

    protected String generateUsername(CharSequence yourName, CharSequence email) {
        String username;
        Matcher m = NAME_TO_USERNAME.matcher(yourName);
        if (m.matches()) {
            username = m.group(1).toLowerCase() + m.group(2).toLowerCase();
        } else {
            m = EMAIL_TO_USERNAME.matcher(email);
            if (m.matches()) {
                username = m.group(1);
            } else {
                username = "";
            }
        }

        return username;
    }

    private final TextWatcher mTextWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!mUserEditedUsername) {
                setUsername(generateUsername(getYourName(), getEmail()));
            }
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void afterTextChanged(Editable s) {
        }
    };

    private final TextWatcher mUsernameTextWatcher = new TextWatcher() {

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {

        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
            if (!mChangingUsername) {
                mUserEditedUsername = true;
            }
        }

        @Override
        public void afterTextChanged(Editable s) {
        }

    };

    private boolean mChangingUsername = false;
    private boolean mUserEditedUsername = false;

    protected boolean validateForm() {
        final CharSequence yourName = trimEntry(getYourName());
        if (yourName.length() == 0) {
            setYourNameError(getString(R.string.register_message_empty_name));
            return false;
        }

        final CharSequence email = trimEntry(getEmail());

        if (email.length() == 0) {
            setEmailError(getString(R.string.register_message_empty_email_address));
            return false;
        }

        final CharSequence username = trimEntry(getUsername());

        if (username.length() == 0) {
            setUsernameError(getString(R.string.register_message_empty_username));
            return false;
        }

        final String password = getPassword().toString();
        final String passwordConfirm = getPasswordConfirm().toString();

        if (password.length() == 0) {
            setPasswordError(getString(R.string.register_message_empty_password));
            return false;
        }

        if (!passwordConfirm.equals(password)) {
            setPasswordConfirmError(getString(R.string.register_message_password_mismatch));
            return false;
        }

        return true;
    }

    private final OnClickListener mOnClickListener = new OnClickListener() {

        @Override
        public void onClick(View v) {
            final int id = v.getId();
            if (R.id.register == id) {
                register();
            }
        }
    };

    private String trimEntry(CharSequence text) {
        return text.toString().trim();
    }

    private void register() {
        if (!validateForm()) {
            return;
        }

        new RegisterTask(this).execute();
    }

    private String getErrorText(Bundle data, String key) {
        return TextUtils.join("\\n", data.getStringArray(key));
    }

    /**
     * Extend this to handle client errors. These errors should be recoverable by the user (or by
     * fixing a programming bug), so they're handled different from network errors.
     *
     * @param e
     */
    protected void onRegisterError(ClientResponseException e) {
        final Bundle data = e.getData();
        if (data != null) {
            Log.d(TAG, "Server response: " + e.getData().toString());
            if (data.containsKey(NetworkClient.SERVER_KEY_EMAIL)) {
                setEmailError(getErrorText(data, NetworkClient.SERVER_KEY_EMAIL));
            }
            if (data.containsKey(NetworkClient.SERVER_KEY_USERNAME)) {
                setUsernameError(getErrorText(data, NetworkClient.SERVER_KEY_USERNAME));
            }
            if (data.containsKey(NetworkClient.SERVER_KEY_PASSWORD)) {
                setPasswordError(getErrorText(data, NetworkClient.SERVER_KEY_PASSWORD));
            }
        } else {
            Toast.makeText(this,
                    getString(R.string.auth_error_network_protocol_error, e.getLocalizedMessage()),
                    Toast.LENGTH_LONG).show();
            Log.e(TAG, "Client error on registration", e);
        }
    }

    /**
     * Extend this to handle network errors. The client responds with {@link IOException},
     * {@link NetworkProtocolException}, and {@link JSONException} here. The default implementation
     * shows a {@link Toast} with a generic error and logs the error to the system log.
     *
     * @param e
     */
    protected void onNetworkError(Exception e) {
        Log.e(TAG, "Network error while registering", e);

        if (e instanceof JSONException) {
            Toast.makeText(this, R.string.auth_error_server_returned_invalid_data,
                    Toast.LENGTH_LONG).show();

        } else if (e instanceof NetworkProtocolException) {
            Toast.makeText(this,
                    getString(R.string.auth_error_network_protocol_error, e.getLocalizedMessage()),
                    Toast.LENGTH_LONG).show();

        } else {
            Toast.makeText(this, R.string.auth_error_could_not_contact_server, Toast.LENGTH_LONG)
                    .show();
        }
    }

    protected void onRegisterComplete(Bundle result) {
        final Intent intent = new Intent();

        // TODO this method of passing data between apps is insecure. Find a better solution.
        // http://stackoverflow.com/questions/7647750/how-to-secure-intent-data-while-sending-it-across-applications
        intent.putExtras(result);

        setResult(RESULT_OK, intent);
        finish();

    }

    private class RegisterTask extends AsyncTask<Void, Integer, Bundle> {

        private final Context mContext;

        private Exception mException;

        public RegisterTask(Context context) {
            mContext = context;
        }

        @Override
        protected void onPreExecute() {
            setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected Bundle doInBackground(Void... params) {
            final NetworkClient nc = NetworkClient.getInstance(mContext, null);
            try {
                final String username = trimEntry(getUsername());
                final String password = getPassword().toString();

                final Bundle result = nc.register(username, trimEntry(getYourName()),
                        trimEntry(getEmail()), password);
                result.putString(AbsLocastAuthenticatorActivity.EXTRA_USERNAME, username);
                result.putString(AbsLocastAuthenticatorActivity.EXTRA_PASSWORD, password);
                return result;

            } catch (final JSONException e) {
                mException = e;
                return null;
            } catch (final NetworkProtocolException e) {
                mException = e;
                return null;
            } catch (final ClientResponseException e) {
                mException = e;
                return null;
            } catch (final IOException e) {
                mException = e;
                return null;
            }
        }

        @Override
        protected void onPostExecute(Bundle result) {
            setProgressBarIndeterminateVisibility(false);
            if (result != null) {
                onRegisterComplete(result);
            } else {
                if (mException == null) {
                    return;
                }
                if (mException instanceof ClientResponseException) {
                    onRegisterError((ClientResponseException) mException);
                } else {
                    onNetworkError(mException);
                }
            }
        }
    }
}
