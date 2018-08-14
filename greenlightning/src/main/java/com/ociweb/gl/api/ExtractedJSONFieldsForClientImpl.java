package com.ociweb.gl.api;

import com.ociweb.json.JSONAccumRule;
import com.ociweb.json.JSONAligned;
import com.ociweb.json.JSONRequired;
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
	public <T extends Enum<T>> ExtractedJSONFieldsForClient stringField(JSONAligned isAligned, JSONAccumRule accumRule,
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
	public <T extends Enum<T>> ExtractedJSONFieldsForClient integerField(JSONAligned isAligned, JSONAccumRule accumRule,
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
	public <T extends Enum<T>> ExtractedJSONFieldsForClient decimalField(JSONAligned isAligned, JSONAccumRule accumRule,
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
	public <T extends Enum<T>> ExtractedJSONFieldsForClient booleanField(JSONAligned isAligned, JSONAccumRule accumRule,
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
			JSONRequired required, LongValidator validator) {
		Object temp = ex.integerField(extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient stringField(String extractionPath, T field,
			JSONRequired required, ByteSequenceValidator validator) {
		Object temp = ex.stringField(extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient decimalField(String extractionPath, T field,
			JSONRequired required, DecimalValidator validator) {
		Object temp = ex.decimalField(extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient integerField(JSONAligned isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, JSONRequired required, LongValidator validator) {
		Object temp = ex.integerField(isAligned, accumRule,extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient stringField(JSONAligned isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, JSONRequired required, ByteSequenceValidator validator) {
		Object temp = ex.stringField(isAligned, accumRule,extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient decimalField(JSONAligned isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, JSONRequired required, DecimalValidator validator) {
		Object temp = ex.decimalField(isAligned, accumRule,extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient booleanField(String extractionPath, T field,
			JSONRequired isRequired) {
		Object temp = ex.booleanField(extractionPath, field, isRequired);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}

	@Override
	public <T extends Enum<T>> ExtractedJSONFieldsForClient booleanField(JSONAligned isAligned, JSONAccumRule accumRule,
			String extractionPath, T field, JSONRequired isRequired) {
		Object temp = ex.booleanField(isAligned, accumRule, extractionPath, field, isRequired);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return this;
	}
}