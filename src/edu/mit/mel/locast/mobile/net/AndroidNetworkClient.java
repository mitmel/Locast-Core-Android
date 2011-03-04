package edu.mit.mel.locast.mobile.net;
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

import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mel.locast.mobile.R;
import edu.mit.mel.locast.mobile.data.Cast;
import edu.mit.mel.locast.mobile.notifications.ProgressNotification;

public class AndroidNetworkClient extends NetworkClient implements OnSharedPreferenceChangeListener {
	private static String TAG = AndroidNetworkClient.class.getSimpleName();

	private final Context context;
	private final SharedPreferences prefs;
	public final static String  PREF_USERNAME = "username",
								PREF_PASSWORD = "password",
								PREF_SERVER_URL = "server_url",
								PREF_LOCAST_SITE = "locast_site";

	static public AndroidNetworkClient getInstance(Context context){

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
        		PlainSocketFactory.getSocketFactory(), 443));

        final ClientConnectionManager manager =
                new ThreadSafeClientConnManager(params, schemeRegistry);

        return new AndroidNetworkClient(context, manager, params);
	}

	public AndroidNetworkClient(Context context, ClientConnectionManager manager, HttpParams params){
		super(manager, params);
		this.context = context;

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

	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
			String key) {
		if (PREF_PASSWORD.equals(key) || PREF_USERNAME.equals(key) || PREF_SERVER_URL.equals(key)){
			loadFromPreferences();
		}
	}

	public void loadFromPreferences(){
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

	@Override
	protected InputStream getFileStream(String localFilename) throws IOException {
		if (localFilename.startsWith("content:")){
			final ContentResolver cr = this.context.getContentResolver();
			return cr.openInputStream(Uri.parse(localFilename));
		}else{
			return new FileInputStream(new File(localFilename));
		}
	}

	@Override
	protected InputStream getFileStream(Context context, Uri localFileUri) throws IOException {
		return context.getContentResolver().openInputStream(localFileUri);
	}

	protected synchronized void loadBaseUri(){
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
	@Override
	protected synchronized void loadCredentials() throws IOException {
		if (prefs.contains(PREF_USERNAME) && ! prefs.getString(PREF_USERNAME, "").equals("")){
			setCredentials(prefs.getString(PREF_USERNAME, ""), prefs.getString(PREF_PASSWORD, ""));
		}
	}

	/**
	 * Save the user credentials in a record store for use later. This
	 * will treat the client as being "paired" with the server.
	 *
	 * @param username
	 * @param auth_secret
	 * @throws IOException
	 */
	@Override
	public synchronized void saveCredentials(String username, String auth_secret) throws IOException {
		final Editor e = prefs.edit();
		e.putString(PREF_USERNAME, username);
		e.putString(PREF_PASSWORD, auth_secret);
		e.commit();
		loadCredentials();
	}

	/**
	 * Clears all the credentials. This will effectively unpair the client, but won't
	 * issue an unpair request.
	 *
	 */
	@Override
	public void clearCredentials() {
		final Editor e = prefs.edit();
		e.putString(PREF_USERNAME, "");
		e.putString(PREF_PASSWORD, "");
		e.commit();
	}

	/**
	 * Perform an offline check to see if there is a pairing stored for this client.
	 * Does not block on network connection.
	 *
	 * @return true if the client is paired with the server.
	 */
	@Override
	public boolean isPaired(){
		return (! prefs.getString(PREF_PASSWORD, "").equals(""));
	}

	/**
	 * Perform an check to see if the network connection can contact
	 * the server and return a valid object.
	 *
	 * @return true if the connection was deemed to be working correctly.
	 *
	 */
	public boolean isConnectionWorking(){
		if (isPaired()){
			try {
				final JSONObject u = getUser();
				if(u != null) {
					return true;
				}
			}catch(final Exception e){
				return false;
			}
		}
		return false;
	}

	@Override
	protected void showError(Exception e) {
		Toast.makeText(this.context, e.toString(), Toast.LENGTH_LONG).show();

	}

	@Override
	protected void logDebug(String msg) {
		Log.d(TAG, msg);

	}

	public void uploadContentWithNotification(Context context, Uri cast, String serverPath, Uri localFile, String contentType) throws NetworkProtocolException, IOException{
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
	 * This is a work-around for Android's overzealous SSL verification.
	 * XXX doesn't work :-(
	 * @see http://stackoverflow.com/questions/1217141
	 */
	/*
	private static void trustEveryone() {
        try {
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier(){
                        public boolean verify(String hostname, SSLSession session) {
                                return true;
                        }});
                final SSLContext context = SSLContext.getInstance("TLS");
                context.init(null, new X509TrustManager[]{new X509TrustManager(){
                        public void checkClientTrusted(X509Certificate[] chain,
                                        String authType) throws CertificateException {}
                        public void checkServerTrusted(X509Certificate[] chain,
                                        String authType) throws CertificateException {}
                        public X509Certificate[] getAcceptedIssuers() {
                                return new X509Certificate[0];
                        }}}, new SecureRandom());
                HttpsURLConnection.setDefaultSSLSocketFactory(
                                context.getSocketFactory());
        } catch (final Exception e) { // should never happen
                e.printStackTrace();
        }
}*/

}
