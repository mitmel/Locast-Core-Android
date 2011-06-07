package edu.mit.mobile.android.locast.net;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.BasicHttpParams;
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
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mobile.android.locast.accounts.AuthenticationService;
import edu.mit.mobile.android.locast.data.Cast;
import edu.mit.mobile.android.locast.data.MediaProvider;
import edu.mit.mobile.android.locast.notifications.ProgressNotification;
import edu.mit.mobile.android.locast.ver2.R;
import edu.mit.mobile.android.utils.StreamUtils;


/**
 * An client implementation of the JSON RESTful API for the Locast project.
 *
 * @author stevep
 */
public class NetworkClient extends DefaultHttpClient implements OnSharedPreferenceChangeListener {
	private static final String TAG = NetworkClient.class.getSimpleName();
	public final static String JSON_MIME_TYPE = "application/json";

	private final static String
		PATH_PAIR = "pair/",
		PATH_UNPAIR = "un-pair/",
		PATH_USER   = "user/me"
		;

	protected String baseurl;
	protected URI baseuri;
	// one of the formats from ISO 8601
	public final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

	private JSONObject user;

	private AuthScope authScope;

	protected final Context context;

	protected final SharedPreferences prefs;
	protected final static HttpRequestInterceptor PREEMPTIVE_AUTH = new HttpRequestInterceptor() {
	    public void process(
	            final HttpRequest request,
	            final HttpContext context) throws HttpException, IOException {

	        final AuthState authState = (AuthState) context.getAttribute(
	                ClientContext.TARGET_AUTH_STATE);
	        final CredentialsProvider credsProvider = (CredentialsProvider) context.getAttribute(
	                ClientContext.CREDS_PROVIDER);
	        final HttpHost targetHost = (HttpHost) context.getAttribute(
	                ExecutionContext.HTTP_TARGET_HOST);

	        // If not auth scheme has been initialized yet
	        if (authState.getAuthScheme() == null) {
	            final AuthScope authScope = new AuthScope(
	                    targetHost.getHostName(),
	                    targetHost.getPort());
	            // Obtain credentials matching the target host
	            final Credentials creds = credsProvider.getCredentials(authScope);
	            // If found, generate BasicScheme preemptively
	            if (creds != null) {
	            	if (creds.getUserPrincipal() != null){
	            		Log.d("NetworkClient", "Pre-emptively authenticating as: " + creds.getUserPrincipal().getName());
	            	}
	                authState.setAuthScheme(new BasicScheme());
	                authState.setCredentials(creds);
	            }
	        }
	    }
	};

	protected final static HttpRequestInterceptor REMOVE_EXPECTATIONS = new HttpRequestInterceptor() {

		public void process(HttpRequest request, HttpContext context)
				throws HttpException, IOException {
			if (request.containsHeader("Expect")){
				request.removeHeader(request.getFirstHeader("Expect"));
			}
		}
	};

	public static final String PREF_SERVER_URL = "server_url";

	public static final String PREF_LOCAST_SITE = "locast_site";


	private NetworkClient(Context context, ClientConnectionManager manager, HttpParams params){
		super(manager, params);
		this.context = context;


		this.addRequestInterceptor(PREEMPTIVE_AUTH, 0);

		final HttpParams p = this.getParams();
		p.setParameter("http.socket.timeout", new Integer(60000));
		p.setParameter("http.protocol.expect-continue", true);


		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		Log.i(TAG, prefs.getString(PREF_SERVER_URL, ""));
		loadBaseUri();

		prefs.registerOnSharedPreferenceChangeListener(this);

		initClient();

		/*addRequestInterceptor(new HttpRequestInterceptor() {

			public void process(HttpRequest request, HttpContext context)
					throws HttpException, IOException {

				final AbstractClientConnAdapter connAdapter = (AbstractClientConnAdapter) context.getAttribute(ExecutionContext.HTTP_CONNECTION);

				final HttpConnectionMetrics metrics = connAdapter.getMetrics();
				metrics.getSentBytesCount();
			}
		});*/
	}

