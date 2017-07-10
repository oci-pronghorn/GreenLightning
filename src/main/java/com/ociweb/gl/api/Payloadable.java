package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.BlobReader;

public interface Payloadable {

	void read(BlobReader reader);

}
