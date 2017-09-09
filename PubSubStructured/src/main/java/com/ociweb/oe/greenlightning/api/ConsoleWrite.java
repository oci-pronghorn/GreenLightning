package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.field.MessageConsumer;

public class ConsoleWrite implements PubSubListener {

	private MessageConsumer consumer;	
	public ConsoleWrite(AppendableProxy console) {

		this.consumer = new MessageConsumer()
	            .integerProcessor(PubSubStructured.VALUE_FIELD, value -> {
	                Appendables.appendValue(console, value).append('\n');	            	
					return true;
				});
	}

	@Override
	public boolean message(CharSequence topic, BlobReader payload) {		
		consumer.process(payload);
		return true;
	}

}
