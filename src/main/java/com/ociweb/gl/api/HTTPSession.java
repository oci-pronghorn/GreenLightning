package com.ociweb.gl.api;

public class HTTPSession {

	public final byte[] host;
	public final int port;
	public final int sessionId;
	
	//cache
	private long connectionId;
	
	public HTTPSession(String host, int port, int sessionId) {
		this.host = host.getBytes();
		this.port = port;
		this.sessionId = sessionId;
	}
	
	void setConnectionId(long id) {
		connectionId = id;		
	}
	
	long getConnectionId() {
		return connectionId;
	}
	
}
