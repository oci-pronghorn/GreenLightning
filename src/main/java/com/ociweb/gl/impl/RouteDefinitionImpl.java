package com.ociweb.gl.impl;

import com.ociweb.gl.api.ExtractedJSONFieldsForRoute;
import com.ociweb.gl.api.RouteDefinition;
import com.ociweb.json.JSONAccumRule;
import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.json.decode.JSONTable;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.http.CompositeRoute;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStageConfig;
import com.ociweb.pronghorn.struct.ByteSequenceValidator;
import com.ociweb.pronghorn.struct.DecimalValidator;
import com.ociweb.pronghorn.struct.LongValidator;

final class RouteDefinitionImpl implements RouteDefinition {

	private final HTTP1xRouterStageConfig<?,?,?,?> config;
	private final HTTPHeader[] headers;
	private CompositeRoute route = null;

	RouteDefinitionImpl(HTTP1xRouterStageConfig<?,?,?,?> config, HTTPHeader[] headers) {
		this.config = config;
		this.headers = headers;
	}

	@Override
	public CompositeRoute path(CharSequence path) {
		return (null==route) ? this.config.registerCompositeRoute(headers).path(path) :  route;
	}

	@Override
	public ExtractedJSONFieldsForRoute parseJSON() {
						
		return new ExtractedJSONFieldsForRoute() {

			@Override
			public CompositeRoute path(CharSequence path) {
				return route = RouteDefinitionImpl.this.config.registerCompositeRoute(ex.finish(), headers).path(path);
			}					
			
			JSONTable<JSONExtractor> ex = new JSONExtractor().begin();
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute stringField(boolean isAligned, JSONAccumRule accumRule,
																		String extractionPath, T field) {
										
				Object temp = ex.stringField(isAligned, accumRule, extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute stringField(String extractionPath, T field) {
				Object temp = ex.stringField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}

			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute integerField(boolean isAligned, JSONAccumRule accumRule,
					String extractionPath, T field) {
				Object temp = ex.integerField(isAligned, accumRule, extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute integerField(String extractionPath, T field) {
				Object temp = ex.integerField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute decimalField(boolean isAligned, JSONAccumRule accumRule,
					String extractionPath, T field) {
				Object temp = ex.decimalField(isAligned, accumRule, extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute decimalField(String extractionPath, T field) {
				Object temp = ex.decimalField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute booleanField(boolean isAligned, JSONAccumRule accumRule,
					String extractionPath, T field) {
				Object temp = ex.booleanField(isAligned, accumRule, extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
			
			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute booleanField(String extractionPath, T field) {
				Object temp = ex.booleanField(extractionPath, field);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}

			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute integerField(String extractionPath, T field,
					LongValidator validator) {
				Object temp = ex.integerField(extractionPath, field, validator);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}

			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute stringField(String extractionPath, T field,
					ByteSequenceValidator validator) {
				Object temp = ex.stringField(extractionPath, field, validator);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}

			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute decimalField(String extractionPath, T field,
					DecimalValidator validator) {
				Object temp = ex.decimalField(extractionPath, field, validator);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}

			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute integerField(boolean isAligned,
					JSONAccumRule accumRule, String extractionPath, T field, LongValidator validator) {
				Object temp = ex.integerField(isAligned, accumRule, extractionPath, field, validator);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}

			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute stringField(boolean isAligned,
					JSONAccumRule accumRule, String extractionPath, T field, ByteSequenceValidator validator) {
				
				Object temp = ex.stringField(isAligned, accumRule, extractionPath, field, validator);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}

			@Override
			public <T extends Enum<T>> ExtractedJSONFieldsForRoute decimalField(boolean isAligned,
					JSONAccumRule accumRule, String extractionPath, T field, DecimalValidator validator) {
				Object temp = ex.decimalField(isAligned, accumRule, extractionPath, field, validator);
				assert(temp == ex) : "internal error, the same instance should have been returned";
				return this;
			}
		};
	}	
}