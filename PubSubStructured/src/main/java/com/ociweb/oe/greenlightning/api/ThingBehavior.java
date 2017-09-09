package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.util.field.MessageConsumer;

public class ThingBehavior implements PubSubListener {

	private final GreenCommandChannel cmd;
    private final MessageConsumer consumer;
    private int lastValue;
    private final CharSequence publishTopic;
    private final GreenRuntime runtime;
		
    public ThingBehavior(GreenRuntime runtime, CharSequence topic) {
    	this.cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);

		this.consumer = new MessageConsumer()
				            .integerProcessor(PubSubStructured.VALUE_FIELD, 
				            		(value)->{
				            			lastValue = (int)value;
				            			return true;
				            	});
		
		this.publishTopic = topic;
		this.runtime = runtime;
	}
    
    
	@Override
	public boolean message(CharSequence topic, BlobReader payload) {
					
		//
		////NOTE: this one line will copy messages from payload if consumer returns true
		////      when the message is copied its topic is changed to the first argument string
		//
		//cmd.copyStructuredTopic("outgoing topic", payload, consumer);
		//
		
		if (consumer.process(payload)) {
			if (lastValue>0) {
				System.out.println(lastValue);
				return cmd.publishStructuredTopic(publishTopic, (writer)->{
					writer.writeLong(PubSubStructured.VALUE_FIELD, lastValue-1);
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
