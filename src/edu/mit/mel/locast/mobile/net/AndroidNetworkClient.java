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
import java.util.HashMap;

import org.apache.http.params.HttpParams;
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
import edu.mit.mel.locast.mobile.notifications.ProgressNotification;

public class AndroidNetworkClient extends NetworkClient {
	private final Context context;
	private final SharedPreferences prefs;
	public final static String  PREF_USERNAME = "username",
								PREF_PASSWORD = "password",
								PREF_SERVER_URL = "server_url",
								TAG = "AndroidNetworkClient";

	// maintain a separate network client for each thread as the client should only be used on one thread.
	private static HashMap<Long, AndroidNetworkClient> instances = new HashMap<Long, AndroidNetworkClient>();
	static public AndroidNetworkClient getInstance(Context context){
		final long thisThread = Thread.currentThread().getId();

		if (!instances.containsKey(thisThread)){
			instances.put(thisThread, new AndroidNetworkClient(context));
		}
		return instances.get(thisThread);
	}

	public AndroidNetworkClient(Context context){
		super();
		this.context = context;

		final HttpParams p = this.getParams();
		String appVersion = "unknown";
		try {
			appVersion = context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
		} catch (final NameNotFoundException e) {
			e.printStackTrace();
		}
		p.setParameter("http.useragent", context.getString(R.string.app_name) + "/"+appVersion);

		prefs = PreferenceManager.getDefaultSharedPreferences(context);
		Log.i(TAG, prefs.getString(PREF_SERVER_URL, ""));
		loadBaseUri();

		prefs.registerOnSharedPreferenceChangeListener(new OnSharedPreferenceChangeListener() {
			public void onSharedPreferenceChanged(SharedPreferences sharedPreferences,
					String key) {
				loadFromPreferences();
			}
		});

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

	public void loadFromPreferences(){
		Log.i(TAG, "Preferences changed. Updating network settings.");
		loadBaseUri();
		try {
			loadCredentials();
		} catch (final IOException e) {
			e.printStackTrace();
		}
		instances.clear();
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
	}
	/**
	 * Retrieves the user credentials from local storage.
	 *
	 * @throws IOException
	 */
	@Override
	protected synchronized void loadCredentials() throws IOException {
		if (prefs.contains(PREF_USERNAME) && ! prefs.getString(PREF_USERNAME, "").equals("")){
			Log.d("NetworkClient", "u: "+prefs.getString(PREF_USERNAME, "") + "; p: "+prefs.getString(PREF_PASSWORD, ""));
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

	private final static int NOTIFICATION_UPLOAD = 0x2000;
	public void uploadContentWithNotification(Context context, Uri localItem, String serverPath, Uri localFile, String contentType) throws NetworkProtocolException, IOException{
		final ProgressNotification notification = new ProgressNotification(context, 0, "Uploading cast...", true);
		notification.setType(ProgressNotification.TYPE_UPLOAD);
		notification.contentIntent = PendingIntent.getActivity(context, 0, new Intent(Intent.ACTION_VIEW, localItem), 0);
		final NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

		final AssetFileDescriptor afd = context.getContentResolver().openAssetFileDescriptor(localFile, "r");
		final long max = afd.getLength();

		final TransferProgressListener tpl = new TransferProgressListener() {
			int completed;
			public void publish(long bytes) {
				completed = (int)((bytes * 1000) / max);
				notification.setProgress(1000, completed);
				nm.notify(NOTIFICATION_UPLOAD, notification);
			}
		};

		try {
			// assume fail: when successful, all will be reset.
			notification.doneTitle = context.getText(R.string.sync_upload_fail);
			notification.doneIntent = PendingIntent.getActivity(context, 0,
					new Intent(Intent.ACTION_VIEW, localItem).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), 0);

			uploadContent(context, tpl, serverPath, localFile, contentType);
			notification.doneTitle = context.getText(R.string.sync_upload_success);
		}catch (final NetworkProtocolException e){

			notification.successful = false;
			notification.doneText = e.getLocalizedMessage();
			throw e;
		}catch (final IOException e){
			notification.successful = false;
			notification.doneText = e.getLocalizedMessage();
			throw e;
		}finally{
			nm.cancel(NOTIFICATION_UPLOAD);
			notification.done((int) (NOTIFICATION_UPLOAD + ContentUris.parseId(localItem)));
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
