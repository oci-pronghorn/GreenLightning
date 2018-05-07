package com.ociweb.gl.example.personApp;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class GetResponder implements PubSubListener {

	private final HTTPResponseService resp;

	public GetResponder(GreenRuntime runtime) {
		resp = runtime.newCommandChannel().newHTTPResponseService();
	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		return resp.publishHTTPResponse(payload.structured().readLong(GreenField.connectionId), 
									    payload.structured().readLong(GreenField.sequenceId), 
									    payload.structured().readInt(GreenField.status));
		
	}

}
