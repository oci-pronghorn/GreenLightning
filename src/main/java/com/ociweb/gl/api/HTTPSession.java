package com.ociweb.gl.api;

public class HTTPSession {

	public final String host;
	public final byte[] hostBytes;
	public final int port;
	public final int sessionId;
	
	//cache
	private long connectionId;
	
	public String toString() {
		return host+":"+port;
	}
	
	public HTTPSession(String host, int port, int sessionId) {
		this.host = host;
		this.hostBytes = host.getBytes();
		this.port = port;
		this.sessionId = sessionId;
	}
	
	public HTTPSession(String host, int port) {
		this.host = host;
		this.hostBytes = host.getBytes();
		this.port = port;
		this.sessionId = 0;
	}
	
	void setConnectionId(long id) {
		connectionId = id;		
	}
	
	long getConnectionId() {
		return connectionId;
	}
	
}
