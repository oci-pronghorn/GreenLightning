package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.MessageReader;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.pipe.BlobReader;

public class CountBehavior implements PubSubListener {

	public static int count = 0;
    private final CharSequence publishTopic;
	final GreenCommandChannel channel2;
	
	public CountBehavior(GreenRuntime runtime, CharSequence publishTopic) {

		channel2 = runtime.newCommandChannel(DYNAMIC_MESSAGING);;
		
		this.publishTopic = publishTopic;
	}


	@Override
	public boolean message(CharSequence topic, BlobReader payload) {
		count++;
		
		if(count<7){
			return channel2.publishTopic(publishTopic);
		}
		
		return true;
	}

}
