package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class RestResponder implements PubSubListener{

	private final GreenCommandChannel cmd;

	public RestResponder(GreenRuntime runtime) {
		cmd = runtime.newCommandChannel();
		cmd.ensureHTTPServerResponse();
	}
	
	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		return cmd.publishHTTPResponse(
				payload.readPackedLong(), 
				payload.readPackedLong(), 
				200);
	}

}
