package edu.mit.mobile.android.locast.net;

/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
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
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.Vector;

import org.apache.http.HttpEntity;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpRequestInterceptor;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.AuthState;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import edu.mit.mobile.android.locast.Constants;
import edu.mit.mobile.android.locast.accounts.AbsLocastAuthenticationService;
import edu.mit.mobile.android.locast.data.NoPublicPath;
import edu.mit.mobile.android.locast.data.Titled;
import edu.mit.mobile.android.locast.notifications.ProgressNotification;
import edu.mit.mobile.android.locast.sync.SyncableProvider;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.utils.StreamUtils;

/**
 * An client implementation of the JSON RESTful API for the Locast project.
 *
 * @author stevep
 */
public class NetworkClient extends DefaultHttpClient {
    private static final String TAG = NetworkClient.class.getSimpleName();
    public final static String JSON_MIME_TYPE = "application/json";

    private static final boolean DEBUG = Constants.DEBUG;

    private final static String PATH_PAIR = "pair/", PATH_UNPAIR = "un-pair/",
            PATH_USER = "user/me";

    protected URI mBaseUrl;
    // one of the formats from ISO 8601
    public final static SimpleDateFormat dateFormat = new SimpleDateFormat(
            "yyyy-MM-dd'T'HH:mm:ss'Z'");

    private AuthScope mAuthScope;

    protected final Context mContext;

    protected final static HttpRequestInterceptor PREEMPTIVE_AUTH = new HttpRequestInterceptor() {
        public void process(final HttpRequest request, final HttpContext context)
                throws HttpException, IOException {

            final AuthState authState = (AuthState) context
                    .getAttribute(ClientContext.TARGET_AUTH_STATE);
            final CredentialsProvider credsProvider = (CredentialsProvider) context
                    .getAttribute(ClientContext.CREDS_PROVIDER);
            final HttpHost targetHost = (HttpHost) context
                    .getAttribute(ExecutionContext.HTTP_TARGET_HOST);

            // If not auth scheme has been initialized yet
            if (authState.getAuthScheme() == null) {
                final AuthScope authScope = new AuthScope(targetHost.getHostName(),
                        targetHost.getPort());
                // Obtain credentials matching the target host
                final Credentials creds = credsProvider.getCredentials(authScope);
                // If found, generate BasicScheme preemptively
                if (creds != null) {
                    if (creds.getUserPrincipal() != null) {
                        if (DEBUG) {
                            Log.d("NetworkClient", "Pre-emptively authenticating as: "
                                    + creds.getUserPrincipal().getName());
                        }
                    }
                    authState.setAuthScheme(new BasicScheme());
                    authState.setCredentials(creds);
                }
            }
        }
    };

    /**
     * Adds an accept-language header based on the user's current locale.
     */
    protected final static HttpRequestInterceptor ACCEPT_LANGUAGE = new HttpRequestInterceptor() {

        @Override
        public void process(HttpRequest request, HttpContext context) throws HttpException,
                IOException {
            final Locale locale = Locale.getDefault();
            final String language = locale.getLanguage();
            if (DEBUG) {
                Log.d(TAG, "added header Accept-Language: " + language);
            }
            request.addHeader("Accept-Language", language);

        }
    };

    protected final static HttpRequestInterceptor REMOVE_EXPECTATIONS = new HttpRequestInterceptor() {

        public void process(HttpRequest request, HttpContext context) throws HttpException,
                IOException {
            if (request.containsHeader("Expect")) {
                request.removeHeader(request.getFirstHeader("Expect"));
            }
        }
    };

    public static final String PREF_SERVER_URL = "server_url";

    public static final String PREF_LOCAST_SITE = "locast_site";

    /**
     * Create a new NetworkClient, authenticating with the given account.
     *
     * @param context
     * @param account
     */
    public NetworkClient(Context context, Account account) {
        super();
        this.mContext = context;

        initClient();

        loadFromExistingAccount(account);
    }

    public NetworkClient(Context context) {
        super();
        this.mContext = context;

        initClient();

        loadWithoutAccount();
    }

    /**
     * Create a new NetworkClient using the baseUrl. You will need to call
     * {@link #setCredentials(Credentials)} at some point if you want authentication.
     *
     * @param context
     * @param baseUrl
     * @throws MalformedURLException
     */
    private NetworkClient(Context context, String baseUrl) throws MalformedURLException {
        super();
        this.mContext = context;

        initClient();

        setBaseUrl(baseUrl);
    }

    protected void initClient() {
        this.addRequestInterceptor(PREEMPTIVE_AUTH, 0);
        this.addRequestInterceptor(ACCEPT_LANGUAGE, 1);
    }

