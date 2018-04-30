package com.ociweb.gl.api;

import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class GreenTokenizer extends GreenExtractor {

	private final TrieParser tp;
	
	GreenTokenizer(TrieParser tp, int extractions) {
		super(new TrieParserReader(extractions, true));
		this.tp = tp;		
	}

	/**
	 *
	 * @param value arg of data type CharSequence
	 * @see TrieParserReader
	 * @return token
	 */
	public long tokenize(CharSequence value) {
		return TrieParserReader.query(tpr, tp, value);
	}

	/**
	 *
	 * @param source byte arg
	 * @param position int arg
	 * @param length int arg
	 * @see TrieParserReader
	 * @return token
	 */
	public long tokenize(byte[] source, int position, int length) {
		return TrieParserReader.query(tpr, tp, source, position, length, Integer.MAX_VALUE);
	}

	/**
	 *
	 * @param source byte arg
	 * @param position int arg
	 * @param length int arg
	 * @param mask int arg
	 * @see TrieParserReader
	 * @return token
	 */
	public long tokenize(byte[] source, int position, int length, int mask) {
		return TrieParserReader.query(tpr, tp, source, position, length, mask);
	}
	
	
}
