package edu.mit.mel.locast.mobile;

import java.util.Collection;

public class ListUtils {
	
	/**
	 * Join. Why is Collections missing this?
	 * 
	 * @param list
	 * @param delim
	 * @return
	 * @see http://stackoverflow.com/questions/63150/whats-the-best-way-to-build-a-string-of-delimited-items-in-java
	 */
	public static String join(Collection<String> list, String delim) {

	    final StringBuilder sb = new StringBuilder();

	    String loopDelim = "";

	    for(final String s : list) {

	        sb.append(loopDelim);
	        sb.append(s);            

	        loopDelim = delim;
	    }

	    return sb.toString();
	}

}
