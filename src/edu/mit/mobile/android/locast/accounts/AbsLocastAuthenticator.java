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

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import edu.mit.mobile.android.locast.net.NetworkClient;
import edu.mit.mobile.android.locast.net.NetworkProtocolException;

/**
 * This class is an implementation of AbstractAccountAuthenticator for
 * authenticating accounts in the Locast domain
 */
public abstract class AbsLocastAuthenticator extends AbstractAccountAuthenticator {
    private final static String TAG = AbsLocastAuthenticator.class.getSimpleName();
    // Authentication Service context

    public static final String PREF_SKIP_AUTH = "skip_authentication";

    private final Context mContext;

    public AbsLocastAuthenticator(Context context) {
        super(context);
        mContext = context;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response,
        String accountType, String authTokenType, String[] requiredFeatures,
        Bundle options) {
        final Intent intent = getAuthenticator(mContext);
        intent.putExtra(AbsLocastAuthenticatorActivity.EXTRA_AUTHTOKEN_TYPE,
            authTokenType);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
            response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    public static boolean hasRealAccount(Context context){
        final Account[] accounts = getAccounts(context);
        final boolean hasRealAccount = accounts.length > 0;

        return hasRealAccount;
    }

    public static Account[] getAccounts(Context context){
        return AccountManager.get(context).getAccountsByType(AbsLocastAuthenticationService.ACCOUNT_TYPE);
    }

    /**
     * @param context
     * @return an intent that would launch the appropriate {@link AbsLocastAuthenticatorActivity}.
     *         All the extras will be populated for you.
     */
    public abstract Intent getAuthenticator(Context context);

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response,
        Account account, Bundle options) {
        if (options != null && options.containsKey(AccountManager.KEY_PASSWORD)) {
            final String password =
                options.getString(AccountManager.KEY_PASSWORD);
            final Bundle verified = onlineConfirmPassword(account, password);
            final Bundle result = new Bundle();
            result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, verified != null);
            return result;
        }
        // Launch AuthenticatorActivity to confirm credentials
        final Intent intent = getAuthenticator(mContext);
        intent.putExtra(AbsLocastAuthenticatorActivity.EXTRA_USERNAME, account.name);
        intent.putExtra(AbsLocastAuthenticatorActivity.EXTRA_CONFIRMCREDENTIALS, true);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
            response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response,
        String accountType) {
        throw new UnsupportedOperationException();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response,
        Account account, String authTokenType, Bundle loginOptions) {
        if (!authTokenType.equals(AbsLocastAuthenticationService.AUTHTOKEN_TYPE)) {
            final Bundle result = new Bundle();
            result.putString(AccountManager.KEY_ERROR_MESSAGE,
                "invalid authTokenType");
            return result;
        }
        final AccountManager am = AccountManager.get(mContext);
        final String password = am.getPassword(account);
        if (password != null) {
            final Bundle accountData = onlineConfirmPassword(account, password);
            if (accountData != null) {
                final Bundle result = new Bundle();

                result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
                result.putString(AccountManager.KEY_ACCOUNT_TYPE,
                        AbsLocastAuthenticationService.ACCOUNT_TYPE);
                result.putString(AccountManager.KEY_AUTHTOKEN, password);
                return result;
            }
        }
        // the password was missing or incorrect, return an Intent to an
        // Activity that will prompt the user for the password.
        final Intent intent = getAuthenticator(mContext);
        intent.putExtra(AbsLocastAuthenticatorActivity.EXTRA_USERNAME, account.name);
        intent.putExtra(AbsLocastAuthenticatorActivity.EXTRA_AUTHTOKEN_TYPE,
            authTokenType);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE,
            response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response,
        Account account, String[] features) {
        final Bundle result = new Bundle();
        result.putBoolean(AccountManager.KEY_BOOLEAN_RESULT, false);
        return result;
    }

    /**
     * Validates user's password on the server
     */
    private Bundle onlineConfirmPassword(Account account, String password) {
        Bundle response = null;
        try {
            response = NetworkClient.authenticate(mContext, account, password);

        } catch (final IOException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        } catch (final JSONException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        } catch (final NetworkProtocolException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
        return response;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response,
        Account account, String authTokenType, Bundle loginOptions) {
        final Intent intent = getAuthenticator(mContext);
        intent.putExtra(AbsLocastAuthenticatorActivity.EXTRA_USERNAME, account.name);
        intent.putExtra(AbsLocastAuthenticatorActivity.EXTRA_AUTHTOKEN_TYPE,
            authTokenType);
        intent.putExtra(AbsLocastAuthenticatorActivity.EXTRA_CONFIRMCREDENTIALS, false);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    public static String getUserUri(Context context){
        final Account[] accounts = AbsLocastAuthenticator.getAccounts(context);
        if (accounts.length > 0){
            return AccountManager.get(context).getUserData(accounts[0], AbsLocastAuthenticationService.USERDATA_USER_URI);
        }
        return null;
    }

    public static Account getFirstAccount(Context context){
        final Account[] accounts = AbsLocastAuthenticator.getAccounts(context);
        if (accounts.length > 0){
            return accounts[0];
        }
        return null;
    }

    public static String getUserData(Context context, String key){
        final Account account = getFirstAccount(context);
        if (account == null){
            throw new RuntimeException("no accounts registered");
        }
        return AccountManager.get(context).getUserData(account, key);
    }
}
