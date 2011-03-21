package edu.mit.mobile.android.locast;

/*
 * Copyright (C) 2010 MIT Mobile Experience Lab
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
		    	imgCache.setMaxSize(300, 300);
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
