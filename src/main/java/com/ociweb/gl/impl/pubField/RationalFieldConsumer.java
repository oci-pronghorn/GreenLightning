package com.ociweb.gl.impl.pubField;

import com.ociweb.pronghorn.util.TrieParserReader;

public class RationalFieldConsumer implements FieldConsumer {

	private final RationalFieldProcessor processor;
	private final TrieParserReader reader;
	
	private long numerator;
	private long denominator;
	
	
	public RationalFieldConsumer(RationalFieldProcessor processor, TrieParserReader reader) {
		this.processor = processor;
		this.reader = reader;
	}
	
	public void store(long value) {	
		numerator = value;
		denominator = 1;
	}
	
	public void store(byte[] backing, int pos, int len, int mask) {

		//TODO: parse fraction...
		
//		if (TrieParserReader.query(reader, 
//				                   DataInputBlobReader.textToNumberTrieParser(), 
//			               		   backing, pos, len, mask)>=0) {
//			
//			this.e = TrieParserReader.capturedDecimalEField(reader,0);
//			this.m = TrieParserReader.capturedDecimalMField(reader,0);
//			
//    	} else {
//    		//TODO: We should use the Rabin Fingerprint to have a strong model of collision
//    		this.e = 0;
//    		this.m = MurmurHash.hash32(backing, pos, len, 314-579-0066);    		
//    	}
	}
	
	public void store(byte e, long m) {
		//TODO: take fraction converter code
		
	}
	
	public void store(long numerator, long denominator) {
		this.numerator = numerator;
		this.denominator = denominator;
	}
		
	public boolean run() {
		return processor.process(numerator, denominator);
	}
	
}
