package com.ociweb.gl.impl;

import com.ociweb.pronghorn.pipe.MessageSchema;

public interface Headable<S extends MessageSchema<S>> {

	public void read(HTTPPayloadReader<S> httpPayloadReader);

}
