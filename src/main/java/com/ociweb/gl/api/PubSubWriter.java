package com.ociweb.gl.api;

import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.pronghorn.pipe.Pipe;

public class PubSubWriter extends PayloadWriter<MessagePubSub> {

	public PubSubWriter(Pipe<MessagePubSub> p) {
		super(p);
	}

}
