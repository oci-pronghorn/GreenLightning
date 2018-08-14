package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.ChannelWriter;
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

	/**
	 *
	 * @param idx int arg index number
	 * @param writer arg of data type ChannelWriter
	 * @return length
	 */
	public int copyExtractedBytesToWriter(int idx, ChannelWriter writer) {
		return TrieParserReader.capturedFieldBytes(tpr, idx, writer);
	}

	/**
	 *
	 * @param idx int arg index number
	 * @return length
	 */
	public int copyExtractedBytesLength(int idx) {
		return TrieParserReader.capturedFieldBytesLength(tpr, idx);
	}

	/**
	 *
	 * @param idx int arg index number
	 * @param writer
	 * @return length
	 */
	public int copyExtractedUTF8ToWriter(int idx, ChannelWriter writer) {
		return TrieParserReader.writeCapturedUTF8(tpr, idx, writer);
	}

    /**
     *
     * @param idx int arg index number
     * @param target
     * @param <A>
     * @return length
     */
	public <A extends Appendable> A copyExtractedUTF8ToAppendable(int idx, A target) {
		return TrieParserReader.capturedFieldBytesAsUTF8(tpr, idx, target);
	}

    /**
     *
     * @param idx int arg index number
     * @return text
     */
	public String extractedString(int idx) {
		return TrieParserReader.capturedFieldBytesAsUTF8(tpr, idx, new StringBuilder()).toString();
	}

    /**
     *
     * @param idx int arg index number
     * @return value
     */
	public long extractedLong(int idx) {
		return TrieParserReader.capturedLongField(tpr, idx);
	}

    /**
     *
     * @param idx int arg index number
     * @return value
     */
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

    /**
     *
     * @param idx int arg index number
     * @return extractedDouble
     */
	public double extractedDouble(int idx) {
				
		Decimal.sum(TrieParserReader.capturedDecimalMField(tpr, idx), 
				    TrieParserReader.capturedDecimalEField(tpr, idx),
				    
				    TrieParserReader.capturedDecimalMField(tpr, idx+1), 
				    TrieParserReader.capturedDecimalEField(tpr, idx+1),
				    
				    doubleConverter);
		return extractedDouble;
		
	}	
}
