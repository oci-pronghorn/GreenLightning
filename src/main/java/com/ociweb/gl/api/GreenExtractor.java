package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.BlobWriter;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.math.Decimal;
import com.ociweb.pronghorn.util.math.DecimalResult;

public class GreenExtractor {

	protected final TrieParserReader tpr;
	
	protected GreenExtractor(TrieParserReader tpr) {
		this.tpr = tpr;
	}

	public int extractedFieldCount() {		
		return TrieParserReader.capturedFieldCount(tpr);		
	}
	
	public int copyExtractedBytesToWriter(int idx, BlobWriter writer) {
		return TrieParserReader.capturedFieldBytes(tpr, idx, writer);
	}
	
	public int copyExtractedBytesLength(int idx) {
		return TrieParserReader.capturedFieldBytesLength(tpr, idx);
	}
	
	public int copyExtractedUTF8ToWriter(int idx, BlobWriter writer) {
		return TrieParserReader.writeCapturedUTF8(tpr, idx, writer);
	}
	
	public <A extends Appendable> A copyExtractedUTF8ToAppendable(int idx, A target) {
		return TrieParserReader.capturedFieldBytesAsUTF8(tpr, idx, target);
	}
	
	public String extractedString(int idx) {
		return TrieParserReader.capturedFieldBytesAsUTF8(tpr, idx, new StringBuilder()).toString();
	}
	
	public long extractedLong(int idx) {
		return TrieParserReader.capturedLongField(tpr, idx);
	}
	
	public long extractedDecimalMantissa(int idx) {
		return TrieParserReader.capturedDecimalMField(tpr, idx);
	}
	
	public long extractedDecimalExponent(int idx) {
		return TrieParserReader.capturedDecimalEField(tpr, idx);
	}
	
	private double extractedDouble;
	private final DecimalResult doubleConverter = new DecimalResult() {		
		@Override
		public void result(long m, byte e) {
			extractedDouble = Decimal.asDouble(m,e);
		}
	};
	
	public double extractedDouble(int idx) {
				
		Decimal.sum(TrieParserReader.capturedDecimalMField(tpr, idx), 
				    TrieParserReader.capturedDecimalEField(tpr, idx),
				    
				    TrieParserReader.capturedDecimalMField(tpr, idx+1), 
				    TrieParserReader.capturedDecimalEField(tpr, idx+1),
				    
				    doubleConverter);
		return extractedDouble;
		
	}	
}
