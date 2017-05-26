package com.ociweb.gl.impl;

import com.ociweb.pronghorn.pipe.MessageSchema;

public interface Payloadable<S extends MessageSchema<S>> {

	void read(PayloadReader<S> httpPayloadReader);

}
