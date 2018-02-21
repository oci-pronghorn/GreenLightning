package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class RestResponder implements PubSubListener{

	private final GreenCommandChannel cmd;
	
	private ChannelReader payloadW;	
	private Writable w = new Writable() {

		@Override
		public void write(ChannelWriter writer) {
			writer.writePackedInt(payloadW.readPackedInt());
		}
		
	};

	public RestResponder(GreenRuntime runtime) {
		cmd = runtime.newCommandChannel();
		cmd.ensureHTTPServerResponse();
	}
	
	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		payloadW = payload;
		return cmd.publishHTTPResponse(
				payload.readPackedLong(), 
				payload.readPackedLong(), 
				200, false, HTTPContentTypeDefaults.BIN, w);
	}

}
