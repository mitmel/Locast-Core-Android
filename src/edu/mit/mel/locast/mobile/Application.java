package edu.mit.mel.locast.mobile;

import java.io.File;

import com.commonsware.cwac.cache.AsyncCache;
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
	private SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> imgCache;
	
	private WebImageLoader imageLoader; 
	
	public SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> getImageCache() {
		if (imgCache == null){ 
		    	imgCache = new SimpleWebImageCache<ThumbnailBus, ThumbnailMessage>(getCacheDir(), policy, 101, 
		    			bus);
		}
		return imgCache;
	}
	
    private final AsyncCache.DiskCachePolicy policy=new AsyncCache.DiskCachePolicy() {
        public boolean eject(File file) {
            return(System.currentTimeMillis()-file.lastModified()>1000*60*60*24*7);
        }
    };
	
	public ThumbnailBus getThumbnailBus() {
		return bus;
	}
	
	/**
	 * @return the cached image loader.
	 */
	public WebImageLoader getImageLoader(){
		if (imageLoader == null){
			imageLoader = new WebImageLoader(getImageCache());
		}
		return imageLoader;
	}
}
