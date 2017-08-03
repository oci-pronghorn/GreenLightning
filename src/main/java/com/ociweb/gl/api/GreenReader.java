package com.ociweb.gl.api;

import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class GreenReader extends GreenExtractor {

	private final static boolean alwaysCompletePayloads = true;
	private final TrieParser tp;
	
	GreenReader(TrieParser tp, int extractions) {		
		super(new TrieParserReader(extractions, alwaysCompletePayloads));
		this.tp = tp;
	}

	@SuppressWarnings("unchecked")
	public void beginRead(BlobReader reader) {
		TrieParserReader.parseSetup(tpr, (DataInputBlobReader)reader);
	}
	
	@SuppressWarnings("unchecked")
	public void beginRead(BlobReader reader, int maxBytes) {
		TrieParserReader.parseSetup(tpr, (DataInputBlobReader)reader, maxBytes);
	}
	
	public long readToken() {
		return TrieParserReader.parseNext(tpr, tp);
	}
		
	public int skipByte() {
		return TrieParserReader.parseSkipOne(tpr);
	}
	
	public boolean hasMore() {		
		return TrieParserReader.parseHasContent(tpr);
	}
	
	
	public static GreenReader examplePrepare() {
		
		return new GreenParser()
				    .addTemplate(1234, "type: %b\n")
				    .addTemplate(3322, "age: %i\n")
	                .addTemplate(1,    " ") //white space
				    .newReader();
		
	}
	
	public static void exampleConsume(GreenReader reader, BlobReader blob) {
		
		reader.beginRead(blob);
		while (reader.hasMore()) {
			
			long token = reader.readToken();
			
			switch ((int)token) {
				case 1234: //this is a token id
					
					//may call methods here to capture extractions
					
					//copyExtractedUTF8ToAppendable(idx, target)
				    //extractedLong(idx)
				    
					break;
				default:
					//unknown
					reader.skipByte();
			}
			
			
			
		}
		
		
		
	}
	
}
