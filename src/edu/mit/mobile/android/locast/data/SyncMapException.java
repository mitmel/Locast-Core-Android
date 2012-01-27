package edu.mit.mobile.android.locast.data;

public class SyncMapException extends RuntimeException {

	/**
	 *
	 */
	private static final long serialVersionUID = -2593099075563144728L;

	public SyncMapException(String message, Throwable e) {
		super(message, e);
	}

	public SyncMapException(String message) {
		super(message);

	}
}
