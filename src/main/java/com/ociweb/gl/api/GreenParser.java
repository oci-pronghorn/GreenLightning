package com.ociweb.gl.api;

import com.ociweb.pronghorn.util.TrieParser;

public class GreenParser {

	
	private final TrieParser tp;
	private int extractions = 0;
	
	public GreenParser() {
		this(false);
	}
	
	public GreenParser(boolean ignoreCase) {
		
		boolean skipDeepChecks = false;
		boolean supportsExtraction = true;
		tp = new TrieParser(128, 4, skipDeepChecks, supportsExtraction, ignoreCase);
				
	}	
	
	public GreenTokenizer newTokenizer() {
		return new GreenTokenizer(tp, extractions);
	}	
	
	public GreenReader newReader() {
		return new GreenReader(tp, extractions);
	}
		
	public GreenParser addTemplate(long id, CharSequence template) {
		tp.setUTF8Value(template, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());		
		return this;
	}
	
	public GreenParser addTemplate(long id, CharSequence templatePart1, CharSequence templatePart2) {
		tp.setUTF8Value(templatePart1, templatePart2, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}
	
	public GreenParser addTemplate(long id, CharSequence templatePart1, CharSequence templatePart2, CharSequence templatePart3) {
		tp.setUTF8Value(templatePart1, templatePart2, templatePart3, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}
	
	public GreenParser addTemplate(long id, byte[] template) {
		tp.setValue(template, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}
	
	public GreenParser addTemplate(long id, byte[] template, int offset, int length) {
		tp.setValue(template, offset, length, Integer.MAX_VALUE, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}
	
	public GreenParser addTemplate(long id, byte[] template, int offset, int length, int mask) {
		tp.setValue(template, offset, length, mask, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}
	
	
}