	protected void initClient(){

		try {
			final URL baseUrl = new URL(this.baseurl);
			baseuri = baseUrl.toURI();

			this.authScope = new AuthScope(baseUrl.getHost(), baseUrl.getPort());

			loadCredentials();

		} catch (final Exception e) {
			showError(e);
			e.printStackTrace();
		}
	}
	/************************* credentials and pairing **********************/

	public String getUsername() {
		String username = null;
		final Credentials credentials = getCredentialsProvider().getCredentials(authScope);
		if (credentials != null){
			username = credentials.getUserPrincipal().getName();
		}
		return username;
	}

	/**
	 * Set the login credentials.
	 *
	 * @param credentials
	 * @throws RecordStoreException
	 * @throws IOException
	 */
	protected void setCredentials(String username, String auth_secret) throws IOException {
		this.user = null;


		this.setCredentials(new UsernamePasswordCredentials(username, auth_secret));
	}

	/**
	 * Set the login credentials. Most often you will want to pass in a UsernamePasswordCredentials() object.
	 *
	 * @param credentials
	 */
	protected void setCredentials(Credentials credentials){
		this.user = null;


		this.getCredentialsProvider().clear();
		this.getCredentialsProvider().setCredentials(authScope, credentials);
	}

	/**
	 * Gets the authenticated user.
	 *
	 * @return
	 * @throws IllegalStateException
	 * @throws NetworkProtocolException
	 * @throws IOException
	 * @throws JSONException
	 */
	public JSONObject getAuthenticatedUser() throws IllegalStateException, NetworkProtocolException, IOException, JSONException{
		if (user == null){
			user = getUser(getUsername());
		}
		return user;
	}

	public boolean isAuthenticated(){
		return getUsername() != null;
	}

	public static Bundle authenticate(Context context, String username, String password) throws IOException, JSONException, NetworkProtocolException{
		final NetworkClient nc = getInstance(context);
		nc.setCredentials(username, password);
		final HttpResponse res = nc.get(PATH_USER);
		final boolean authenticated = nc.checkStatusCode(res, false);

		final HttpEntity ent = res.getEntity();
		JSONObject jo = null;

		if (authenticated){
			jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
			ent.consumeContent();
		}else{
			jo = null;
		}
		// ensure that this instance is never reused, as it could have invalid authentication cached.
		mInstance = null;

		return jsonObjectToBundle(jo);
	}

	public static Bundle jsonObjectToBundle(JSONObject jsonObject){
		final Bundle b = new Bundle();
		for (final Iterator<String> i = jsonObject.keys(); i.hasNext(); ){
			final String key = i.next();
			final Object value = jsonObject.opt(key);
			if (value == null){
				b.putSerializable(key, null);
			}else if (value instanceof String){
				b.putString(key, (String) value);
			}else if (value instanceof Integer){
				b.putInt(key, (Integer) value);
			}
		}
		return b;
	}

	/**
	 * Makes a request to pair the device with the server. The server sends
	 * back a set of credentials which are then stored for making further
	 * queries.
	 *
	 * @param pairCode the unique code that is provided by the server.
	 * @return true if pairing process was successful, otherwise false.
	 * @throws IOException
	 * @throws JSONException
	 * @throws RecordStoreException
	 * @throws NetworkProtocolException
	 */
	public boolean pairDevice(String pairCode) throws IOException, JSONException, NetworkProtocolException{
		final DefaultHttpClient hc = new DefaultHttpClient();
		hc.addRequestInterceptor(REMOVE_EXPECTATIONS);
		final HttpPost r = new HttpPost(getFullUriAsString(PATH_PAIR));

		final List<BasicNameValuePair> parameters = new ArrayList<BasicNameValuePair>();
		parameters.add(new BasicNameValuePair("auth_secret", pairCode));
		r.setEntity(new UrlEncodedFormEntity(parameters));

		r.setHeader("Content-Type", URLEncodedUtils.CONTENT_TYPE);
		final HttpResponse c = hc.execute(r);

		checkStatusCode(c, false);

		final JSONObject creds = toJsonObject(c);
		saveCredentials(creds.getString("username"), creds.getString("auth_secret"));

		return true;
	}

