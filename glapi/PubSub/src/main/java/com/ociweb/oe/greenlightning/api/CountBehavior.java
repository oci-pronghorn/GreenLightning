package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class CountBehavior implements PubSubMethodListener {

	private int count = 0;

    private final PubSubFixedTopicService channel;
    private final GreenRuntime runtime;
	private final boolean doShutdown = true;
    
	public CountBehavior(GreenRuntime runtime, CharSequence publishTopic) {
		this.channel = runtime.newCommandChannel().newPubSubService(publishTopic.toString());

		this.runtime = runtime;
	}


	public boolean triggerNextAndCount(CharSequence topic, ChannelReader payload) {
		
		if(count<6) {
			
			boolean result = channel.publishTopic();
			if (result) {
				count++;
			}
			
			return result;
		} else {
			if (doShutdown) {
				runtime.shutdownRuntime();
			}
		}
		
		return true;
	}
	
	public boolean anotherMessage(CharSequence topic, ChannelReader payload) {
		//do nothing, just here for example
		return true;
	}
	

}
