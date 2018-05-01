package com.ociweb.gl.api;

@Deprecated
public class HTTPSession extends ClientHostPortInstance{
	
	public HTTPSession(String host, int port, int sessionId) {
		super(host, port, sessionId, null);
	}
	
	public HTTPSession(String host, int port) {
		super(host, port, null,null);
	}
}
