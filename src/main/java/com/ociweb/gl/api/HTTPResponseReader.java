package com.ociweb.gl.api;

import com.ociweb.gl.impl.HTTPPayloadReader;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public class HTTPResponseReader extends HTTPPayloadReader<NetResponseSchema> {

	public HTTPResponseReader(Pipe<NetResponseSchema> pipe) {
		super(pipe);
	}

}