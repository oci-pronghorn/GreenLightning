package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.http.HeaderValue;
import com.ociweb.pronghorn.util.Appendables;

import java.io.IOException;

public class BasicAuthorization implements HeaderValue {

	private final byte[] backing;


	/**
	 *
	 * @param username used for basic authorization
	 * @param password used for basic authorization
	 */
	public BasicAuthorization(String username, String password) {		
		backing = (username+":"+password).getBytes();		
	}


	@Override
	public <A extends Appendable> A appendTo(A target) {
		
		try {
			Appendables.appendBase64(target.append("Basic "), backing, 0, backing.length,Integer.MAX_VALUE);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
				
		return target;
	}

}
