package com.ociweb.gl.impl.stage;

import com.ociweb.gl.api.HTTPRequestReader;

public interface CallableStaticRestRequestReader<T> {

	boolean restRequest(T that, HTTPRequestReader request);
	
}
