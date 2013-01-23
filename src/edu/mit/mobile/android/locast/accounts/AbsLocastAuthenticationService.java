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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * Service to handle Account authentication. It instantiates the authenticator and returns its
 * IBinder.
 */
public abstract class AbsLocastAuthenticationService extends Service {
    private AbsLocastAuthenticator mAbsLocastAuthenticator;

    public static final String USERDATA_USERID = "id";

    public static final String USERDATA_DISPLAY_NAME = "display_name";

    public static final String USERDATA_USER_URI = "uri";

    public static final String USERDATA_LOCAST_API_URL = "locast_url";

    @Override
    public void onCreate() {
        mAbsLocastAuthenticator = getAuthenticator(this);
    }

    protected abstract AbsLocastAuthenticator getAuthenticator(
            AbsLocastAuthenticationService service);

    @Override
    public void onDestroy() {
        mAbsLocastAuthenticator = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mAbsLocastAuthenticator.getIBinder();
    }
}
