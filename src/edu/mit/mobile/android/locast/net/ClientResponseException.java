package edu.mit.mobile.android.locast.net;

import org.apache.http.client.HttpResponseException;

import android.os.Bundle;

/**
 * A 400-class HTTP response from the server. Notably, it includes the response data from the server
 * retrievable with {@link #getData()}.
 *
 * @author <a href="mailto:spomeroy@mit.edu">Steve Pomeroy</a>
 *
 */
public class ClientResponseException extends HttpResponseException {

    private Bundle mData;

    public ClientResponseException(int statusCode, String s) {
        super(statusCode, s);
    }

    public void setResponseData(Bundle data) {
        mData = data;
    }

    /**
     * @return response data from server translated to a bundle or null if none was set
     */
    public Bundle getData() {
        return mData;
    }

    @Override
    public String toString() {
        return super.toString() + (mData != null ? "; " + mData.toString() : "");
    }

    /**
     *
     */
    private static final long serialVersionUID = -7763868522067008991L;

}
