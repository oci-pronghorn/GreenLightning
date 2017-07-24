package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.BlobReader;

public interface CallableStaticMethod<T> {
	
	boolean method(T that, CharSequence title, BlobReader reader);
	
}
