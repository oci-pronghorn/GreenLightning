package com.ociweb.gl.impl;
import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Headable;
import com.ociweb.gl.api.HeaderReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class HTTPPayloadReader<S extends MessageSchema<S>> extends PayloadReader<S> implements HeaderReader {

	protected IntHashTable headerHash; //look up index of the header we want from its header id
	protected int paraIndexCount; //how may fields to skip over before starting
	protected TrieParser headerTrieParser; //look up header id from the header string bytes
	protected TrieParserReader reader = new TrieParserReader(0, true);
	protected HTTPSpecification httpSpec;
	protected int payloadIndexOffset;
	
	private static final Logger logger = LoggerFactory.getLogger(HTTPPayloadReader.class);
	
	public int headerId(byte[] header) {		
		return (int)TrieParserReader.query(reader, headerTrieParser, 
				                           header, 0, header.length, Integer.MAX_VALUE);
	}

	public HTTPPayloadReader(Pipe<S> pipe) {
		super(pipe);
	}


	public boolean openHeaderData(byte[] header, Headable headReader) {
		return openHeaderData(headerId(header), headReader);
	}

	public boolean openHeaderData(int headerId, Headable headReader) {
	
		if (headerId>=0) {//is this a known header
			int item = IntHashTable.getItem(headerHash, HTTPHeader.HEADER_BIT | headerId);
			
			if (item!=0) { //index location for the header				
								
				int posFromStart = readFromEndLastInt(paraIndexCount + 1 + (0xFFFF & item));
				assert(posFromStart<=getBackingPipe(this).maxVarLen) : "index position "+posFromStart+" is out of bounds "+getBackingPipe(this).maxVarLen;
				assert(posFromStart>=0) : "index position must be zero or positive";
				
				//we look back 2 bytes to grab the short for this header
				assert(matchesHeader(headerId, posFromStart-2)) : "Index did not point to the expeced header";
				
				setPositionBytesFromStart(posFromStart);
				
				headReader.read(headerId, this);				
				
				return true;
			}
		}
		return false;
		
	}
	
	private boolean matchesHeader(int headerId, int idx) {
		setPositionBytesFromStart(idx);
		return (this.readShort() == headerId);
	}

	private int findHeaderOrdinal(int itemId) {
		final int offset = paraIndexCount + 1 + itemId;
		final int sizeOfHeaderId = 2;

		int headerFieldIdx = readFromEndLastInt(offset);

		if(headerFieldIdx > 0) {
			setPositionBytesFromStart(headerFieldIdx-sizeOfHeaderId);
			return readShort();
		}

		return -1;
	}

	public void visitHeaders(Headable headReader) {
		
		int item = IntHashTable.count(headerHash); //this is the payload position

		while (--item >= 0) {
				
			int headerOrdinal = findHeaderOrdinal(item);
			if(headerOrdinal >= 0) {
				headReader.read(headerOrdinal, this);
			}
		}		
	}
	
	public <A extends Appendable> A headers(A target) {		
		
		int item = IntHashTable.count(headerHash); //this is the payload position

		while (--item >= 0) {
				
			int headerOrdinal = findHeaderOrdinal(item);

			if (headerOrdinal >= 0) {
				httpSpec.writeHeader(target, headerOrdinal, this);
				try {
					target.append("\r\n");
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
		
		
		return target;
	}
	
	
	public boolean openPayloadData(Payloadable reader) {
		
		if (hasRemainingBytes()) {		
					
			setPositionBytesFromStart(readFromEndLastInt(payloadIndexOffset));
			reader.read(this);//even when we have zero length...
			return true;
		} else {
			return false;
		}
	}

	public HTTPSpecification getHttpSpecification() {
		return httpSpec;
	}
}
