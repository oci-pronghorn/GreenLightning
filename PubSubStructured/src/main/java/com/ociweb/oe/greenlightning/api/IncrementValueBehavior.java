package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.field.MessageConsumer;

public class IncrementValueBehavior implements PubSubListener {
	private final GreenCommandChannel channel;
    private final MessageConsumer consumer;
    private final CharSequence publishTopic;
    private final GreenRuntime runtime;
    private final long stepSize = 1;
    private final AppendableProxy console;
    private final int limit = 100;
	private long lastValue;
		
    IncrementValueBehavior(GreenRuntime runtime, CharSequence publishTopic, AppendableProxy console) {
    	this.channel = runtime.newCommandChannel(DYNAMIC_MESSAGING);
    	this.console = console;
    	// Process each field in order. Return false to stop processing.
		this.consumer = new MessageConsumer()
				            .integerProcessor(PubSubStructured.VALUE_FIELD, value -> {
								lastValue = (int) value;
								return true;
							});
		
		this.publishTopic = publishTopic;
		this.runtime = runtime;
	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {

		if (consumer.process(payload)) {
			if (lastValue<=limit) {// If not zero, republish the message
				
				Appendables.appendValue(console, lastValue).append('\n');				
				
				return channel.publishStructuredTopic(publishTopic, writer -> {
					writer.writeLong(PubSubStructured.VALUE_FIELD, lastValue + stepSize);
					writer.writeUTF8(PubSubStructured.SENDER_FIELD, "from thing one behavior");
				});
			} else {
				runtime.shutdownRuntime();
				return true;
			} 
		} else {
			return false;
		}
	}
}
