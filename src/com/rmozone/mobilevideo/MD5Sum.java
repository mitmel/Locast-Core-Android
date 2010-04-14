package com.rmozone.mobilevideo;

//adapted from: http://vyshemirsky.blogspot.com/2007/08/computing-md5-digest-checksum-in-java.html

import java.io.InputStream;
import java.security.MessageDigest;

import android.content.Context;
import android.net.Uri;

public class MD5Sum {

	public static String checksum(Uri uri, Context ctx) {
		  try {
			  //http://stackoverflow.com/questions/559902/android-how-can-i-convert-android-net-uri-object-to-java-net-uri-object
		    InputStream fin = ctx.getContentResolver().openInputStream(uri);
		    
		    java.security.MessageDigest md5er =
		        MessageDigest.getInstance("MD5");
		    byte[] buffer = new byte[1024];
		    int read;
		    do {
		      read = fin.read(buffer);
		      if (read > 0)
		        md5er.update(buffer, 0, read);
		    } while (read != -1);
		    fin.close();
		    byte[] digest = md5er.digest();
		    if (digest == null)
		      return null;
		    String strDigest = "0x";
		    for (int i = 0; i < digest.length; i++) {
		      strDigest += Integer.toString((digest[i] & 0xff) 
		                + 0x100, 16).substring(1).toUpperCase();
		    }
		    return strDigest;
		  } catch (Exception e) {
		    return null;
		  }
		}
	
}
