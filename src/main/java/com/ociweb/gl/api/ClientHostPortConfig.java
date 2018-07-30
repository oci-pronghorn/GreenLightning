package com.ociweb.gl.api;

import com.ociweb.json.JSONAccumRule;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.json.decode.JSONTable;

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
		
		return new ExtractedJSONFieldsForClient() {
			JSONTable<JSONExtractor> ex = new JSONExtractor().begin();
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForClient stringField(boolean isAligned, JSONAccumRule accumRule,
																		String extractionPath, T field) {
				Object temp = ex.element(JSONType.TypeString, isAligned, accumRule).asField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForClient stringField(String extractionPath, T field) {
				Object temp = ex.element(JSONType.TypeString, false, null).asField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}

			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForClient integerField(boolean isAligned, JSONAccumRule accumRule,
					String extractionPath, T field) {
				Object temp = ex.element(JSONType.TypeInteger, isAligned, accumRule).asField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForClient integerField(String extractionPath, T field) {
				Object temp = ex.element(JSONType.TypeInteger, false, null).asField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForClient decimalField(boolean isAligned, JSONAccumRule accumRule,
					String extractionPath, T field) {
				Object temp = ex.element(JSONType.TypeDecimal, isAligned, accumRule).asField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForClient decimalField(String extractionPath, T field) {
				Object temp = ex.element(JSONType.TypeDecimal, false, null).asField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForClient booleanField(boolean isAligned, JSONAccumRule accumRule,
					String extractionPath, T field) {
				Object temp = ex.element(JSONType.TypeBoolean, isAligned, accumRule).asField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForClient booleanField(String extractionPath, T field) {
				Object temp = ex.element(JSONType.TypeBoolean, false, null).asField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}

			@Override
			public ClientHostPortInstance finish() {
				return new ClientHostPortInstance(host, port, ex.finish());
			}
			
		};
	}
}
