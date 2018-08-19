package com.ociweb.gl.api;

public class ClientHostPortConfig {
	public final String host;
	public final int port;
	private long timeoutNS = -1;

	public ClientHostPortConfig(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public ClientHostPortConfig setTimeoutNS(long timeoutNS) {
		this.timeoutNS = timeoutNS;
		return this;
	}

	public ClientHostPortInstance finish() {
		return new ClientHostPortInstance(host, port, null, timeoutNS);
	}

	public ExtractedJSONFieldsForClient parseJSON() {		
		return new ExtractedJSONFieldsForClientImpl(this, timeoutNS);
	}
}
