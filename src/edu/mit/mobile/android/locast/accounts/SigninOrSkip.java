package edu.mit.mobile.android.locast.accounts;
/*
 * Copyright (C) 2011  MIT Mobile Experience Lab
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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
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

    public static final String PREF_HAVE_RUN_BEFORE = "have_run_before";

    private boolean mSkipIsCancel = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.auth_signin_or_skip);

        findViewById(R.id.sign_in).setOnClickListener(this);
        final Button skip = (Button) findViewById(R.id.skip);
        skip.setOnClickListener(this);

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
        if (v.getId() == R.id.sign_in) {
            startActivityForResult(new Intent(this, AuthenticatorActivity.class), REQUEST_SIGNIN);
        } else if (v.getId() == R.id.skip) {
            skip();
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
        if (Constants.USE_ACCOUNT_FRAMEWORK) {
            return Authenticator.getAccounts(context).length == 0;
        } else {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            final boolean runBefore = prefs.getBoolean(PREF_HAVE_RUN_BEFORE, false);
            if (!runBefore) {
                prefs.edit().putBoolean(PREF_HAVE_RUN_BEFORE, true).commit();
            }
            return !runBefore;
        }
    }

    /**
     *
     * Calls context.startActivityForResult() with the appropriate intent
     *
     * @param context
     * @param requestCode
     */
    public static final boolean startSignin(Activity context, int requestCode) {
        if (!Constants.USE_ACCOUNT_FRAMEWORK) {
            return true;

        } else if (Constants.REQUIRE_LOGIN) {
            if (!Authenticator.hasRealAccount(context)) {
                context.startActivityForResult(new Intent(context, AuthenticatorActivity.class),
                        requestCode);
                return true;
            }
            return false;
            // login isn't required, but accounts can be created.
        } else if (Constants.CAN_CREATE_CASTS) {
            if (!Authenticator.isDemoMode(context) && !Authenticator.hasRealAccount(context)) {
                context.startActivityForResult(new Intent(context, SigninOrSkip.class), requestCode);
                return true;
            }
            return false;
        } else {
            // we shouldn't do anything if there's no possibility of creating casts.
            if (checkFirstTime(context)) {
                Authenticator.addDemoAccount(context);
            }
            return false;
        }
    }
}
