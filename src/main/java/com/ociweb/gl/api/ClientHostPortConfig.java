package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractor;

public class ClientHostPortConfig {
	public final String host;
	public final int port;

	private JSONExtractor extractor;
	private Integer sessionId;

	public ClientHostPortConfig(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void setExtractor(JSONExtractor extractor) {
		this.extractor = extractor;
	}

	public void setSharedSessionId(int sessionId) {
		this.sessionId = sessionId;
	}

	public ClientHostPortInstance finish() {
		ClientHostPortInstance instance = new ClientHostPortInstance(host, port, sessionId, extractor);
		return instance;
	}
}
