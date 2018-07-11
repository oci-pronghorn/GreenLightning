package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractorCompleted;

public class ClientHostPortConfig {
	public final String host;
	public final int port;

	private JSONExtractorCompleted extractor;

	public ClientHostPortConfig(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public ClientHostPortConfig setExtractor(JSONExtractorCompleted extractor) {
		this.extractor = extractor;
		return this;
	}

	public ClientHostPortInstance finish() {
		return new ClientHostPortInstance(host, port, extractor);
	}
}
