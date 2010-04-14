package edu.mit.mel.locast.mobile.net;

import java.io.IOException;

import org.apache.http.HttpResponse;
import org.json.JSONException;

public class NetworkProtocolException extends Exception {
	private int httpResponseCode;
	private String httpResponseMessage;
	private Exception origException;
	private boolean isGeneric = true;
	/**
	 * 
	 */
	private static final long serialVersionUID = -4060655111979036128L;

	public NetworkProtocolException(String message){
		super(message);
	}
	
	public NetworkProtocolException(String message, HttpResponse r) throws IOException{
		this(message);
		
		httpResponseCode = r.getStatusLine().getStatusCode();
		httpResponseMessage = r.getStatusLine().getReasonPhrase();
	}
	
	public NetworkProtocolException(JSONException e) {
		this("error parsing JSON result:" + e.getMessage());
		isGeneric = false;
		origException = e;
	}
	
	public NetworkProtocolException(HttpResponse r, int expectingHttpResult) throws IOException {
		this(" was expecting to get " + expectingHttpResult + 
				" but got " + r.getStatusLine().getStatusCode() + ": " + r.getStatusLine().getReasonPhrase(), r);
		isGeneric = false;
	}

	public int getHttpResponseCode() {
		return httpResponseCode;
	}

	public String getHttpResponseMessage() {
		return httpResponseMessage;
	}
	
	public Exception getOriginalException(){
		return origException;
	}
	public boolean isGeneric() {
		return isGeneric;
	}
}
