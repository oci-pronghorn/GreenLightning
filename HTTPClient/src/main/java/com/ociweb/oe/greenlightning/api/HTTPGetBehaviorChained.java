package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.HTTPSession;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;

public class HTTPGetBehaviorChained implements StartupListener {
	
	private GreenCommandChannel cmd;
	private int responseId;
    private HTTPSession session;
	
	public HTTPGetBehaviorChained(GreenRuntime runtime, HTTPSession session) {
		this.cmd = runtime.newCommandChannel(NET_REQUESTER);
		this.session = session;
	}

	@Override
	public void startup() {
		
		cmd.httpGet(session, "/testPageB");
		
	}

}
