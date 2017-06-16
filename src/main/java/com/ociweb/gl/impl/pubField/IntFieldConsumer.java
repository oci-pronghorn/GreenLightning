package com.ociweb.gl.impl.pubField;

import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.util.hash.MurmurHash;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.math.Decimal;

public class IntFieldConsumer implements FieldConsumer {

	private final IntegerFieldProcessor processor;
	private final TrieParserReader reader;
	private long value;
	
	public IntFieldConsumer(IntegerFieldProcessor processor, TrieParserReader reader) {
		this.processor = processor;
		this.reader = reader;
	}
	
	public void store(long value) {	
		this.value = value;
	}
	
	public void store(byte[] backing, int pos, int len, int mask) {
	
		if (TrieParserReader.query(reader, 
				                   DataInputBlobReader.textToNumberTrieParser(), 
			               		   backing, pos, len, mask)>=0) {
			
			byte e = TrieParserReader.capturedDecimalEField(reader,0);
			long m = TrieParserReader.capturedDecimalMField(reader,0);
			
    		this.value = Decimal.asLong(m, e);
    	} else {
    		//TODO: We should use the Rabin Fingerprint to have a strong model of collision
    		this.value = MurmurHash.hash32(backing, pos, len, 314-579-0066);//negative hash
    	}
	}
	
	public void store(byte e, long m) {
		this.value = Decimal.asLong(m, e);
	}
	
	public void store(long numerator, long denominator) {
		this.value = numerator/denominator;
	}
		
	public boolean run() {
		return processor.process(value);
	}
	
}
