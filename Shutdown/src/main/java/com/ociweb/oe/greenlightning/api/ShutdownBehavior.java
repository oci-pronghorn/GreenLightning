package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.ShutdownListener;

public class ShutdownBehavior implements ShutdownListener, RestListener{

	private final GreenCommandChannel channel;
	private final byte[] KEY = "key".getBytes();
	private final byte[] PASS1 = "2709843294721594".getBytes();
	private final byte[] PASS2 = "A5E8F4D8C1B987EFCC00A".getBytes();
	
	private final GreenRuntime runtime;
  
	private boolean hasFirstKey;
	private boolean hasSecondKey;
	
    public ShutdownBehavior(GreenRuntime runtime) {
		this.channel = runtime.newCommandChannel(NET_RESPONDER);
		this.runtime = runtime;
	}
	    	
	@Override
	public boolean acceptShutdown() {
		return hasFirstKey & hasSecondKey;
	}
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {

		if (request.isEqual(KEY, PASS1)) {
			if (channel.publishHTTPResponse(request, 200)) {
				runtime.shutdownRuntime();
				hasFirstKey = true;
				return true;
			}
		} else if (hasFirstKey && request.isEqual(KEY, PASS2)) {
			if (channel.publishHTTPResponse(request, 200)) {
				hasSecondKey = true;
				return true;
			}
		} else {
			return channel.publishHTTPResponse(request, 404);
		}
		return false;
	}
	
}
