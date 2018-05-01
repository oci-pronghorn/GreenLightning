package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractor;

public class ClientHostPortConfig {
	public final String host;
	public final int port;

	private JSONExtractor extractor;
	private int sessionId;
	private boolean didSetSessionId;

	public ClientHostPortConfig(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void setExtractor(JSONExtractor extractor) {
		this.extractor = extractor;
	}

	public void setSharedSessionId(int sessionId) {
		this.sessionId = sessionId;
		this.didSetSessionId = true;
	}

	public ClientHostPortInstance finish() {
		if (didSetSessionId) {
			return new ClientHostPortInstance(host, port, sessionId, extractor);
		}
		return new ClientHostPortInstance(host, port, extractor);
	}
}
