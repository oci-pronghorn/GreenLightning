package com.mydomain.greenlightning.slipstream;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.StructuredReader;

public class RestResponder implements PubSubListener {

	private HTTPResponseService responseService;

	public RestResponder(GreenRuntime runtime) {		
		responseService = runtime.newCommandChannel().newHTTPResponseService();		
	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		StructuredReader struct = payload.structured();
		return responseService.publishHTTPResponse(
				struct.readLong(Field.CONNECTION),
				struct.readLong(Field.SEQUENCE),
				struct.readInt(Field.STATUS),
				HTTPContentTypeDefaults.JSON, w->{
					struct.readText(Field.PAYLOAD, w);
				});
	}
	
}
