package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.HTTPResponseReader;

public interface CallableStaticHTTPResponse<T> {
	
	boolean responseHTTP(T that, HTTPResponseReader reader);

}
