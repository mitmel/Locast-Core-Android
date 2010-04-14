package edu.mit.mel.locast.mobile;

import java.net.URL;

import android.os.Handler;
import android.widget.ImageView;

import com.commonsware.cwac.cache.SimpleWebImageCache;
import com.commonsware.cwac.thumbnail.ThumbnailBus;
import com.commonsware.cwac.thumbnail.ThumbnailMessage;

public class WebImageLoader {
	private SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> cache;
	
	Handler handler;
	public WebImageLoader(SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> cache) {
		this.cache = cache;
		cache.getBus().register(getBusKey(), onCache);
		
	}

	/**
	 * If you wish to share your image cache across the entire application or activity,
	 * you can set a cache here. If you don't specify one here, one will be created when
	 * setURL is called.
	 * 
	 * @param cache your shared cache
	 */
	public void setCache(SimpleWebImageCache<ThumbnailBus, ThumbnailMessage> cache){
		this.cache = cache;
		
	}
	
	@Override
	public void finalize() throws Throwable {
		// should probably find a better place for this
		cache.getBus().unregister(onCache);
	}
	
	private String getBusKey(){
		return toString();
		
	}
	
	public void loadImage(ImageView image, URL url){
		loadImage(image, url.toExternalForm());
	}
	
	/**
	 * Sets the URL for the image and requests that it be loaded. 
	 * Will load in the background.
	 * Note: sets the image's Tag to the URL.
	 * this must be called from the UI thread.
	 * 
	 * @param url 
	 */
	public void loadImage(ImageView image, String url){
		// this will grab the UI thread.
		if (handler == null){
			handler = new Handler();
		}
		final ThumbnailMessage msg = cache.getBus().createMessage(getBusKey());
		
		msg.setImageView(image);
		msg.setUrl(url);
		image.setTag(url);
		
		try {
			cache.notify(msg.getUrl(), msg);
		} catch (final Exception e) {
			e.printStackTrace();
		}
	}
	
	// from ThumbnailAdapter.java
    private final ThumbnailBus.Receiver<ThumbnailMessage> onCache =
        new ThumbnailBus.Receiver<ThumbnailMessage>() {
        public void onReceive(final ThumbnailMessage message) {
            final ImageView image = message.getImageView();

            handler.post(new Runnable() {
                public void run() {
                    if (image.getTag()!=null &&
                            image.getTag().toString().equals(message.getUrl())) {
                        image.setImageDrawable(cache.get(message.getUrl()));
                    }
                }
            });
        }
    };
}
