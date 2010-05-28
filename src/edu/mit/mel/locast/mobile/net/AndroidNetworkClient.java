package edu.mit.mel.locast.mobile.net;

import java.io.IOException;
import java.io.InputStream;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.net.Uri;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;
import edu.mit.mel.locast.mobile.data.User;

public class AndroidNetworkClient extends NetworkClient {
	private final Context context;
	private final SharedPreferences prefs;
	public final static String PREF_USERNAME = "username",
								PREF_PASSWORD = "password",
								PREF_SERVER_URL = "server_url",
								TAG = "AndroidNetworkClient";
	
	private static AndroidNetworkClient instance = null;
	static public AndroidNetworkClient getInstance(Context context){
		if (instance == null){
			instance = new AndroidNetworkClient(context);
		}
		return instance;
	}
	
	public AndroidNetworkClient(Context context){
		this.context = context;
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
	}

	public void loadFromPreferences(){
		Log.i(TAG, "Preferences changed. Updating network settings.");
		loadBaseUri();
		try {
			loadCredentials();
		} catch (final IOException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	protected InputStream getFileStream(String localFilename) throws IOException {
		final ContentResolver cr = this.context.getContentResolver();
		//Cursor c = cr.query(Uri.parse(localFilename), null, null, null, null);
		//c.getColumnIndex(Media.DATA);
		return cr.openInputStream(Uri.parse(localFilename));
		
	}
	
	protected synchronized void loadBaseUri(){
		this.baseurl = prefs.getString(PREF_SERVER_URL, "http://mel-pydev.mit.edu/civic/api");
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
				final User u = getUser();
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
