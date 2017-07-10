package com.ociweb.gl.api;

import com.ociweb.pronghorn.util.field.StructuredBlobWriter;

public interface PubSubStructuredWritable {

	void write(StructuredBlobWriter writer);
	
}
