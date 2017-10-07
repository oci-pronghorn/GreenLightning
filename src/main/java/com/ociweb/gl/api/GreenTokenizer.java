package com.ociweb.gl.api;

import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class GreenTokenizer extends GreenExtractor {

	private final TrieParser tp;
	
	GreenTokenizer(TrieParser tp, int extractions) {
		super(new TrieParserReader(extractions, true));
		this.tp = tp;		
	}
	
	public long tokenize(CharSequence value) {
		return TrieParserReader.query(tpr, tp, value);
	}

	public long tokenize(byte[] source, int position, int length) {
		return TrieParserReader.query(tpr, tp, source, position, length, Integer.MAX_VALUE);
	}
	
	public long tokenize(byte[] source, int position, int length, int mask) {
		return TrieParserReader.query(tpr, tp, source, position, length, mask);
	}
	
	
}
