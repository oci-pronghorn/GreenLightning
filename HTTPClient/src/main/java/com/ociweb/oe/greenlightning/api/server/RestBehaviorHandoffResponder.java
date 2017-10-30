package com.ociweb.oe.greenlightning.api.server;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponder;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.AppendableProxy;

public class RestBehaviorHandoffResponder implements PubSubListener {

	HTTPResponder responder;
	
	public RestBehaviorHandoffResponder(GreenRuntime runtime, AppendableProxy console) {
		
		responder = new HTTPResponder(runtime.newCommandChannel(),256*1024);
				
	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		boolean result = responder.readReqesterData(payload);
		if (result) {
			responder.respondWith(200, false, HTTPContentTypeDefaults.TXT, (w)->{w.writeUTF("sent by responder");});
		}
		
		return result;
	}
	
}
