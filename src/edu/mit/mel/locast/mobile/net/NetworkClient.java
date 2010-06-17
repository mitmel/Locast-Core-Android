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
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.protocol.ClientContext;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.auth.BasicScheme;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.protocol.ExecutionContext;
import org.apache.http.protocol.HttpContext;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.location.Location;
import android.util.Log;
import edu.mit.mel.locast.mobile.StreamUtils;
import edu.mit.mel.locast.mobile.data.JSONSerializable;
import edu.mit.mel.locast.mobile.data.User;


/**
 * An client implementation of the JSON RESTful API for the WayfarerMobi project.
 * 
 * @author stevep
 * @see https://mobile-server.mit.edu/trac/rai/wiki/JsonRestInterface
 *
 */
/**
 * @author steve
 *
 */
abstract public class NetworkClient extends DefaultHttpClient {
	public final static String JSON_MIME_TYPE = "application/json";

	protected String baseurl;
	// one of the formats from ISO 8601
	public final static SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
	
	private User user;
	
	private boolean isAuthenticated = false;

	private AuthScope authScope;
	HttpRequestInterceptor preemptiveAuth = new HttpRequestInterceptor() {
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
	                authState.setAuthScheme(new BasicScheme());
	                authState.setCredentials(creds);
	            }
	        }
	    }		    
	};
	
	HttpRequestInterceptor removeExpectations = new HttpRequestInterceptor() {
		
		public void process(HttpRequest request, HttpContext context)
				throws HttpException, IOException {
			if (request.containsHeader("Expect")){
				request.removeHeader(request.getFirstHeader("Expect"));
			}
		}
	};
	
	public NetworkClient() {
		this.addRequestInterceptor(preemptiveAuth, 0);
		this.addRequestInterceptor(removeExpectations);
	}
	
	/**
	 * @param baseurl the base URL of the API. This should not end in a "/"
	 */
	public NetworkClient(String baseurl){
		this.baseurl = baseurl;
		
		
		// as dates will be coming in as UTC, but the parser doesn't understand the "Z"
		//DateParser.setTimeZone(TimeZone.getTimeZone("UTC"));
	}
	
	protected void initClient(){
		
		try {
			final URL baseUrl = new URL(this.baseurl); 
			this.authScope = new AuthScope(baseUrl.getHost(), baseUrl.getPort());
			
			loadCredentials();
			
		} catch (final Exception e) {
			showError(e);
			e.printStackTrace();
		}
	}
	abstract protected void showError(Exception e);
	abstract protected void logDebug(String msg);
	
	/************************* credentials and pairing **********************/
	
	public String getUsername() {
		String username = "";
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
	 */
	public User getAuthenticatedUser() throws IllegalStateException, NetworkProtocolException, IOException{
		if (user == null){
			user = getUser(getUsername());
		}
		return user;
	}
	
	/**
	 * Retrieves the user credentials from local storage.
	 * 
	 * @throws IOException
	 */
	protected abstract void loadCredentials() throws IOException;
	
	/**
	 * Save the user credentials for use later. This
	 * will treat the client as being "paired" with the server.
	 * 
	 * @param username
	 * @param auth_secret
	 * @throws IOException
	 */
	public abstract void saveCredentials(String username, String auth_secret) throws IOException;
	
	/**
	 * Clears all the credentials. This will effectively unpair the client, but won't
	 * issue an unpair request.
	 * 
	 */
	public abstract void clearCredentials();
	
	public boolean isAuthenticated(){
		return isAuthenticated;
	}
	
	/**
	 * @return true if the client is paired with the server.
	 */
	abstract public boolean isPaired();
	
	/**
	 * Adds a WWW-Authenticate header to a connection based on the currently set credentials
	 * 
	 * @param con
	 * @throws IOException
	 */
	/*protected void addBasicAuthHeader(HttpRequest con) throws IOException {
		String encoded  = username + ":" + auth_secret;
		encoded = Base64.encodeBytes(encoded.getBytes(), 0, encoded.length());
		
		con.setHeader("Authorization", "Basic " + encoded);
	}*/
	
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
		hc.addRequestInterceptor(removeExpectations);
		final HttpPost r = openPOST("/pair/");
		final String formData = "auth_secret="+pairCode;
		r.setEntity(new StringEntity(formData));
		
		r.setHeader("Content-Type", "x-www-form-urlencoded");
		final HttpResponse c = hc.execute(r);

		final int responseCode = c.getStatusLine().getStatusCode();
		
		if (responseCode == HttpStatus.SC_BAD_REQUEST){
			// avoids any unpleasantly-large error messages.
			String msg;
			if (c.getEntity().getContentLength() > 40){
				logDebug("Got long response");
				msg = c.getStatusLine().getReasonPhrase();
			}else{
				msg = StreamUtils.inputStreamToString(c.getEntity().getContent());
			}
			throw new NetworkProtocolException("Incorrect pairing code (" + msg + ")", c);
			
		}else if(responseCode != HttpStatus.SC_OK) {
			throw new NetworkProtocolException(c, HttpStatus.SC_OK);
		}
		
		final JSONObject creds = new JSONObject(StreamUtils.inputStreamToString(c.getEntity().getContent()));
		saveCredentials(creds.getString("username"), creds.getString("auth_secret"));
		isAuthenticated = true;
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
		final HttpPost r = openPOST("/un-pair");
		
		final HttpResponse c = this.execute(r);
		if (c.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
			clearCredentials();
			return true;
		} else {
			return false;
		}
	}
	
	/*************************** all request methods ******************/
	
	public HttpResponse head(String uri) throws IOException, JSONException, NetworkProtocolException {
		final HttpHead req = openHEAD(uri);
		
		return this.execute(req);
	}
	/************************************ GET *******************************/
	public HttpResponse get(String uri) throws IOException, JSONException, NetworkProtocolException, HttpResponseException {
		
		final HttpGet req = openGET(uri);
		
		final HttpResponse res = this.execute(req);
		
		final int statusCode = res.getStatusLine().getStatusCode();
		if (statusCode == HttpStatus.SC_NOT_FOUND) {
			throw new HttpResponseException(statusCode, res.getStatusLine().getReasonPhrase());
		}
		if (statusCode != HttpStatus.SC_OK){
			final HttpEntity e = res.getEntity();
			if (e.getContentType().getValue().equals("text/html") || e.getContentLength() > 40){
				logDebug("Got long response body. Not showing.");
			}else{
				logDebug(StreamUtils.inputStreamToString(e.getContent()));
			}
			throw new NetworkProtocolException("HTTP " + res.getStatusLine().getStatusCode() + " "+ res.getStatusLine().getReasonPhrase(), res);
		}
		return res;
	}
	/**
	 * Loads a JSON object from the given URI
	 * 
	 * @param uri
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 * @throws NetworkProtocolException
	 */
	public JSONObject getObject(String uri) throws IOException, JSONException, NetworkProtocolException{
		final HttpEntity ent = getJson(uri);
		final JSONObject jo = new JSONObject(StreamUtils.inputStreamToString(ent.getContent()));
		ent.consumeContent();
		return jo;
	}
	
	/**
	 * GETs a JSON Array
	 * 
	 * @param uri
	 * @return
	 * @throws IOException
	 * @throws JSONException
	 * @throws NetworkProtocolException
	 */
	public JSONArray getArray(String uri) throws IOException, JSONException, NetworkProtocolException{
		
		final HttpEntity ent = getJson(uri);
		final JSONArray ja = new JSONArray(StreamUtils.inputStreamToString(ent.getContent()));
		ent.consumeContent();
		return ja;	
	}
	
	private synchronized HttpEntity getJson(String uri) throws IOException, JSONException, NetworkProtocolException {
		
		final HttpResponse res = get(uri);
		
		if (! res.getFirstHeader("Content-Type").getValue().startsWith(JSON_MIME_TYPE)) {
			throw new NetworkProtocolException(uri + " did not return content-type JSON object. Got: "+
					res.getFirstHeader("Content-Type").getValue(), res);
		}
		isAuthenticated = true; // this should only get set if we managed to make it here
		
		return res.getEntity();
	}

	/***************************** PUT ***********************************/
	
	/**
	 * PUTs a JSON object
	 * 
	 * @param uri
	 * @param jsonObject
	 * @return
	 * @throws IOException
	 * @throws NetworkProtocolException
	 */
	public HttpResponse putJson(String uri, JSONObject jsonObject)  throws IOException, 
		NetworkProtocolException {
		return put(uri, jsonObject.toString());
	}
	
	public HttpResponse putJson(String uri, boolean jsonValue)  throws IOException, 
		NetworkProtocolException {
		return put(uri, jsonValue ? "true" : "false");
	}
	
	protected synchronized HttpResponse put(String uri, String jsonString) throws IOException, 
		NetworkProtocolException{
		final HttpPut r = openPUT(uri);
		
		r.setEntity(new StringEntity(jsonString, "utf-8"));
		
		r.setHeader("Content-Type", JSON_MIME_TYPE);
		
		Log.d("NetworkClient", "PUTting: "+jsonString);
		final HttpResponse c = this.execute(r);
		
		if (c.getStatusLine().getStatusCode() >= 300){
			logDebug("just sent:" + jsonString);
			// TODO should revise this to say that HTTP_CREATED is ok too.
			throw new NetworkProtocolException(c, HttpStatus.SC_OK);
		}
		return c;
	}
	
	protected synchronized HttpResponse put(String uri, String contentType, InputStream is) throws IOException, 
		NetworkProtocolException {
		final HttpPut r = openPUT(uri);
		
		r.setEntity(new InputStreamEntity(is, 0));
		
		r.setHeader("Content-Type", contentType);
		
		final HttpResponse c = this.execute(r);
		
		if (c.getStatusLine().getStatusCode() >= 300) {
			// TODO should revise this to say that HTTP_CREATED is ok too.
			throw new NetworkProtocolException(c, HttpStatus.SC_OK);
		}
		return c;
	}
	
	protected synchronized String getFullUri (String uri){
		String fullUri;
		if (uri.startsWith("http")){
			fullUri = uri;
			
		}else {
			fullUri = baseurl + uri;
		}
		
		return fullUri;
	}

	protected synchronized HttpHead openHEAD(String uri) throws IOException, 
	NetworkProtocolException {
		final String fullUri = getFullUri(uri);
		logDebug("HEADting "+ fullUri);
		return new HttpHead(fullUri);
	}
	
	protected synchronized HttpGet openGET(String uri) throws IOException, 
	NetworkProtocolException {
		final String fullUri = getFullUri(uri);
		logDebug("GETting "+ fullUri);
		if (getCredentialsProvider() != null && getCredentialsProvider().getCredentials(authScope) != null){
			logDebug("Authenticating as " + getCredentialsProvider().getCredentials(authScope).getUserPrincipal().getName());
		}else{
			logDebug("No credentials");
		}
		return new HttpGet(fullUri);
	}
	
	protected synchronized HttpPost openPOST(String uri) throws IOException, 
	NetworkProtocolException {
		logDebug("POSTing "+ baseurl + uri);
		return new HttpPost(baseurl+uri);
	}
	
	protected synchronized HttpPut openPUT(String uri) throws IOException, 
	NetworkProtocolException {
		logDebug("PUTting "+ baseurl + uri);
		return new HttpPut(baseurl+uri);
	}
	
	
	/************************** POST ******************************/
	
	/**
	 * @param uri
	 * @param jsonString
	 * @return
	 * @throws IOException
	 * @throws NetworkProtocolException 
	 */
	public synchronized HttpResponse post(String uri, String jsonString) 
		throws IOException, NetworkProtocolException {
		final HttpPost r = openPOST(uri);
		
		r.setEntity(new StringEntity(jsonString, "utf-8"));
		
		r.setHeader("Content-Type", JSON_MIME_TYPE);
		
		final HttpResponse c = this.execute(r);
		
		if (c.getStatusLine().getStatusCode() >= 300){
			logDebug("just sent: " + jsonString);
			// TODO should revise this to say that HTTP_CREATED is ok too.
			throw new NetworkProtocolException(c, HttpStatus.SC_OK);
		}
		return c;
	}
	
	/*********************************** User ******************************/
	
	/**
	 * Loads/returns the User for the authenticated user
	 * 
	 * @return the User object for the authenticated user.
	 * @throws NetworkProtocolException
	 * @throws IOException
	 */
	public User getUser() throws NetworkProtocolException, IOException{
		if (user == null){
			
			user = getUser(getUsername());
		}
		return user;
	}
	
	/**
	 * Retrieves a User object representing the given user from the network.
	 * @param username 
	 * @return a new instance of the user
	 * @throws NetworkProtocolException 
	 * @throws IOException 
	 */
	public User getUser(String username) throws NetworkProtocolException, IOException {
	
		User u;
		try{
			u = loadUser(getObject("/user/"+username+"/"));
		}catch(final JSONException e){
			throw new NetworkProtocolException(e);
		}
	
		return u;
	}
	
	public Vector<User> getUsers() throws NetworkProtocolException, IOException {
		final Vector<User> outUsers = new Vector<User>();
		try{
			final JSONArray users =getArray("/user/");
			for (int i = 0; i < users.length(); i++){
				outUsers.addElement(loadUser(users.getJSONObject(i)));
				
			}
		}catch(final JSONException e){
			final NetworkProtocolException newe = new NetworkProtocolException(e);
			
			throw newe;
		}
		return outUsers;
	}
	
	public User loadUser(JSONObject user) throws NetworkProtocolException, IOException{
		User u = null;
	
		try{
			u = new User(user.getString("username"));
			
			u.setName(user.getString("name"));
			u.setLanguage(user.getString("language"));
			
			/*
			if (user.has("location")){
				JSONObject locJson = user.getJSONObject("location");
				u.setLoc(Locatable.locationToCoordinates(locJson));
			
				u.setLastUpdated(new Date(DateParser.parse(locJson.getString("last_updated"))));
			}*/
			final String iconUrl = user.optString("icon");
			
			
			if (iconUrl != null && iconUrl.length() > 0 && !iconUrl.equals("None")){
//				Image userIcon = getUserIcon(iconUrl);
//				if (userIcon != null){
//					u.setIcon(userIcon);
//				}else{
//					logDebug("could not retrieve icon for " + u.username);
//				}
			}else{
				logDebug(u.username + " has no user icon");
			}
			
		}catch(final JSONException e){
			throw new NetworkProtocolException(e);
		}
		return u;
	}
	
	protected abstract InputStream getFileStream(String localFile) throws IOException;
	
	public void uploadContent(int contentId, String localFile, String contentType) throws NetworkProtocolException, IOException{
		
		if (localFile == null) {
			throw new IOException("Cannot send. Content item does not reference a local file.");
		}
		
		final InputStream is = getFileStream(localFile);
				
		// next step is to send the file contents.
		final HttpPost r = openPOST("/content/"+contentId+"/upload");
		
		r.setHeader("Content-Type", contentType);

		r.setEntity(new InputStreamEntity(is, is.available()));
		
		final HttpResponse c = this.execute(r);
		final int status = c.getStatusLine().getStatusCode();
		if (status != HttpStatus.SC_CREATED && status != HttpStatus.SC_OK) {
			throw new NetworkProtocolException(c, HttpStatus.SC_CREATED);
		}
	}
	

	/***************************categories   **************************/
	
	/**
	 * Gets the categories.
	 * 
	 * @return
	 * @throws IOException
	 * @throws NetworkProtocolException
	 * @throws JSONException
	 */
	public Map<String, Integer> getCategories() throws IOException, NetworkProtocolException, JSONException {
		final JSONArray cats = getArray("/categories/");
		final Map<String, Integer> catOut = new HashMap<String, Integer>();
		
		final int length = cats.length();
		for (int i = 0; i < length; i++){
			final JSONObject o = cats.getJSONObject(i);
			String count = o.optString("count");
			if (count == null){
				count = o.getString("rough_count");
			}
			catOut.put(o.getString("name"), Integer.valueOf(count));
		}
		return catOut;
	}
	
	
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
		return toNativeStringList(getArray("/tag/"+path));
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
	 * Loads a Bitmap from the given URL. Can be a relative path to the baseurl 
	 * 
	 * @param url
	 * @return

	 * @throws IOException
	 */
	/*public Image getBitmap(String url) throws IOException {
		if (!url.startsWith("http://") && ! url.startsWith("https://")){
			url = baseurl + url;
		}
		HttpRequest get = (HttpRequest)Connector.open(url);
		
		
		if (get.getResponseCode() == 200){
			return Image.createImage(get.openInputStream());
			
		}else{
			return null;
		}
	}*/
	
	/*public Image getUserIcon(String url) throws IOException{
		return getBitmap(url);
	}*/

	/**
	 * @param jsonArray
	 * @param objClass
	 * @return
	 * @throws JSONException
	 * @throws IOException
	 * @throws NetworkProtocolException
	 */
	static public Vector<? extends JSONSerializable> jsonToNative(
			JSONArray jsonArray, Class<? extends JSONSerializable> objClass)
			throws JSONException, IOException, NetworkProtocolException {
		final Vector<JSONSerializable> vector = new Vector<JSONSerializable>();
		try {
			for (int i = 0; i < jsonArray.length(); i++){
				JSONSerializable o;	
				o = objClass.newInstance();
				o.fromJSON(jsonArray.getJSONObject(i));
				vector.addElement(o);
			}
		} catch (final InstantiationException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final IllegalAccessException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return vector;
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
