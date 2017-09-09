package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.HTTPSession;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;

public class HTTPGetBehaviorChained implements StartupListener {
	
	private GreenCommandChannel cmd;
	private int responseId;
    private HTTPSession session = new HTTPSession("www.objectcomputing.com",80,0);
	
	public HTTPGetBehaviorChained(GreenRuntime runtime, int responseId) {
		this.cmd = runtime.newCommandChannel(NET_REQUESTER);
		this.responseId = responseId;
	}

	@Override
	public void startup() {
		
		cmd.httpGet(session, "/", responseId);
		
	}

}
