package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.BlobReader;

public interface CallableMethod {
	
	boolean method(CharSequence title, BlobReader reader);
	
}
