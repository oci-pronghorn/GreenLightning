package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.util.field.MessageConsumer;

public class DecrementValueBehavior implements PubSubListener {
	private final GreenCommandChannel channel;
    private final MessageConsumer consumer;
    private final CharSequence publishTopic;
    private final GreenRuntime runtime;
    private final long decrementBy;

	private long lastValue;
		
    DecrementValueBehavior(GreenRuntime runtime, CharSequence publishTopic, long decrementBy) {
    	this.channel = runtime.newCommandChannel(DYNAMIC_MESSAGING);

    	// Process each field in order. Return false to stop processing.
		this.consumer = new MessageConsumer()
				            .integerProcessor(PubSubStructured.COUNT_DOWN_FIELD, value -> {
								lastValue = (int) value;
								return true;
							});
		
		this.publishTopic = publishTopic;
		this.runtime = runtime;
		this.decrementBy = decrementBy;
	}

	@Override
	public boolean message(CharSequence topic, BlobReader payload) {
		//
		////NOTE: this one line will copy messages from payload if consumer returns true
		////      when the message is copied its topic is changed to the first argument string
		//
		//cmd.copyStructuredTopic(publishTopic, payload, consumer);
		//
		// consumer.process returns the process chain return value
		if (consumer.process(payload)) {
			if (lastValue>0) {
				// If not zero, republish the message
				System.out.println(lastValue);
				return channel.publishStructuredTopic(publishTopic, writer -> {
					writer.writeLong(PubSubStructured.COUNT_DOWN_FIELD, lastValue-decrementBy);
					writer.writeUTF8(PubSubStructured.SENDER_FIELD, "from thing one behavior");
				});
			} else {
				// When zero, shutdown the system
				runtime.shutdownRuntime();
				return true;
			} 
		} else {
			return false;
		}
	}
}