    @Override
    protected HttpParams createHttpParams() {
        final HttpParams params = super.createHttpParams();

        // from AndroidHttpClient:
        // Turn off stale checking. Our connections break all the time anyway,
        // and it's not worth it to pay the penalty of checking every time.
        HttpConnectionParams.setStaleCheckingEnabled(params, false);

        // Default connection and socket timeout of 20 seconds. Tweak to taste.
        HttpConnectionParams.setConnectionTimeout(params, 20 * 1000);
        HttpConnectionParams.setSoTimeout(params, 20 * 1000);
        HttpConnectionParams.setSocketBufferSize(params, 8192);
        HttpProtocolParams.setUseExpectContinue(params, true);

        String appVersion = "unknown";
        CharSequence appName = "OpenLocast";

        try {
            final PackageInfo pkg = mContext.getPackageManager().getPackageInfo(
                    mContext.getPackageName(), 0);
            appName = pkg.applicationInfo.loadLabel(mContext.getPackageManager());
            appVersion = pkg.versionName;
        } catch (final NameNotFoundException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }

        final String userAgent = appName + "/" + appVersion;

        // Set the specified user agent and register standard protocols.
        HttpProtocolParams.setUserAgent(params, userAgent);

        return params;
    }

    @Override
    protected ClientConnectionManager createClientConnectionManager() {
        final SchemeRegistry registry = new SchemeRegistry();
        registry.register(new Scheme("http", PlainSocketFactory.getSocketFactory(), 80));
        registry.register(new Scheme("https", SSLSocketFactory.getSocketFactory(), 443));
        return new ThreadSafeClientConnManager(getParams(), registry);
    }

    /************************* credentials and pairing **********************/

    public static NetworkClient getInstance(Context context, Account account) {
        if (account == null) {
            return new NetworkClient(context);
        } else {
            return new NetworkClient(context, account);
        }
    }

    public static String getBaseUrlFromPreferences(Context context) {
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        return prefs.getString(NetworkClient.PREF_SERVER_URL,
                context.getString(R.string.default_api_url));

    }

    private void setBaseUrl(String baseUrlString) throws MalformedURLException {
        final URL baseUrl = new URL(baseUrlString);
        try {
            mBaseUrl = baseUrl.toURI();

            mAuthScope = new AuthScope(mBaseUrl.getHost(), mBaseUrl.getPort());

        } catch (final URISyntaxException e) {
            final MalformedURLException me = new MalformedURLException(e.getLocalizedMessage());
            me.initCause(e);
            throw me;
        }
    }

