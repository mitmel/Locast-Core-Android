package edu.mit.mobile.android.locast.net;
/*
 * Copyright (C) 2010  MIT Mobile Experience Lab
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
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
