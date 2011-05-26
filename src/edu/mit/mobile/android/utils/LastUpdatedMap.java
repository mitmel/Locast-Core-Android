package edu.mit.mobile.android.utils;

import java.util.HashMap;

public class LastUpdatedMap<T> extends HashMap<T, Long> {
	/**
	 *
	 */
	private static final long serialVersionUID = -1046063858797590395L;

	private final long mTimeout;

	public LastUpdatedMap(long timeout) {
		mTimeout = timeout;
	}

	/**
	 * Mark the item as being recently updated.
	 * @param item
	 */
	public void markUpdated(T item){
		put(item, System.nanoTime());
	}

	/**
	 * @param item
	 * @return true if the item has been updated recently
	 */
	public boolean isUpdatedRecently(T item){
		final Long lastUpdated = get(item);
		if (lastUpdated != null){
			return (System.nanoTime() - lastUpdated) < mTimeout;
		}else{
			return false;
		}
	}
}