    /**
     * initializes
     */
    protected synchronized void loadWithoutAccount() {
        final String baseUrlString = getBaseUrlFromPreferences(mContext);

        try {
            setBaseUrl(baseUrlString);

        } catch (final MalformedURLException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);
        }
    }

    protected synchronized void loadFromExistingAccount(Account account) {
        if (account == null) {
            throw new IllegalArgumentException("must specify account");
        }

        String baseUrlString;

        final AccountManager am = AccountManager.get(mContext);
        baseUrlString = am.getUserData(account, AbsLocastAuthenticationService.USERDATA_LOCAST_API_URL);
        if (baseUrlString == null) {
            Log.w(TAG, "loading base URL from preferences instead of account metadata");
            baseUrlString = getBaseUrlFromPreferences(mContext);
            // if it's null in the userdata, then it must be an account from before this feature
            // was added.
            // Store for later use.
            am.setUserData(account, AbsLocastAuthenticationService.USERDATA_LOCAST_API_URL, baseUrlString);
        }

        try {
            setBaseUrl(baseUrlString);

            setCredentialsFromAccount(account);

        } catch (final MalformedURLException e) {
            Log.e(TAG, e.getLocalizedMessage(), e);

        }
    }

    public void setCredentialsFromAccount(Account account) {
        final AccountManager am = AccountManager.get(mContext);

        setCredentials(account.name, am.getPassword(account));
    }

    public String getUsername() {
        String username = null;
        final Credentials credentials = getCredentialsProvider().getCredentials(mAuthScope);
        if (credentials != null) {
            username = credentials.getUserPrincipal().getName();
        }
        return username;
    }

    /**
     * Set the login credentials.
     *
     * @param credentials
     */
    protected void setCredentials(String username, String auth_secret) {
        this.setCredentials(new UsernamePasswordCredentials(username, auth_secret));
    }

    /**
     * Set the login credentials. Most often you will want to pass in a
     * UsernamePasswordCredentials() object.
     *
     * @param credentials
     */
    protected void setCredentials(Credentials credentials) {

        this.getCredentialsProvider().clear();
        if (credentials != null) {
            this.getCredentialsProvider().setCredentials(mAuthScope, credentials);
        }
    }

    public boolean isAuthenticated() {
        return getUsername() != null;
    }

    /**
     * @param context
     * @param account
     *            an existing account which contains a userdata key of
     *            {@link AbsLocastAuthenticationService#USERDATA_LOCAST_API_URL} which specifies the URL to
     *            use.
     * @param password
     * @return a Bundle containing the user's profile or null if authentication failed.
     * @throws IOException
     * @throws JSONException
     * @throws NetworkProtocolException
     */
    public static Bundle authenticate(Context context, Account account, String password)
            throws IOException, JSONException, NetworkProtocolException {
        return authenticate(context, new NetworkClient(context, account), account.name, password);

    }

    /**
     * @param context
     * @param baseUrl
     * @param username
     * @param password
     * @return
     * @throws IOException
     * @throws JSONException
     * @throws NetworkProtocolException
     */
    public static Bundle authenticate(Context context, String baseUrl, String username,
            String password) throws IOException, JSONException, NetworkProtocolException {
        return authenticate(context, new NetworkClient(context, baseUrl), username, password);

    }

    private static Bundle authenticate(Context context, NetworkClient nc, String username,
            String password) throws IOException, JSONException, NetworkProtocolException {

        nc.setCredentials(username, password);

        boolean authenticated = false;
        try {
            final HttpResponse res = nc.get(PATH_USER);

            authenticated = nc.checkStatusCode(res, false);

            final HttpEntity ent = res.getEntity();
            JSONObject jo = null;

            if (authenticated) {
                jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
                ent.consumeContent();
            } else {
                jo = null;
            }

            final Bundle userData = jsonObjectToBundle(jo, true);
            userData.putString(AbsLocastAuthenticationService.USERDATA_LOCAST_API_URL, nc.getBaseUrl());
            return userData;

        } catch (final HttpResponseException e) {
            if (e.getStatusCode() == HttpStatus.SC_UNAUTHORIZED) {
                return null;
            } else {
                throw e;
            }
        }
    }

    public static Bundle jsonObjectToBundle(JSONObject jsonObject, boolean allStrings) {
        final Bundle b = new Bundle();
        for (@SuppressWarnings("unchecked")
        final Iterator<String> i = jsonObject.keys(); i.hasNext();) {
            final String key = i.next();
            final Object value = jsonObject.opt(key);
            if (value == null) {
                b.putSerializable(key, null);

            } else if (allStrings) {
                b.putString(key, String.valueOf(value));

            } else if (value instanceof String) {
                b.putString(key, (String) value);

            } else if (value instanceof Integer) {
                b.putInt(key, (Integer) value);
            }
        }
        return b;
    }

    /**
     * Makes a request to pair the device with the server. The server sends back a set of
     * credentials which are then stored for making further queries.
     *
     * @param pairCode
     *            the unique code that is provided by the server.
     * @return true if pairing process was successful, otherwise false.
     * @throws IOException
     * @throws JSONException
     * @throws RecordStoreException
     * @throws NetworkProtocolException
     */
    public boolean pairDevice(String pairCode) throws IOException, JSONException,
            NetworkProtocolException {
        final DefaultHttpClient hc = new DefaultHttpClient();
        hc.addRequestInterceptor(REMOVE_EXPECTATIONS);
        final HttpPost r = new HttpPost(getFullUrlAsString(PATH_PAIR));

        final List<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
        parameters.add(new BasicNameValuePair("auth_secret", pairCode));
        r.setEntity(new UrlEncodedFormEntity(parameters));

        r.setHeader("Content-Type", URLEncodedUtils.CONTENT_TYPE);
        final HttpResponse c = hc.execute(r);

        checkStatusCode(c, false);

        // final JSONObject creds = toJsonObject(c);

        return true;
    }

    /**
     * Requests that the device be unpaired with the server.
     *
     * @return true if successful, false otherwise.
     * @throws IOException
     * @throws NetworkProtocolException
     * @throws RecordStoreException
     */
    public boolean unpairDevice() throws IOException, NetworkProtocolException {
        final HttpPost r = new HttpPost(getFullUrlAsString(PATH_UNPAIR));

        final HttpResponse c = this.execute(r);
        checkStatusCode(c, false);

        if (c.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {

            return true;
        } else {
            return false;
        }
    }

    /*************************** all request methods ******************/

    public HttpResponse head(String path) throws IOException, JSONException,
            NetworkProtocolException {
        final String fullUrl = getFullUrlAsString(path);
        if (DEBUG) {
            Log.d(TAG, "HEAD " + fullUrl);
        }
        final HttpHead req = new HttpHead(fullUrl);

        return this.execute(req);
    }

    /**
     * Given a HttpResponse, checks that the return types are all correct and returns a JSONObject
     * from the response.
     *
     * Fully consumes the content.
     *
     * @param res
     * @return the full response body as a JSONObject
     * @throws IllegalStateException
     * @throws IOException
     * @throws NetworkProtocolException
     * @throws JSONException
     */
    public static JSONObject toJsonObject(HttpResponse res) throws IllegalStateException,
            IOException, NetworkProtocolException, JSONException {
        checkContentType(res, JSON_MIME_TYPE, false);

        final HttpEntity ent = res.getEntity();
        final JSONObject jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
        ent.consumeContent();
        return jo;

    }

    /**
     * Verifies that the HttpResponse has a good status code. Throws exceptions if they are not.
     *
     * @param res
     * @param createdOk
     *            true if a 201 (CREATED) code is ok. Otherwise, only 200 (OK) is allowed.
     * @throws HttpResponseException
     *             if the status code is 400 series.
     * @throws NetworkProtocolException
     *             for all status codes errors.
     * @throws IOException
     * @return returns true upon success or throws an exception explaining what went wrong.
     */
    public boolean checkStatusCode(HttpResponse res, boolean createdOk)
            throws HttpResponseException, NetworkProtocolException, IOException {
        final int statusCode = res.getStatusLine().getStatusCode();
        if (statusCode == HttpStatus.SC_OK || (createdOk && statusCode == HttpStatus.SC_CREATED)) {
            return true;
        } else if (statusCode >= HttpStatus.SC_BAD_REQUEST
                && statusCode < HttpStatus.SC_INTERNAL_SERVER_ERROR) {
            final HttpEntity e = res.getEntity();
            if (e.getContentType().getValue().equals("text/html") || e.getContentLength() > 40) {
                logDebug("Got long response body. Not showing.");
            } else {
                logDebug(StreamUtils.inputStreamToString(e.getContent()));
            }
            e.consumeContent();
            throw new HttpResponseException(statusCode, res.getStatusLine().getReasonPhrase());
        } else {
            final HttpEntity e = res.getEntity();
            if (e.getContentType().getValue().equals("text/html") || e.getContentLength() > 40) {
                logDebug("Got long response body. Not showing.");
            } else {
                logDebug(StreamUtils.inputStreamToString(e.getContent()));
            }
            e.consumeContent();
            throw new NetworkProtocolException("HTTP " + res.getStatusLine().getStatusCode() + " "
                    + res.getStatusLine().getReasonPhrase(), res);
        }
    }

    /**
     * Verifies that the HttpResponse has a correct content type. Throws exceptions if not.
     *
     * @param res
     * @param contentType
     * @param exact
     *            If false, will only check to see that the result content type starts with the
     *            desired contentType.
     * @return
     * @throws NetworkProtocolException
     * @throws IOException
     */
    private static boolean checkContentType(HttpResponse res, String contentType, boolean exact)
            throws NetworkProtocolException, IOException {
        final String resContentType = res.getFirstHeader("Content-Type").getValue();
        if (!(exact ? resContentType.equals(contentType) : resContentType.startsWith(contentType))) {
            throw new NetworkProtocolException("Did not return content-type '" + contentType
                    + "'. Got: '" + resContentType + "'", res);
        }
        return true;
    }

    /************************************ GET *******************************/
    /**
     * Gets an object and verifies that it got a successful response code.
     *
     * @param path
     * @return
     * @throws IOException
     * @throws JSONException
     * @throws NetworkProtocolException
     * @throws HttpResponseException
     */
    public HttpResponse get(String path) throws IOException, JSONException,
            NetworkProtocolException, HttpResponseException {
        final String fullUri = getFullUrlAsString(path);
        final HttpGet req = new HttpGet(fullUri);
        if (DEBUG) {
            Log.d("NetworkClient", "GET " + fullUri);
        }
        final HttpResponse res = this.execute(req);

        checkStatusCode(res, false);

        return res;
    }

    /**
     * Loads a JSON object from the given URI
     *
     * @param path
     * @return
     * @throws IOException
     * @throws JSONException
     * @throws NetworkProtocolException
     */
    public JSONObject getObject(String path) throws IOException, JSONException,
            NetworkProtocolException {
        final HttpEntity ent = getJson(path);
        final JSONObject jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
        ent.consumeContent();
        return jo;
    }

    /**
     * GETs a JSON Array
     *
     * @param path
     * @return
     * @throws IOException
     * @throws JSONException
     * @throws NetworkProtocolException
     */
    public JSONArray getArray(String path) throws IOException, JSONException,
            NetworkProtocolException {

        final HttpEntity ent = getJson(path);
        final JSONArray ja = new JSONArray(StreamUtils.inputStreamToString(ent.getContent()));
        ent.consumeContent();
        return ja;
    }

    private synchronized HttpEntity getJson(String path) throws IOException, JSONException,
            NetworkProtocolException {

        final HttpResponse res = get(path);

        checkContentType(res, JSON_MIME_TYPE, false);

        // XXX possibly untrue isAuthenticated = true; // this should only get set if we managed to
        // make it here

        return res.getEntity();
    }

    /***************************** PUT ***********************************/

    /**
     * PUTs a JSON object, returns an updated JSON object.
     *
     * @param path
     * @param jsonObject
     * @return
     * @throws IOException
     * @throws NetworkProtocolException
     * @throws JSONException
     * @throws IllegalStateException
     */
    public JSONObject putJson(String path, JSONObject jsonObject) throws IOException,
            NetworkProtocolException, IllegalStateException, JSONException {
        return toJsonObject(put(path, jsonObject.toString()));
    }

    public HttpResponse putJson(String path, boolean jsonValue) throws IOException,
            NetworkProtocolException {
        return put(path, jsonValue ? "true" : "false");
    }

    /**
     * @param path
     * @param jsonString
     * @return A HttpResponse that has been checked for improper response codes.
     * @throws IOException
     * @throws NetworkProtocolException
     */
    protected synchronized HttpResponse put(String path, String jsonString) throws IOException,
            NetworkProtocolException {
        final String fullUri = getFullUrlAsString(path);
        final HttpPut r = new HttpPut(fullUri);
        if (DEBUG) {
            Log.d("NetworkClient", "PUT " + fullUri);
        }

        r.setEntity(new StringEntity(jsonString, "utf-8"));

        r.setHeader("Content-Type", JSON_MIME_TYPE);

        if (DEBUG) {
            Log.d("NetworkClient", "PUTting: " + jsonString);
        }
        final HttpResponse c = this.execute(r);

        checkStatusCode(c, true);

        return c;
    }

    protected synchronized HttpResponse put(String path, String contentType, InputStream is)
            throws IOException, NetworkProtocolException {
        final String fullUri = getFullUrlAsString(path);
        final HttpPut r = new HttpPut(fullUri);
        if (DEBUG) {
            Log.d("NetworkClient", "PUT " + fullUri);
        }
        r.setEntity(new InputStreamEntity(is, 0));

        r.setHeader("Content-Type", contentType);

        final HttpResponse c = this.execute(r);

        checkStatusCode(c, true);

        return c;
    }

    public synchronized Uri getFullUrl(String path) {
        Uri fullUri;
        if (path.startsWith("http")) {
            fullUri = Uri.parse(path);

        } else {

            fullUri = Uri.parse(mBaseUrl.resolve(path).normalize().toASCIIString());
            if (DEBUG) {
                Log.d("NetworkClient", "path: " + path + ", baseUrl: " + mBaseUrl + ", fullUri: "
                        + fullUri);
            }
        }

        return fullUri;
    }

    public String getBaseUrl() {
        return mBaseUrl.toString();
    }

    public synchronized String getFullUrlAsString(String path) {
        String fullUrl;
        if (path.startsWith("http")) {
            fullUrl = path;

        } else {

            fullUrl = mBaseUrl.resolve(path).normalize().toASCIIString();
            if (DEBUG) {
                Log.d("NetworkClient", "path: " + path + ", baseUrl: " + mBaseUrl + ", fullUrl: "
                        + fullUrl);
            }
        }

        return fullUrl;
    }

    /************************** POST ******************************/

    /**
     * @param path
     * @param jsonString
     * @return
     * @throws IOException
     * @throws NetworkProtocolException
     */
    public synchronized HttpResponse post(String path, String jsonString) throws IOException,
            NetworkProtocolException {

        final String fullUri = getFullUrlAsString(path);
        final HttpPost r = new HttpPost(fullUri);
        if (DEBUG) {
            Log.d("NetworkClient", "POST " + fullUri);
        }

        r.setEntity(new StringEntity(jsonString, "utf-8"));

        r.setHeader("Content-Type", JSON_MIME_TYPE);

        final HttpResponse c = this.execute(r);
        logDebug("just sent: " + jsonString);
        checkStatusCode(c, true);

        return c;
    }

    public JSONObject postJson(String path, JSONObject object) throws IllegalStateException,
            IOException, NetworkProtocolException, JSONException {
        final HttpResponse res = post(path, object.toString());
        return toJsonObject(res);
    }

    /*********************************** User ******************************/

    /**
     * Loads/returns the User for the authenticated user
     *
     * @return the User object for the authenticated user.
     * @throws NetworkProtocolException
     * @throws IOException
     * @throws JSONException
     */
    public JSONObject getUser() throws NetworkProtocolException, IOException, JSONException {
        JSONObject user;

        user = getUser("me");
        return user;
    }

    /**
     * Retrieves a User object representing the given user from the network.
     *
     * @param username
     * @return a new instance of the user
     * @throws NetworkProtocolException
     * @throws IOException
     * @throws JSONException
     */
    public JSONObject getUser(String username) throws NetworkProtocolException, IOException,
            JSONException {
        if (username == null) {
            return null;
        }
        return getObject("user/" + username + "/");
    }

    /**
     * Listener for use with InputStreamWatcher.
     *
     * @author steve
     *
     */
    public static interface TransferProgressListener {
        /**
         * @param bytes
         *            Total bytes transferred.
         */
        public void publish(long bytes);
    }

    public static class InputStreamWatcher extends InputStream {
        private static final int GRANULARITY = 1024 * 100; // bytes; number needed to trigger a
                                                            // publish()
        private final InputStream mInputStream;
        private final TransferProgressListener mProgressListener;
        private long mCount = 0;
        private long mIncrementalCount = 0;

        public InputStreamWatcher(InputStream wrappedStream,
                TransferProgressListener progressListener) {
            mInputStream = wrappedStream;
            mProgressListener = progressListener;
        }

        private void incrementAndNotify(long count) {
            mCount += count;
            mIncrementalCount += count;
            if (mIncrementalCount > GRANULARITY) {
                mProgressListener.publish(mCount);
                mIncrementalCount = 0;
            }
        }

        @Override
        public int read() throws IOException {
            return mInputStream.read();
        }

        private int rcount;

        @Override
        public int read(byte[] b) throws IOException {
            rcount = mInputStream.read(b);
            incrementAndNotify(rcount);
            return rcount;
        }

        @Override
        public int read(byte[] b, int offset, int length) throws IOException {
            rcount = mInputStream.read(b, offset, length);
            incrementAndNotify(rcount);
            return rcount;
        }

        @Override
        public int available() throws IOException {
            return mInputStream.available();
        }

        @Override
        public void close() throws IOException {
            mCount = 0;
            mInputStream.close();
        }

        @Override
        public boolean equals(Object o) {
            return mInputStream.equals(o);
        }

        @Override
        public int hashCode() {
            return mInputStream.hashCode();
        }

        @Override
        public void mark(int readlimit) {
            mInputStream.mark(readlimit);
        }

        @Override
        public boolean markSupported() {
            return mInputStream.markSupported();
        }

        @Override
        public long skip(long n) throws IOException {
            final long count = mInputStream.skip(n);
            incrementAndNotify(count);
            return count;
        }

        @Override
        public synchronized void reset() throws IOException {
            mInputStream.reset();
        }

    }

    /**
     * Uploads content using HTTP PUT.
     *
     * @param context
     * @param progressListener
     * @param serverPath
     *            the URL fragment to which the content should be PUT
     * @param localFile
     *            the local file that should be stored on the server
     * @param contentType
     *            MIME type of the file to be uploaded
     * @return
     * @throws NetworkProtocolException
     * @throws IOException
     * @throws JSONException
     * @throws IllegalStateException
     */
    public JSONObject uploadContent(Context context, TransferProgressListener progressListener,
            String serverPath, Uri localFile, String contentType) throws NetworkProtocolException,
            IOException, IllegalStateException, JSONException {

        if (localFile == null) {
            throw new IOException("Cannot send. Content item does not reference a local file.");
        }

        final InputStream is = getFileStream(context, localFile);

        // next step is to send the file contents.
        final String putUrl = getFullUrlAsString(serverPath);
        final HttpPut r = new HttpPut(putUrl);

        if (DEBUG) {
            Log.d(TAG, "HTTP PUTting " + localFile + " (mimetype: " + contentType + ") to "
                    + putUrl);
        }

        r.setHeader("Content-Type", contentType);

        final AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(
                localFile, "r");

        final InputStreamWatcher isw = new InputStreamWatcher(is, progressListener);

        r.setEntity(new InputStreamEntity(isw, afd.getLength()));

        final HttpResponse c = this.execute(r);
        checkStatusCode(c, true);
        return toJsonObject(c);
    }

    /**
     * Uploads the content using HTTP POST of a multipart MIME document.
     *
     * @param context
     * @param progressListener
     * @param serverPath
     * @param localFile
     * @param contentType
     * @return
     * @throws NetworkProtocolException
     * @throws IOException
     * @throws JSONException
     * @throws IllegalStateException
     */
    public JSONObject uploadContentUsingForm(Context context,
            TransferProgressListener progressListener, String serverPath, Uri localFile,
            String contentType) throws NetworkProtocolException, IOException,
            IllegalStateException, JSONException {

        if (localFile == null) {
            throw new IOException("Cannot send. Content item does not reference a local file.");
        }

        final InputStream is = getFileStream(context, localFile);

        // next step is to send the file contents.
        final String postUrl = getFullUrlAsString(serverPath);
        final HttpPost r = new HttpPost(postUrl);

        if (DEBUG) {
            Log.d(TAG, "Multipart-MIME POSTing " + localFile + " (mimetype: " + contentType
                    + ") to " + postUrl);
        }

        final InputStreamWatcher isw = new InputStreamWatcher(is, progressListener);

        final MultipartEntity reqEntity = new MultipartEntity(HttpMultipartMode.BROWSER_COMPATIBLE);

        final InputStreamBody fileBody = new AndroidFileInputStreamBody(context, localFile, isw,
                contentType);

        if (DEBUG) {
            Log.d(TAG, "uploadContentUsingForm. content length: " + fileBody.getContentLength());
        }

        reqEntity.addPart("file", fileBody);

        r.setEntity(reqEntity);

        final HttpResponse c = this.execute(r);
        checkStatusCode(c, true);

        return toJsonObject(c);
    }

    /**
     * An extension of {@link InputStreamBody} which can retrieve the length / size of a local file
     * using its {@link AssetFileDescriptor}.
     */
    private static class AndroidFileInputStreamBody extends InputStreamBody {
        private final Uri mLocalFile;
        private final Context mContext;

        public AndroidFileInputStreamBody(Context context, Uri localFile, InputStream in,
                String mimeType) {
            super(in, mimeType, localFile.getLastPathSegment());
            mLocalFile = localFile;
            mContext = context;
        }

        private long length() throws FileNotFoundException {
            final AssetFileDescriptor afd = mContext.getContentResolver().openAssetFileDescriptor(
                    mLocalFile, "r");
            return afd.getLength();
        }

        @Override
        public long getContentLength() {

            try {
                return length();
            } catch (final FileNotFoundException e) {
                return -1;
            }
        }
    }

    /*************************** categories **************************/

    /**
     * @return A list of all tags, with the most popular one first.
     *
     * @throws JSONException
     * @throws IOException
     * @throws NetworkProtocolException
     */
    public List<String> getTagsList() throws JSONException, IOException, NetworkProtocolException {
        return getTagsList("");
    }

    public List<String> getRecommendedTagsList(Location near) throws JSONException, IOException,
            NetworkProtocolException {
        return getTagsList("?location=" + near.getLongitude() + ',' + near.getLatitude());
    }

    /**
     * Gets a specfic type of tag list.
     *
     * @param path
     *            either 'favorite' or 'ignore'
     * @return
     * @throws JSONException
     * @throws IOException
     * @throws NetworkProtocolException
     */
    public List<String> getTagsList(String path) throws JSONException, IOException,
            NetworkProtocolException {
        return toNativeStringList(getArray("tag/" + path));
    }

    protected InputStream getFileStream(String localFilename) throws IOException {
        if (localFilename.startsWith("content:")) {
            final ContentResolver cr = this.mContext.getContentResolver();
            return cr.openInputStream(Uri.parse(localFilename));
        } else {
            return new FileInputStream(new File(localFilename));
        }
    }

    protected InputStream getFileStream(Context context, Uri localFileUri) throws IOException {
        return context.getContentResolver().openInputStream(localFileUri);
    }

    /**
     * Perform an offline check to see if there is a pairing stored for this client. Does not block
     * on network connection.
     *
     * @return true if the client is paired with the server.
     */
    @Deprecated
    public boolean isPaired() {
        final AccountManager am = AccountManager.get(mContext);
        final Account[] accounts = am.getAccountsByType(AbsLocastAuthenticationService.ACCOUNT_TYPE);
        return accounts.length >= 1;
    }

    protected void logDebug(String msg) {
        if (DEBUG) {
            Log.d(TAG, msg);
        }

    }

    public static enum UploadType {
        RAW_PUT, FORM_POST
    }

    /**
     * Uploads the content, displaying a notification in the system tray. The notification will show
     * a progress bar as the upload goes on and will show a message when finished indicating whether
     * or not it was successful.
     *
     * @param context
     * @param titled
     *            the titled item that will be used to represent the media item
     * @param serverPath
     *            the path on which
     * @param localFile
     * @param contentType
     * @param uploadType
     * @throws NetworkProtocolException
     * @throws IOException
     * @throws JSONException
     */
    public JSONObject uploadContentWithNotification(Context context, Uri titled, String serverPath,
            Uri localFile, String contentType, UploadType uploadType)
            throws NetworkProtocolException, IOException, JSONException {
        String itemTitle = Titled.getTitle(context, titled);
        if (itemTitle == null) {
            itemTitle = "untitled (item #" + titled.getLastPathSegment() + ")";
        }
        JSONObject updatedCastMedia;
        final ProgressNotification notification = new ProgressNotification(context,
                context.getString(R.string.sync_uploading_cast, itemTitle),
                ProgressNotification.TYPE_UPLOAD, PendingIntent.getActivity(context, 0, new Intent(
                        Intent.ACTION_VIEW, titled).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0), true);

        // assume fail: when successful, all will be reset.
        notification.successful = false;
        notification.doneTitle = context.getString(R.string.sync_upload_fail);
        notification.doneText = context.getString(R.string.sync_upload_fail_message, itemTitle);

        notification.doneIntent = PendingIntent.getActivity(context, 0, new Intent(
                Intent.ACTION_VIEW, titled).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);

        final NotificationManager nm = (NotificationManager) context
                .getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationProgressListener tpl = new NotificationProgressListener(nm, notification,
                0, titled.hashCode());

        try {
            final AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(
                    localFile, "r");
            final long max = afd.getLength();

            tpl.setSize(max);

            switch (uploadType) {
                case RAW_PUT:
                    updatedCastMedia = uploadContent(context, tpl, serverPath, localFile,
                            contentType);
                    break;
                case FORM_POST:
                    updatedCastMedia = uploadContentUsingForm(context, tpl, serverPath, localFile,
                            contentType);
                    break;

                default:
                    throw new IllegalArgumentException("unhandled upload type: " + uploadType);
            }

            notification.doneTitle = context.getString(R.string.sync_upload_success);
            notification.doneText = context.getString(R.string.sync_upload_success_message,
                    itemTitle);
            notification.successful = true;

        } catch (final NetworkProtocolException e) {
            notification.setUnsuccessful(e.getLocalizedMessage());
            throw e;
        } catch (final IOException e) {
            notification.setUnsuccessful(e.getLocalizedMessage());
            throw e;
        } finally {
            tpl.done();
        }
        return updatedCastMedia;
    }

    /**
     * @param favoritable
     * @param newState
     * @return the newly-set state
     * @throws NetworkProtocolException
     * @throws IOException
     */
    public boolean setFavorite(Uri favoritable, boolean newState) throws NetworkProtocolException,
            IOException {
        try {
            final String newStateString = "favorite=" + (newState ? "true" : "false");
            // TODO this may work for many cases, but breaks when the provider is an a different
            // process. Is there another way to communicate with it?

            final SyncableProvider provider = (SyncableProvider) mContext.getContentResolver()
                    .acquireContentProviderClient(favoritable).getLocalContentProvider();
            if (provider == null) {
                throw new RuntimeException("provider for " + favoritable
                        + " must be in the same process as the calling class");
            }
            final HttpResponse hr = post(provider.getPublicPath(mContext, favoritable)
                    + "favorite/", newStateString);
            final JSONObject serverStateObj = toJsonObject(hr);
            final boolean serverState = serverStateObj.getBoolean("is_favorite");
            return serverState;

        } catch (final IllegalStateException e) {
            throw new NetworkProtocolException(e.getLocalizedMessage());
        } catch (final JSONException e) {
            throw new NetworkProtocolException(e);
        } catch (final NoPublicPath e) {
            final NetworkProtocolException npe = new NetworkProtocolException(
                    "no known path to mark favorite");
            npe.initCause(e);
            throw npe;
        }
    }

    /******************************** utils **************************/

    public static JSONArray featureCollectionToList(JSONObject featureCollection)
            throws NetworkProtocolException, JSONException {
        if (!featureCollection.getString("type").equals("FeatureCollection")) {
            throw new NetworkProtocolException("Expecting a FeatureCollection but received a "
                    + featureCollection.getString("type"));
        }

        return featureCollection.getJSONArray("features");
    }

    public static List<String> toNativeStringList(JSONArray ja) throws JSONException {
        final Vector<String> strs = new Vector<String>(ja.length());
        for (int i = 0; i < ja.length(); i++) {
            strs.add(i, ja.getString(i));
        }
        return strs;
    }

    /**
     * Generates a query string from a hashmap of query parameters.
     *
     * @param parameters
     * @return
     */
    static public String toQueryString(HashMap<String, Object> parameters) {
        final StringBuilder query = new StringBuilder();
        for (final Iterator<String> i = parameters.keySet().iterator(); i.hasNext();) {
            final String key = i.next();
            query.append(key).append('=');

            final Object val = parameters.get(key);
            if (val instanceof Date) {
                query.append(dateFormat.format(val));
            } else {
                query.append(val.toString());
            }

            if (i.hasNext()) {
                query.append("&");
            }
        }
        return query.toString();
    }

    public static Date parseDate(String dateString) throws ParseException {
        /*
         * if (dateString.endsWith("Z")){ dateString = dateString.substring(0,
         * dateString.length()-2) + "GMT"; }
         */
        return dateFormat.parse(dateString);
    }

    static {
        dateFormat.setCalendar(Calendar.getInstance(TimeZone.getTimeZone("GMT")));
    }

}
