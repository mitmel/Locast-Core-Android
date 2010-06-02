package edu.mit.mel.locast.mobile.widget;

import java.util.Date;
import java.util.List;

import android.content.Context;
import android.os.AsyncTask;
import android.widget.ArrayAdapter;
import edu.mit.mel.locast.mobile.net.AndroidNetworkClient;

public class RemoteTagsAdapter extends ArrayAdapter<String>{
	private final static long cacheAge = 60 * 1000;
	private long lastUpdated = 0;
	private boolean updating = false;

	public RemoteTagsAdapter(Context context, int textViewResourceId) {
		super(context, textViewResourceId);
	}
	
    public void refreshTags(){
		if (!updating && (lastUpdated == 0 || (new Date().getTime() - lastUpdated > cacheAge))){
			new TagLoaderTask().execute();
		}
    }
	
	public class TagLoaderTask extends AsyncTask<Void, Integer, List<String>>{

		@Override
		protected void onPreExecute() {
			updating = true;
		}
		@Override
		protected List<String> doInBackground(Void... params) {
			
			try {
				final AndroidNetworkClient nc = AndroidNetworkClient.getInstance(getContext());
				final List<String> tags =  nc.getTagsList();
				lastUpdated = new Date().getTime();
				return tags;
				
			} catch (final Exception e) {
				e.printStackTrace();
			}
			return null;
		}
		
		@Override
		protected void onPostExecute(List<String> result) {
			if (result != null){
				// bulk load the items into the array
				RemoteTagsAdapter.this.setNotifyOnChange(false);
				RemoteTagsAdapter.this.clear();
				for (final String tag: result){
					RemoteTagsAdapter.this.add(tag);
				}
				RemoteTagsAdapter.this.notifyDataSetChanged();
				RemoteTagsAdapter.this.setNotifyOnChange(true);
			}
			updating = false;
		}
	}
	
}