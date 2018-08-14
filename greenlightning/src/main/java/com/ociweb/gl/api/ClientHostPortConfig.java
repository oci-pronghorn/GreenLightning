package com.ociweb.gl.api;

import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;

public class ClientHostPortConfig {
	public final String host;
	public final int port;

	private JSONExtractorCompleted extractor;

	public ClientHostPortConfig(String host, int port) {
		this.host = host;
		this.port = port;
	}

	@Deprecated
	public ClientHostPortConfig setExtractor(JSONExtractorCompleted extractor) {
		this.extractor = extractor;
		return this;
	}

	public ClientHostPortInstance finish() {
		return new ClientHostPortInstance(host, port, extractor);
	}

	public ExtractedJSONFieldsForClient parseJSON() {
		
		return new ExtractedJSONFieldsForClientImpl(this);
	}
}
