package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestService;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.StartupListener;

public class HTTPGetBehaviorChained implements StartupListener {
	
	private HTTPRequestService cmd;

    private ClientHostPortInstance session;

	private PubSubService x;
	
	public HTTPGetBehaviorChained(GreenRuntime runtime, ClientHostPortInstance session) {
		GreenCommandChannel newCommandChannel = runtime.newCommandChannel();
		this.cmd = newCommandChannel.newHTTPClientService();
		this.x = newCommandChannel.newPubSubService();
		
		this.session = session;
	}

	@Override
	public void startup() {
		
		cmd.httpGet(session, "/testPageB");
		
	}

}
