package com.stackoverflow;

import java.util.ArrayList;
import java.util.Collection;


/**
 * License: http://creativecommons.org/licenses/by-sa/2.5/
 *
 * @author jon + Alan
 * @see http://stackoverflow.com/questions/122105/java-what-is-the-best-way-to-filter-a-collection
 */
public class CollectionUtils {
	public static <T> Collection<T> filter(Collection<T> target, Predicate<T> predicate) {
	    final Collection<T> result = new ArrayList<T>();
	    for (final T element: target) {
	        if (predicate.apply(element)) {
	            result.add(element);
	        }
	    }
	    return result;
	}

	public static <T> void filterInPlace(Collection<T> target, Predicate<T> predicate) {
		final ArrayList<T> removeList = new ArrayList<T>();
	    for (final T element: target) {
	        if (!predicate.apply(element)) {
	            removeList.add(element);
	        }
	    }
	    for (final T element: removeList){
	    	target.remove(element);
	    }
	}

}
