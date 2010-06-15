package edu.mit.mobile.android.json;

import java.io.IOException;
import java.net.URI;

import org.apache.http.client.HttpClient;
import org.apache.http.client.HttpResponseException;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.BaseAdapter;

/**
 * An adapter for either a static JSONArray or a remote list that is loaded from a network.
 * If you need to do custom network requests, override getHttpClient() and getHttpGet().
 * You don't need to call them. 
 * 
 * @author steve
 *
 */
public abstract class JSONArrayAdapter extends BaseAdapter {
	
	private JSONArray ar;
	private LoadJSONArrayTask loadTask;
	
	public JSONArrayAdapter(Context context, JSONArray jsonArray) {
		ar = jsonArray;
	}
	
	public JSONArrayAdapter(Context context, URI remoteList){
		ar = new JSONArray(); // temporary until we get our content...
		loadTask = new LoadJSONArrayTask();
		loadTask.execute(remoteList);
	}
	
	public int getCount() {
		return ar.length();
	}
	
	public void setJSONArray(JSONArray jsonArray){
		ar = jsonArray;
		notifyDataSetChanged();
	}

	public Object getItem(int position) {
		try {
			return ar.get(position);
		} catch (final JSONException e) {
			
			e.printStackTrace();
			return null;
		}
	}

	public long getItemId(int position) {
		
		return position;
	}
	
	public HttpClient getHttpClient(){
		return new DefaultHttpClient();
	}
	
	public HttpGet getHttpGet(URI uri){
		return new HttpGet(uri);
	}
	
	public void onPreExecute(){
		
	}
	
	public void onPostExecute(JSONArray result){
		
	}
	
	private class LoadJSONArrayTask extends AsyncTask<URI, Double, JSONArray>{

		@Override
		protected void onPreExecute() {
			JSONArrayAdapter.this.onPreExecute();
		}
		
		@Override
		protected JSONArray doInBackground(URI... params) {
			JSONArray result = null;
			final HttpClient hc = getHttpClient();
			final HttpGet req = getHttpGet(params[0]);
			try {
				result = new JSONArray(hc.execute(req, new BasicResponseHandler()));
			} catch (final HttpResponseException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (final JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			return result;
		}
	
		@Override
		protected void onPostExecute(JSONArray result) {
			JSONArrayAdapter.this.onPostExecute(result);
			
			if (result == null){
				return;
			}
			setJSONArray(result);
		}
	}
}
