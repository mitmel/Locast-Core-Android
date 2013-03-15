package edu.mit.mobile.android.locast.accounts;

import android.accounts.Account;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticatorActivity.LogoutHandler;

public class LogoutFragment extends DialogFragment {

    public static final String ARG_APP_NAME = "app_name";

    private LogoutHandler mLogoutHandler;

    public static final LogoutFragment instantiate(CharSequence appName) {
        final Bundle b = new Bundle();
        b.putCharSequence(ARG_APP_NAME, appName);

        final LogoutFragment f = new LogoutFragment();
        f.setArguments(b);

        return f;
    }

    private LogoutHandler mWrappedLogoutHandler;
    private CharSequence mAppName;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final Bundle b = getArguments();

        mAppName = b.getCharSequence(ARG_APP_NAME);

        mLogoutHandler = new LogoutHandler(getActivity()) {

            @Override
            public void onAccountRemoved(boolean success) {
                mWrappedLogoutHandler.onAccountRemoved(success);
            }

            @Override
            public void setAccount(Account account) {
                mWrappedLogoutHandler.setAccount(account);
            }

            @Override
            public void onClick(DialogInterface dialog, int which) {
                mWrappedLogoutHandler.onClick(dialog, which);
            }
        };
    }

    public LogoutFragment setOnLogoutHandler(LogoutHandler logoutHandler) {
        mWrappedLogoutHandler = logoutHandler;

        return this;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return AbsLocastAuthenticatorActivity.createLogoutDialog(getActivity(), mAppName,
                mLogoutHandler);
    }

}
