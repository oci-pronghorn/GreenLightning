package com.ociweb.gl.api;

import com.ociweb.pronghorn.util.TrieParser;

public class GreenTokenMap {

	
	private final TrieParser tp;
	private int extractions = 0;
	
	public GreenTokenMap() {
		this(false);
	}
	
	public GreenTokenMap(boolean ignoreCase) {
		
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
		
	public int getSize() {
		return tp.getLimit();
	}

	/**
	 * Used to add ids and templates to GreenTokenMaps
	 * @param id long id to add to GreenTokenMap
	 * @param template CharSequence template to add to GreenTokenMap
	 */
	public GreenTokenMap add(long id, CharSequence template) {
		tp.setUTF8Value(template, id); 
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());		
		return this;
	}

	/**
	 * Used to add ids and templates to GreenTokenMaps
	 * @param id long id to add to GreenTokenMap
	 * @param templatePart1 CharSequence template to add to GreenTokenMap
	 * @param templatePart2 CharSequence template to add to GreenTokenMap
	 */
	public GreenTokenMap addTemplate(long id, CharSequence templatePart1, CharSequence templatePart2) {
		tp.setUTF8Value(templatePart1, templatePart2, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}

	/**
	 * Used to add ids and templates to GreenTokenMaps
	 * @param id long id to add to GreenTokenMap
	 * @param templatePart1 CharSequence template to add to GreenTokenMap
	 * @param templatePart2 CharSequence template to add to GreenTokenMap
	 * @param templatePart3 CharSequence template to add to GreenTokenMap
	 */
	public GreenTokenMap addTemplate(long id, CharSequence templatePart1, CharSequence templatePart2, CharSequence templatePart3) {
		tp.setUTF8Value(templatePart1, templatePart2, templatePart3, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}

	/**
	 * Used to add long id and template to GreenTokenMaps
	 * @param id long id to add to GreenTokenMap
	 * @param template byte[] template to add to GreenTokenMap
	 * @return token map
	 */
	public GreenTokenMap addTemplate(long id, byte[] template) {
		tp.setValue(template, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}
	
	public GreenTokenMap addTemplate(long id, byte[] template, int offset, int length) {
		tp.setValue(template, offset, length, Integer.MAX_VALUE, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}
	
	public GreenTokenMap addTemplate(long id, byte[] template, int offset, int length, int mask) {
		tp.setValue(template, offset, length, mask, id);
		extractions = Math.max(extractions, tp.lastSetValueExtractionCount());	
		return this;
	}
	
	
}
