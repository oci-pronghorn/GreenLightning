package com.ociweb.gl.example.personApp;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class PostResponder implements PubSubListener {

	private final HTTPResponseService resp;

	public PostResponder(GreenRuntime runtime) {
		resp = runtime.newCommandChannel().newHTTPResponseService();
	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		return resp.publishHTTPResponse(
                 payload.structured().readLong(GreenField.connectionId),
				 payload.structured().readLong(GreenField.sequenceId), 
				 payload.structured().readInt(GreenField.status),
				 false,
				 HTTPContentTypeDefaults.JSON,
				 w-> {
					 
					ChannelReader reader = payload.structured().read(GreenField.payload);
					reader.readInto(w, reader.available());
														 
				 });
		
	}

}
