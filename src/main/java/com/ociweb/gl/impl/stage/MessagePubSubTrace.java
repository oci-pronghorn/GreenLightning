package com.ociweb.gl.impl.stage;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeReader;
import com.ociweb.pronghorn.util.Appendables;

public class MessagePubSubTrace {

	private Pipe<?> pipe;
	private int topic;
	private int payload;
	
	public void init(Pipe<?> pipe, int topicLOC, int payloadLOC) {
		this.pipe = pipe;
		this.topic = topicLOC;
		this.payload = payloadLOC;
	}

	public String toString() {
		
		StringBuilder builder = new StringBuilder();
		
		builder.append("topic: ");
		PipeReader.readUTF8(pipe, topic, builder);
		builder.append(" payload: ");
		
		DataInputBlobReader<?> stream = PipeReader.inputStream(pipe, payload);
		
		while (stream.hasRemainingBytes()) {
			
			Appendables.appendFixedHexDigits(builder, (0xFF&stream.readByte()), 8);
			if (stream.hasRemainingBytes()) {
				builder.append(',');
			}
		}
		
		return builder.toString();
	}

}
