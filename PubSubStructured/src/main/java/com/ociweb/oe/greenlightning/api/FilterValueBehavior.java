package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.util.field.MessageConsumer;

public class FilterValueBehavior implements PubSubListener {

	private final MessageConsumer consumer;
	private GreenCommandChannel channel;
	private String publishTopic;
	
	public FilterValueBehavior(GreenRuntime runtime, String publishTopic) {
		
		this.channel = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.publishTopic = publishTopic;
		this.consumer = new MessageConsumer()
	            .integerProcessor(PubSubStructured.VALUE_FIELD, value -> {
					return 0==(value%3);
				});
			
	}

	@Override
	public boolean message(CharSequence topic, BlobReader payload) {
		
		//copy returns if the message was consumed as boolean, 
		//it may or may not have been copied based on consumer rules
		return channel.copyStructuredTopic(publishTopic, payload, consumer);

	}

}
