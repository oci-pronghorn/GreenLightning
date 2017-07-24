package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.BlobReader;

public interface CallableMethod<T> {
	
	boolean method(T that, CharSequence title, BlobReader reader);
	
}
