package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.pronghorn.pipe.BlobReader;

public class CountBehavior implements PubSubMethodListener {

	private int count = 0;
    private final CharSequence publishTopic;
    private final GreenCommandChannel channel;
    private final GreenRuntime runtime;
	
	public CountBehavior(GreenRuntime runtime, CharSequence publishTopic) {
		this.channel = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.publishTopic = publishTopic;
		this.runtime = runtime;
	}


	public boolean triggerNextAndCount(CharSequence topic, BlobReader payload) {
		
		if(count<6) {
			
			boolean result = channel.publishTopic(publishTopic);
			if (result) {
				count++;
			}
			
			return result;
		} else {
			runtime.shutdownRuntime();
		}
		
		return true;
	}
	
	public boolean anotherMessage(CharSequence topic, BlobReader payload) {
		//do nothing, just here for example
		return true;
	}
	

}