	/**
	 * Requests that the device be unpaired with the server.
	 * @return true if successful, false otherwise.
	 * @throws IOException
	 * @throws NetworkProtocolException
	 * @throws RecordStoreException
	 */
	public boolean unpairDevice() throws IOException, NetworkProtocolException{
		final HttpPost r = new HttpPost(getFullUriAsString(PATH_UNPAIR));

		final HttpResponse c = this.execute(r);
		checkStatusCode(c, false);

		if (c.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
			clearCredentials();
			return true;
		} else {
			return false;
		}
	}

	/*************************** all request methods ******************/

	public HttpResponse head(String path) throws IOException, JSONException, NetworkProtocolException {
		final HttpHead req = new HttpHead(getFullUriAsString(path));

		return this.execute(req);
	}

	/**
	 * Given a HttpResponse, checks that the return types are all correct and returns
	 * a JSONObject from the response.
	 *
	 * @param res
	 * @return the full response body as a JSONObject
	 * @throws IllegalStateException
	 * @throws IOException
	 * @throws NetworkProtocolException
	 * @throws JSONException
	 */
	public static JSONObject toJsonObject(HttpResponse res) throws IllegalStateException, IOException, NetworkProtocolException, JSONException {
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
	 * @param createdOk true if a 201 (CREATED) code is ok. Otherwise, only 200 (OK) is allowed.
	 * @throws HttpResponseException if the status code is 400 series.
	 * @throws NetworkProtocolException for all status codes errors.
	 * @throws IOException
	 */
	public boolean checkStatusCode(HttpResponse res, boolean createdOk) throws HttpResponseException, NetworkProtocolException, IOException {
		final int statusCode = res.getStatusLine().getStatusCode();
		if (statusCode == HttpStatus.SC_OK || (createdOk && statusCode == HttpStatus.SC_CREATED)){
			return true;
		}else if (statusCode >= HttpStatus.SC_BAD_REQUEST && statusCode < HttpStatus.SC_INTERNAL_SERVER_ERROR) {
			throw new HttpResponseException(statusCode, res.getStatusLine().getReasonPhrase());
		}else{
			final HttpEntity e = res.getEntity();
			if (e.getContentType().getValue().equals("text/html") || e.getContentLength() > 40){
				logDebug("Got long response body. Not showing.");
			}else{
				logDebug(StreamUtils.inputStreamToString(e.getContent()));
			}
			e.consumeContent();
			throw new NetworkProtocolException("HTTP " + res.getStatusLine().getStatusCode() + " "+ res.getStatusLine().getReasonPhrase(), res);
		}
	}

	/**
	 * Verifies that the HttpResponse has a correct content type. Throws exceptions if not.
	 * @param res
	 * @param contentType
	 * @param exact If false, will only check to see that the result content type starts with the desired contentType.
	 * @return
	 * @throws NetworkProtocolException
	 * @throws IOException
	 */
	private static boolean checkContentType(HttpResponse res, String contentType, boolean exact) throws NetworkProtocolException, IOException {
		final String resContentType = res.getFirstHeader("Content-Type").getValue();
		if (! (exact ? resContentType.equals(contentType) : resContentType.startsWith(contentType))) {
			throw new NetworkProtocolException("Did not return content-type '"+contentType+"'. Got: '"+
					resContentType + "'", res);
		}
		return true;
	}

	/************************************ GET *******************************/
	/**
	 * Gets an object and verifies that it got a successful response code.
	 * @param path
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 * @throws NetworkProtocolException
	 * @throws HttpResponseException
	 */
	public HttpResponse get(String path) throws IOException, JSONException, NetworkProtocolException, HttpResponseException {
		final String fullUri = getFullUriAsString(path);
		final HttpGet req = new HttpGet(fullUri);
		Log.d("NetworkClient", "GET "+ fullUri);
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
	public JSONObject getObject(String path) throws IOException, JSONException, NetworkProtocolException{
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
	public JSONArray getArray(String path) throws IOException, JSONException, NetworkProtocolException{

		final HttpEntity ent = getJson(path);
		final JSONArray ja = new JSONArray(StreamUtils.inputStreamToString(ent.getContent()));
		ent.consumeContent();
		return ja;
	}

	private synchronized HttpEntity getJson(String path) throws IOException, JSONException, NetworkProtocolException {

		final HttpResponse res = get(path);

		checkContentType(res, JSON_MIME_TYPE, false);

		//XXX possibly untrue isAuthenticated = true; // this should only get set if we managed to make it here

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
	public JSONObject putJson(String path, JSONObject jsonObject)  throws IOException,
		NetworkProtocolException, IllegalStateException, JSONException {
		return toJsonObject(put(path, jsonObject.toString()));
	}

	public HttpResponse putJson(String path, boolean jsonValue)  throws IOException,
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
		NetworkProtocolException{
		final String fullUri = getFullUriAsString(path);
		final HttpPut r = new HttpPut(fullUri);
		Log.d("NetworkClient", "PUT "+ fullUri);

		r.setEntity(new StringEntity(jsonString, "utf-8"));

		r.setHeader("Content-Type", JSON_MIME_TYPE);

		Log.d("NetworkClient", "PUTting: "+jsonString);
		final HttpResponse c = this.execute(r);

		checkStatusCode(c, true);

		return c;
	}

	protected synchronized HttpResponse put(String path, String contentType, InputStream is) throws IOException,
		NetworkProtocolException {
		final String fullUri = getFullUriAsString(path);
		final HttpPut r = new HttpPut(fullUri);
		Log.d("NetworkClient", "PUT "+ fullUri);
		r.setEntity(new InputStreamEntity(is, 0));

		r.setHeader("Content-Type", contentType);

		final HttpResponse c = this.execute(r);

		checkStatusCode(c, true);

		return c;
	}

	public synchronized Uri getFullUri (String path){
		Uri fullUri;
		if (path.startsWith("http")){
			fullUri = Uri.parse(path);

		}else {

			fullUri = Uri.parse(baseuri.resolve(path).normalize().toASCIIString());
			Log.d("NetworkClient", "path: " + path + ", baseUri: " + baseuri + ", fullUri: "+fullUri);
		}

		return fullUri;
	}

	public synchronized String getFullUriAsString (String path){
		String fullUri;
		if (path.startsWith("http")){
			fullUri = path;

		}else {

			fullUri = baseuri.resolve(path).normalize().toASCIIString();
			Log.d("NetworkClient", "path: " + path + ", baseUri: " + baseuri + ", fullUri: "+fullUri);
		}

		return fullUri;
	}

	/************************** POST ******************************/

	/**
	 * @param path
	 * @param jsonString
	 * @return
	 * @throws IOException
	 * @throws NetworkProtocolException
	 */
	public synchronized HttpResponse post(String path, String jsonString)
		throws IOException, NetworkProtocolException {

		final String fullUri = getFullUriAsString(path);
		final HttpPost r = new HttpPost(fullUri);
		Log.d("NetworkClient", "POST "+ fullUri);

		r.setEntity(new StringEntity(jsonString, "utf-8"));

		r.setHeader("Content-Type", JSON_MIME_TYPE);

		final HttpResponse c = this.execute(r);

		if (c.getStatusLine().getStatusCode() >= 300){
			logDebug("just sent: " + jsonString);
			c.getEntity().consumeContent();
			// TODO should revise this to say that HTTP_CREATED is ok too.
			throw new NetworkProtocolException(c, HttpStatus.SC_OK);
		}
		return c;
	}

	public JSONObject postJson(String path, JSONObject object) throws IllegalStateException, IOException, NetworkProtocolException, JSONException{
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
	public JSONObject getUser() throws NetworkProtocolException, IOException, JSONException{
		if (user == null){

			user = getUser("me");
		}
		return user;
	}

	/**
	 * Retrieves a User object representing the given user from the network.
	 * @param username
	 * @return a new instance of the user
	 * @throws NetworkProtocolException
	 * @throws IOException
	 * @throws JSONException
	 */
	public JSONObject getUser(String username) throws NetworkProtocolException, IOException, JSONException {
		if (username == null){
			return null;
		}
		return getObject("user/"+username+"/");
	}

	/**
	 * Listener for use with InputStreamWatcher.
	 *
	 * @author steve
	 *
	 */
	public static interface TransferProgressListener {
		/**
		 * @param bytes Total bytes transferred.
		 */
		public void publish(long bytes);
	}

	public static class InputStreamWatcher extends InputStream {
		private static final int GRANULARITY = 1024 * 100; // bytes; number needed to trigger a publish()
		private final InputStream mInputStream;
		private final TransferProgressListener mProgressListener;
		private long mCount = 0;
		private long mIncrementalCount = 0;

		public InputStreamWatcher(InputStream wrappedStream, TransferProgressListener progressListener) {
			mInputStream = wrappedStream;
			mProgressListener = progressListener;
		}
		private void incrementAndNotify(long count){
			mCount += count;
			mIncrementalCount += count;
			if (mIncrementalCount > GRANULARITY){
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

	public void uploadContent(Context context, TransferProgressListener progressListener, String serverPath, Uri localFile, String contentType) throws NetworkProtocolException, IOException{

		if (localFile == null) {
			throw new IOException("Cannot send. Content item does not reference a local file.");
		}

		final InputStream is = getFileStream(context, localFile);

		// next step is to send the file contents.
		final HttpPut r = new HttpPut(getFullUriAsString(serverPath));

		r.setHeader("Content-Type", contentType);

		final AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(localFile, "r");

		final InputStreamWatcher isw = new InputStreamWatcher(is, progressListener);

		r.setEntity(new InputStreamEntity(isw, afd.getLength()));

		final HttpResponse c = this.execute(r);
		checkStatusCode(c, true);
		c.getEntity().consumeContent();
	}


	/***************************categories   **************************/


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

	public List<String> getRecommendedTagsList(Location near) throws JSONException, IOException, NetworkProtocolException{
		return getTagsList("?location=" + near.getLongitude() + ','+ near.getLatitude());
	}

	/**
	 * Gets a specfic type of tag list.
	 * @param path either 'favorite' or 'ignore'
	 * @return
	 * @throws JSONException
	 * @throws IOException
	 * @throws NetworkProtocolException
	 */
	public List<String> getTagsList(String path) throws JSONException, IOException, NetworkProtocolException {
		return toNativeStringList(getArray("tag/"+path));
	}

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		if (PREF_SERVER_URL.equals(key)){
			loadFromPreferences();
		}
	}

	public void loadFromPreferences() {
		Log.i(TAG, "Preferences changed. Updating network settings.");
		loadBaseUri();
		try {
			loadCredentials();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		//instances.clear();
		initClient();
	}

	protected InputStream getFileStream(String localFilename) throws IOException {
		if (localFilename.startsWith("content:")){
			final ContentResolver cr = this.context.getContentResolver();
			return cr.openInputStream(Uri.parse(localFilename));
		}else{
			return new FileInputStream(new File(localFilename));
		}
	}

	protected InputStream getFileStream(Context context, Uri localFileUri)
			throws IOException {
				return context.getContentResolver().openInputStream(localFileUri);
			}

	private static NetworkClient mInstance;

	public static NetworkClient getInstance(Context context) {
		if (mInstance == null){
			final HttpParams params = new BasicHttpParams();

			String appVersion = "unknown";
			try {
				appVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
			} catch (final NameNotFoundException e) {
				e.printStackTrace();
			}
			final String userAgent = context.getString(R.string.app_name) + "/"+appVersion;

		    // Set the specified user agent and register standard protocols.
		    HttpProtocolParams.setUserAgent(params, userAgent);
		    final SchemeRegistry schemeRegistry = new SchemeRegistry();
		    schemeRegistry.register(new Scheme("http",
		            PlainSocketFactory.getSocketFactory(), 80));
		    schemeRegistry.register(new Scheme("https",
		    		SSLSocketFactory.getSocketFactory(), 443));

		    final ClientConnectionManager manager =
		            new ThreadSafeClientConnManager(params, schemeRegistry);

		    mInstance = new NetworkClient(context, manager, params);
		}
	    return mInstance;
	}

	protected synchronized void loadBaseUri() {
		this.baseurl = prefs.getString(PREF_SERVER_URL, context.getString(R.string.default_api_url));
		if (!baseurl.endsWith("/")){
			Log.w(TAG, "Baseurl in preferences (" +baseurl+") didn't end in a slash, so we added one.");
			baseurl = baseurl + "/";
			prefs.edit().putString(PREF_SERVER_URL, baseurl).commit();
		}
	}

	/**
	 * Retrieves the user credentials from local storage.
	 *
	 * @throws IOException
	 */
	protected synchronized void loadCredentials() throws IOException {

		final AccountManager am = AccountManager.get(context);
		final Account[] accounts = am.getAccountsByType(AuthenticationService.ACCOUNT_TYPE);
		if (accounts.length == 0){
			Log.i(TAG, "There are no accounts currently set up");
			return;
		}if (accounts.length > 1){
			Log.w(TAG, "more than one Locast account is defined. Using the first one");
		}
		setCredentials(accounts[0].name, am.getPassword(accounts[0]));
	}

	/**
	 * Save the user credentials in a record store for use later. This
	 * will treat the client as being "paired" with the server.
	 *
	 *@deprecated
	 * @param username
	 * @param auth_secret
	 * @throws IOException
	 */
	@Deprecated
	public synchronized void saveCredentials(String username, String auth_secret)
			throws IOException {
		/* XXX delete this method
				final Editor e = prefs.edit();
				e.putString(PREF_USERNAME, username);
				e.putString(PREF_PASSWORD, auth_secret);
				e.commit();
				loadCredentials();
				*/
			}

	/**
	 * Clears all the credentials. This will effectively unpair the client, but won't
	 * issue an unpair request.
	 *@deprecated
	 */
	@Deprecated
	public void clearCredentials() {
		// XXX delete this method
		/*
		final Editor e = prefs.edit();
		e.putString(PREF_USERNAME, "");
		e.putString(PREF_PASSWORD, "");
		e.commit();
		*/
	}

	/**
	 * Perform an offline check to see if there is a pairing stored for this client.
	 * Does not block on network connection.
	 *
	 * @return true if the client is paired with the server.
	 */
	@Deprecated
	public boolean isPaired() {
		final AccountManager am = AccountManager.get(context);
		final Account[] accounts = am.getAccountsByType(AuthenticationService.ACCOUNT_TYPE);
		return accounts.length >= 1;
	}

	protected void showError(Exception e) {
		Toast.makeText(this.context, e.toString(), Toast.LENGTH_LONG).show();

	}

	protected void logDebug(String msg) {
		Log.d(TAG, msg);

	}

	public void uploadContentWithNotification(Context context, Uri cast,
			String serverPath, Uri localFile, String contentType)
			throws NetworkProtocolException, IOException {
				String castTitle = Cast.getTitle(context, cast);
				if (castTitle == null){
					castTitle = "untitled (cast #"+ cast.getLastPathSegment() + ")";
				}
				final ProgressNotification notification = new ProgressNotification(context,
						context.getString(R.string.sync_uploading_cast, castTitle),
						ProgressNotification.TYPE_UPLOAD,
						PendingIntent.getActivity(context, 0, new Intent(Intent.ACTION_VIEW, cast).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0),
						true);

				// assume fail: when successful, all will be reset.
				notification.successful = false;
				notification.doneTitle = context.getString(R.string.sync_upload_fail);
				notification.doneText = context.getString(R.string.sync_upload_fail_message, castTitle);

				notification.doneIntent = PendingIntent.getActivity(context, 0,
						new Intent(Intent.ACTION_VIEW, cast).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);

				final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
				final NotificationProgressListener tpl = new NotificationProgressListener(nm, notification, 0, (int)ContentUris.parseId(cast));

				try {
					final AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(localFile, "r");
					final long max = afd.getLength();

					tpl.setSize(max);

					uploadContent(context, tpl, serverPath, localFile, contentType);
					notification.doneTitle = context.getString(R.string.sync_upload_success);
					notification.doneText = context.getString(R.string.sync_upload_success_message, castTitle);
					notification.successful = true;
				}catch (final NetworkProtocolException e){
					notification.setUnsuccessful(e.getLocalizedMessage());
					throw e;
				}catch (final IOException e){
					notification.setUnsuccessful(e.getLocalizedMessage());
					throw e;
				}finally{
					tpl.done();
				}
			}

	/**
	 * @param favoritable
	 * @param newState
	 * @return the newly-set state
	 * @throws NetworkProtocolException
	 * @throws IOException
	 */
	public boolean setFavorite(Uri favoritable, boolean newState) throws NetworkProtocolException, IOException {
		try {
			final String newStateString = "favorite=" + (newState ? "true" : "false");

			final HttpResponse hr = post(MediaProvider.getPublicPath(context, favoritable) + "favorite/", newStateString);
			final JSONObject serverStateObj = toJsonObject(hr);
			final boolean serverState = serverStateObj.getBoolean("is_favorite");
			return serverState;

		} catch (final IllegalStateException e) {
			throw new NetworkProtocolException(e.getLocalizedMessage());
		} catch (final JSONException e) {
			throw new NetworkProtocolException(e);
		}
	}

	/******************************** utils **************************/

	public static JSONArray featureCollectionToList(JSONObject featureCollection) throws NetworkProtocolException, JSONException{
		if (! featureCollection.getString("type").equals("FeatureCollection")) {
			throw new NetworkProtocolException("Expecting a FeatureCollection but received a "+
					featureCollection.getString("type"));
		}

		return featureCollection.getJSONArray("features");
	}
	public static List<String> toNativeStringList(JSONArray ja) throws JSONException {
		final Vector<String> strs = new Vector<String>(ja.length());
		for (int i = 0; i < ja.length(); i++){
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
	static public String toQueryString(HashMap<String, Object>parameters){
		final StringBuilder query = new StringBuilder();
		for (final Iterator< String> i = parameters.keySet().iterator(); i.hasNext();){
			final String key = i.next();
			query.append(key)
			.append('=');

			final Object val = parameters.get(key);
			if (val instanceof Date){
				query.append(dateFormat.format(val));
			}else{
				query.append(val.toString());
			}

			if (i.hasNext()){
				query.append("&");
			}
		}
		return query.toString();
	}

	public static Date parseDate(String dateString) throws ParseException{
	/*	if (dateString.endsWith("Z")){
			dateString = dateString.substring(0, dateString.length()-2) + "GMT";
		}*/
		return dateFormat.parse(dateString);
	}

	static {
		dateFormat.setCalendar(Calendar.getInstance(TimeZone.getTimeZone("GMT")));
	}
}
