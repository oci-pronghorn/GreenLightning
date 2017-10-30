package com.ociweb.oe.greenlightning.api.server;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;

public class RestBehaviorHandoff implements RestListener {

		
	private final GreenCommandChannel cmd;
    private final String topic;
    
	public RestBehaviorHandoff(GreenRuntime runtime, String topic) {
		this.cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.topic = topic;
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		return cmd.publishTopic(topic, (writer)->{ 
			request.handoff(writer);			
		});
		
	}

}
