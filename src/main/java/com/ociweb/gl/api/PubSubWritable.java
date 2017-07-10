package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.BlobWriter;

public interface PubSubWritable {

	void write(BlobWriter writer);
	
}
