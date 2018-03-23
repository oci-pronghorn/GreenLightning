package com.ociweb.gl.api;

import java.io.IOException;

import com.ociweb.pronghorn.util.Appendables;

public class BasicAuthorization implements HeaderValue {

	private final byte[] backing;
	
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
