package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;

public class ShutdownRestListener implements RestListener{

	public GreenRuntime runtime;
	private GreenCommandChannel cmd;
	private final byte[] key = "key".getBytes();
	private final byte[] pass = "shutdown".getBytes();
	
	
	public ShutdownRestListener(GreenRuntime runtime) {
		this.runtime = runtime;
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);		
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isEqual(key, pass)) {
			if (cmd.publishHTTPResponse(request, 200)) {		
				runtime.shutdownRuntime();		
				return true;
			} 
			return false;
		} else {
			if (cmd.publishHTTPResponse(request, 404)) {	
				return true;
			} 
			return false;
		}
	}

}
