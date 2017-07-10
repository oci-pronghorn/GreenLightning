package com.ociweb.gl.api;

import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.token.OperatorMask;
import com.ociweb.pronghorn.pipe.token.TokenBuilder;
import com.ociweb.pronghorn.pipe.token.TypeMask;

public class PubSubWriter extends PayloadWriter<MessagePubSub> {

	public PubSubWriter(Pipe<MessagePubSub> p) {
		super(p);
	}


}
