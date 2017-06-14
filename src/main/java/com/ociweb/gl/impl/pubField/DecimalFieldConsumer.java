package com.ociweb.gl.impl.pubField;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.util.hash.MurmurHash;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.math.Decimal;

public class DecimalFieldConsumer implements FieldConsumer {

	private final DecimalFieldProcessor processor;
	private final TrieParserReader reader;
	private byte e;
	private long m;	
	
	public DecimalFieldConsumer(DecimalFieldProcessor processor, TrieParserReader reader) {
		this.processor = processor;
		this.reader = reader;
	}
	
	public void store(long value) {	
		e = 0;
		m = value;
	}
	
	public void store(byte[] backing, int pos, int len, int mask) {
	
		if (TrieParserReader.query(reader, 
				                   DataInputBlobReader.textToNumberTrieParser(), 
			               		   backing, pos, len, mask)>=0) {
			
			this.e = TrieParserReader.capturedDecimalEField(reader,0);
			this.m = TrieParserReader.capturedDecimalMField(reader,0);
			
    	} else {
    		//TODO: We should use the Rabin Fingerprint to have a strong model of collision
    		this.e = 0;
    		this.m = MurmurHash.hash32(backing, pos, len, 314-579-0066);    		
    	}
	}
	
	public void store(byte e, long m) {
		this.e = e;
		this.m = m;
	}
	
	public void store(long numerator, long denominator) {
		byte zeros = 5;
		long accuracy = 10*zeros;
		this.m = (numerator*accuracy)/denominator;
		this.e = (byte) -zeros;
	}
		
	public boolean run() {
		return processor.process(e,m);
	}
	
}
