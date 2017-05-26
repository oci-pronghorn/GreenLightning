package com.ociweb.gl.api;

import com.ociweb.gl.impl.HTTPPayloadReader;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public class HTTPRequestReader extends HTTPPayloadReader<HTTPRequestSchema> {

	public HTTPRequestReader(Pipe<HTTPRequestSchema> pipe) {
		super(pipe);
	}

}
