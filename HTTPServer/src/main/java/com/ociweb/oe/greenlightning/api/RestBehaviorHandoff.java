package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.RestListener;

public class RestBehaviorHandoff implements RestListener {

		
	private final PubSubFixedTopicService cmd;
    
	public RestBehaviorHandoff(GreenRuntime runtime, String topic) {
		this.cmd = runtime.newCommandChannel().newPubSubService(topic);
	
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		return cmd.publishTopic((writer)->{ 
			request.handoff(writer);			
		});
		
	}

}
