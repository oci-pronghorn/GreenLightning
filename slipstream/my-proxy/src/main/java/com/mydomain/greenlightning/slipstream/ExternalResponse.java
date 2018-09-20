package com.mydomain.greenlightning.slipstream;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponder;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class ExternalResponse implements PubSubListener, HTTPResponseListener {

	private HTTPResponder responder;
	
	public ExternalResponse(GreenRuntime runtime) {
		responder = new HTTPResponder(runtime.newCommandChannel(), 300);	

	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		return responder.readHandoffData(payload);
	}

	@Override
	public boolean responseHTTP(HTTPResponseReader reader) {

		if (reader.isConnectionClosed()) {
			return responder.closed();			
		} else {

			return responder.respondWith(200, !reader.isEndOfResponse(),
	
					(headWriter) -> {
						reader.structured().visit(HTTPHeader.class, (header,r,id)->{
							//we are a proxy, our response has its own length, connection and status
							if (header != HTTPHeaderDefaults.CONTENT_LENGTH 
								&& header != HTTPHeaderDefaults.CONNECTION
								&& header != HTTPHeaderDefaults.STATUS ) {								
								headWriter.write(header, reader.getSpec(), r);
							}
						});
					},
					reader.contentType(), 
					(payloadWriter) -> {
						payloadWriter.write(reader.structured().readPayload());
					});
			
		}

	}

}
