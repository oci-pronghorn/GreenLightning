package com.ociweb.gl.api;

import java.util.concurrent.atomic.AtomicInteger;

import com.ociweb.json.JSONExtractor;

public class ClientHostPortInstance {

	public static AtomicInteger sessionCounter = new AtomicInteger(0);
	
	public final String host;
	public final byte[] hostBytes;
	public final int port;
	public final int sessionId;
	public final int uniqueId;
	
	public final JSONExtractor extractor;
		
	//cache
	private long connectionId=-1;
	
	public String toString() {
		return host+":"+port;
	}
	
	public ClientHostPortInstance(String host, int port, int sessionId) {
		this(host,port,null,sessionId);
	}
	
	public ClientHostPortInstance(String host, int port) {
		this(host,port,null);
	}
	
	public ClientHostPortInstance(String host, int port, JSONExtractor extractor, int sessionId) {
		this.uniqueId = sessionCounter.incrementAndGet();
		this.host = host;
		this.hostBytes = host.getBytes();
		this.port = port;
		this.sessionId = sessionId;
		this.extractor = null;
	}
	
	public ClientHostPortInstance(String host, int port, JSONExtractor extractor) {
		this.uniqueId = sessionCounter.incrementAndGet();
		this.host = host;
		this.hostBytes = host.getBytes();
		this.port = port;
		this.sessionId = 0;
		this.extractor = null;
	}
	
	void setConnectionId(long id) {
		connectionId = id;		
	}
	
	long getConnectionId() {
		return connectionId;
	}

	public static int getSessionCount() {
		return sessionCounter.get();
	}
	
}
