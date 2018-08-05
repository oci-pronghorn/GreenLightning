package com.ociweb.gl.api;

import com.ociweb.json.JSONAccumRule;
import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.json.decode.JSONTable;
import com.ociweb.pronghorn.struct.ByteSequenceValidator;
import com.ociweb.pronghorn.struct.DecimalValidator;
import com.ociweb.pronghorn.struct.LongValidator;

final class ExtractedJSONFieldsForClientImpl implements ExtractedJSONFieldsForClient {

	private final ClientHostPortConfig clientHostPortConfig;

	/**
	 * @param clientHostPortConfig
	 */
	ExtractedJSONFieldsForClientImpl(ClientHostPortConfig clientHostPortConfig) {
		this.clientHostPortConfig = clientHostPortConfig;
	}

	JSONTable<JSONExtractor> ex = new JSONExtractor().begin();

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient stringField(boolean isAligned, JSONAccumRule accumRule,
																String extractionPath, T field) {
		Object temp = ex.stringField(isAligned, accumRule, extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient stringField(String extractionPath, T field) {
		Object temp = ex.stringField(extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient integerField(boolean isAligned, JSONAccumRule accumRule,
			String extractionPath, T field) {
		Object temp = ex.integerField(isAligned, accumRule,extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient integerField(String extractionPath, T field) {
		Object temp = ex.integerField(extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient decimalField(boolean isAligned, JSONAccumRule accumRule,
			String extractionPath, T field) {
		Object temp = ex.decimalField(isAligned, accumRule, extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient decimalField(String extractionPath, T field) {
		Object temp = ex.decimalField(extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient booleanField(boolean isAligned, JSONAccumRule accumRule,
			String extractionPath, T field) {
		Object temp = ex.booleanField(isAligned, accumRule, extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient booleanField(String extractionPath, T field) {
		Object temp = ex.booleanField(extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public ClientHostPortInstance finish() {
		return new ClientHostPortInstance(this.clientHostPortConfig.host, this.clientHostPortConfig.port, ex.finish());
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient integerField(String extractionPath, T field,
			LongValidator validator) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient stringField(String extractionPath, T field,
			ByteSequenceValidator validator) {
		Object temp = ex.stringField(extractionPath, field, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient decimalField(String extractionPath, T field,
			DecimalValidator validator) {
		Object temp = ex.decimalField(extractionPath, field, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient integerField(boolean isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, LongValidator validator) {
		Object temp = ex.integerField(isAligned, accumRule,extractionPath, field, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient stringField(boolean isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, ByteSequenceValidator validator) {
		Object temp = ex.stringField(isAligned, accumRule,extractionPath, field, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient decimalField(boolean isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, DecimalValidator validator) {
		Object temp = ex.decimalField(isAligned, accumRule,extractionPath, field, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}
}