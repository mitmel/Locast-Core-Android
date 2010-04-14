package edu.mit.mel.locast.mobile;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public class StreamUtils {
	
	/**
	 * Reads a single line from the given input stream,
	 * blocking until it gets a newline.
	 * 
	 * @param in InputStream to read from.
	 * @return A single line, without the newline at the end or null if no data could be read.
	 * @throws IOException
	 */
	static public String readLine(InputStream in) throws IOException{
		StringBuffer buf = new StringBuffer(512);
		
		InputStreamReader in_reader = new InputStreamReader(in);
		
		boolean receivedData = false;
		
		for (int b = in_reader.read();
			b != -1;
			b = in_reader.read()) {
			receivedData = true;
			if (b == '\r') continue;
			if (b == '\n') break;
			buf.append((char)b);
		}
		
		if (!receivedData) return null;
		
		return buf.toString();
	}
	
	/**
	 * Read an InputStream into a String until it hits EOF.
	 *  
	 * @param in
	 * @return the complete contents of the InputStream
	 * @throws IOException
	 */
	static public String inputStreamToString(InputStream in) throws IOException{
		final int bufsize = 8196;
		char[] cbuf = new char[bufsize];
		
		StringBuffer buf = new StringBuffer(bufsize);
		
		InputStreamReader in_reader = new InputStreamReader(in);
		
		for (int readBytes = in_reader.read(cbuf, 0, bufsize);
			readBytes > 0;
			readBytes = in_reader.read(cbuf, 0, bufsize)) {
			buf.append(cbuf, 0, readBytes);
		}

		return buf.toString();
	}
	
	/**
	 * Reads from an inputstream, dumps to an outputstream
	 * @param is
	 * @param os
	 * @throws IOException
	 */
	static public void inputStreamToOutputStream(InputStream is, OutputStream os) throws IOException{
		final int bufsize = 8196;
		byte[] cbuf = new byte[bufsize];
		
		for (int readBytes = is.read(cbuf, 0, bufsize);
			readBytes > 0;
			readBytes = is.read(cbuf, 0, bufsize)) {
			os.write(cbuf, 0, readBytes);
		}
	}
}
