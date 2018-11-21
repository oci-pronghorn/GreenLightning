package com.ociweb.gl.impl;

import com.ociweb.json.JSONAccumRule;
import com.ociweb.json.JSONAligned;
import com.ociweb.json.JSONRequired;
import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.json.decode.JSONTable;
import com.ociweb.pronghorn.struct.ByteSequenceValidator;
import com.ociweb.pronghorn.struct.DecimalValidator;
import com.ociweb.pronghorn.struct.LongValidator;

public class JSONExtractorDefImpl<J extends JSONExtractorDefImpl> {

	
	protected JSONTable<JSONExtractor> ex = new JSONExtractor().begin();

	public <T extends Enum<T>> J stringField(JSONAligned isAligned, JSONAccumRule accumRule,
																String extractionPath, T field) {
								
		Object temp = ex.stringField(isAligned, accumRule, extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J stringField(String extractionPath, T field) {
		Object temp = ex.stringField(extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J integerField(JSONAligned isAligned, JSONAccumRule accumRule,
			String extractionPath, T field) {
		Object temp = ex.integerField(isAligned, accumRule, extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J integerField(String extractionPath, T field) {
		Object temp = ex.integerField(extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J decimalField(JSONAligned isAligned, JSONAccumRule accumRule,
			String extractionPath, T field) {
		Object temp = ex.decimalField(isAligned, accumRule, extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J decimalField(String extractionPath, T field) {
		Object temp = ex.decimalField(extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J booleanField(JSONAligned isAligned, JSONAccumRule accumRule,
			String extractionPath, T field) {
		Object temp = ex.booleanField(isAligned, accumRule, extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J booleanField(String extractionPath, T field) {
		Object temp = ex.booleanField(extractionPath, field);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J integerField(String extractionPath, T field,
			JSONRequired required, LongValidator validator) {
		Object temp = ex.integerField(extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J stringField(String extractionPath, T field,
			JSONRequired required, ByteSequenceValidator validator) {
		Object temp = ex.stringField(extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J decimalField(String extractionPath, T field,
			JSONRequired required, DecimalValidator validator) {
		Object temp = ex.decimalField(extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J integerField(JSONAligned isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, JSONRequired required, LongValidator validator) {
		Object temp = ex.integerField(isAligned, accumRule, extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J stringField(JSONAligned isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, JSONRequired required, ByteSequenceValidator validator) {
		
		Object temp = ex.stringField(isAligned, accumRule, extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J decimalField(JSONAligned isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, JSONRequired required, DecimalValidator validator) {
		Object temp = ex.decimalField(isAligned, accumRule, extractionPath, field, required, validator);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J booleanField(String extractionPath, T field,
			JSONRequired isRequired) {
		Object temp = ex.booleanField(extractionPath, field, isRequired);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}

	public <T extends Enum<T>> J booleanField(JSONAligned isAligned,
			JSONAccumRule accumRule, String extractionPath, T field, JSONRequired isRequired) {
		Object temp = ex.booleanField(isAligned, accumRule, extractionPath, field, isRequired);
		assert(temp == ex) : "internal error, the same instance should have been returned";
		return (J)this;
	}
	
}
