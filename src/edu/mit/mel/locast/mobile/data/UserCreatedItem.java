package edu.mit.mel.locast.mobile.data;

import java.io.IOException;
import java.text.ParseException;
import java.util.Date;
import java.util.List;
import java.util.Vector;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.mit.mel.locast.mobile.net.NetworkClient;
import edu.mit.mel.locast.mobile.net.NetworkProtocolException;

/**
 * @author steve
 *
 */
/**
 * @author steve
 * 
 */
public class UserCreatedItem implements JSONSerializable {
	private int id = -1;
	private String title;
	private String author;
	private String description;
	private Date created;
	private Date modified;
	private List<String> features;
	private List<String> tags;
	private boolean is_favorite;
	private int priority;
	private boolean is_sponsored;
	
	private String privacy = PRIVATE;
	// privacy constants as defined in the protocol.
	public static final String PUBLIC = "public";
	public static final String PROTECTED = "protected";
	public static final String PRIVATE = "private";
	
	
	public UserCreatedItem(){
		tags = new Vector<String>();
	}
	
	public UserCreatedItem(JSONObject item) throws JSONException, IOException,
			NetworkProtocolException {
		fromJSON(this, item);
	}
	public String getTitle() {
		return title;
	}
	public void setTitle(String title) {
		this.title = title;
	}
	public List<String> getFeatures() {
		return features;
	}
	public void setFeatures(Vector<String> features) {
		this.features = features;
	}
	public String getAuthor() {
		return author;
	}
	public void setAuthor(String author) {
		this.author = author;
	}
	public String getDescription() {
		return description;
	}
	public void setDescription(String description) {
		this.description = description;
	}
	public int getId() {
		return id;
	}
	public void setId(int id) {
		this.id = id;
	}
	public Date getCreated() {
		return created;
	}
	public void setCreated(Date created) {
		this.created = created;
	}
	public Date getModified() {
		return modified;
	}

	public void setModified(Date modified) {
		this.modified = modified;
	}
	public List<String> getTags() {
		return tags;
	}
	public void setTags(List<String> list) {
		this.tags = list;
	}
	public boolean is_favorite() {
		return is_favorite;
	}
	public void setFavorite(boolean is_favorite) {
		this.is_favorite = is_favorite;
	}
	public int getPriority() {
		return priority;
	}
	public void setPriority(int priority) {
		this.priority = priority;
	}
	public boolean is_sponsored() {
		return is_sponsored;
	}
	public void setSponsored(boolean is_sponsored) {
		this.is_sponsored = is_sponsored;
	}
	
	public String getPrivacy() {
		return privacy;
	}

	public void setPrivacy(String privacy) {
		this.privacy = privacy;
	}

	public static UserCreatedItem fromJSON(UserCreatedItem w, JSONObject item)
			throws JSONException, IOException, NetworkProtocolException {
		
		// there's got to be a better way to do this!
		w.setId(item.getInt("id"));
		JSONObject props;
		if (item.has("properties")){
			props = item.getJSONObject("properties");
		}else{
			props = item;
		}
		w.setTitle(props.getString("title"));
		w.setAuthor(props.getJSONObject("author").optString("username", ""));
		final JSONObject jo = props.optJSONObject("description");
		if (jo != null) {
			w.setDescription(jo.toString());
		}
		w.setPrivacy(props.optString("privacy"));
		try {
			w.setCreated(NetworkClient.dateFormat.parse(props.getString("created")));
			w.setModified(NetworkClient.dateFormat.parse(props
					.getString("modified")));
		} catch (final ParseException e) {
			final NetworkProtocolException ne = new NetworkProtocolException(
					"invalid date format");
			ne.initCause(e);
			throw ne;
		}
		
		
		
		final Vector<String> categories = new Vector<String>();
		final JSONArray ja = props.getJSONArray("tags");
		for (int i = 0; i < ja.length(); i++){
			//categories.addElement(((JSONObject)ja.get(i)).getString("name"));
			categories.addElement(ja.getString(i));
		}
		w.setTags(categories);
		
		w.setFavorite(props.optBoolean("is_favorite", false));
		w.setSponsored(props.optBoolean("is_sponsored", false));
		w.setPriority(props.optInt("priority", 3));
		
		
		return w;
	}
	
	// TODO fix this up so it is more generic. Right now, this is specific to uploading
	public JSONObject toJSON() throws JSONException {
		final JSONObject js = new JSONObject();
		final JSONObject props = new JSONObject();
		
		js.put("type", "Feature");
		if (getId() >= 0) {
			js.put("id", getId());
		}
		
		props.putOpt("title", getTitle());
		props.putOpt("description", getDescription());
		props.put("privacy", getPrivacy());
		props.put("tags", new JSONArray(tags));
		
		js.put("properties", props);
		
		return js;
		
	}

	public void fromJSON(JSONObject item) throws JSONException,
			IOException,
			NetworkProtocolException {
		fromJSON(this, item);
	}
}
