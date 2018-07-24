package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.http.HeaderValue;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.CharSequenceToUTF8Local;
import com.ociweb.pronghorn.util.field.UTF8FieldConsumer;

import java.io.IOException;

public class BasicAuthorization implements HeaderValue {

	private final byte[] backing;


	/**
	 *
	 * @param username used for basic authorization
	 * @param password used for basic authorization
	 */
	public BasicAuthorization(String username, String password) {
		backing = CharSequenceToUTF8Local.get()
						    .convert(username)
						    .append(":")
						    .convert(password).asBytes();
		
	}


	@Override
	public <A extends Appendable> A appendTo(A target) {
		
		try {
			Appendables.appendBase64Encoded(target.append("Basic "), backing, 0, backing.length,Integer.MAX_VALUE);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
				
		return target;
	}

}
