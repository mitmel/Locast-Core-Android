package edu.mit.mel.locast.mobile;

import com.commonsware.cwac.cache.SimpleWebImageCache;
import com.commonsware.cwac.thumbnail.ThumbnailBus;
import com.commonsware.cwac.thumbnail.ThumbnailMessage;


/**
 * Contains some useful extra services that are shared with the rest of the application.
 * 
 * @author steve
 *
 */
public class Application extends android.app.Application {
	private final ThumbnailBus bus = new ThumbnailBus();
	private final SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache = 
    	new SimpleWebImageCache<ThumbnailBus, ThumbnailMessage>(null, null, 101, 
    			bus);
	
	private final WebImageLoader imageLoader = new WebImageLoader(imgCache); 
	
	public SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> getImageCache() {
		return imgCache;
	}
	
	public ThumbnailBus getThumbnailBus() {
		return bus;
	}
	
	/**
	 * @return the cached image loader.
	 */
	public WebImageLoader getImageLoader(){
		return imageLoader;
	}
}
