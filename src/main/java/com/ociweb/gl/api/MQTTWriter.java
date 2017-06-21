package com.ociweb.gl.api;

import com.ociweb.pronghorn.network.schema.MQTTClientRequestSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public class MQTTWriter extends PayloadWriter<MQTTClientRequestSchema> {

	protected MQTTWriter(Pipe<MQTTClientRequestSchema> p) {
		super(p);
	}

}
