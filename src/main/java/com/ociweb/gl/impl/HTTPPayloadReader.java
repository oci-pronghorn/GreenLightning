package com.ociweb.gl.impl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Headable;
import com.ociweb.gl.api.HeaderReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.pronghorn.network.config.HTTPHeader;
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
	private static final Logger logger = LoggerFactory.getLogger(HTTPPayloadReader.class);
	
	public int headerId(byte[] header) {		
		int result = (int)TrieParserReader.query(reader, headerTrieParser, header, 0, header.length, Integer.MAX_VALUE);
		return result;
	}

	public HTTPPayloadReader(Pipe<S> pipe) {
		super(pipe);

	}


	public boolean openHeaderData(byte[] header, Headable headReader) {
		return openHeaderData(headerId(header), headReader);
	}

	public boolean openHeaderData(int headerId, Headable headReader) {
	
		if (headerId>=0) {
			int item = IntHashTable.getItem(headerHash, HTTPHeader.HEADER_BIT | headerId);
			
			if (item!=0) {				
								
				int itemIndex = 0xFFFF & item;
				
				int posFromStart = readFromEndLastInt(paraIndexCount + 1 + itemIndex);
				assert(posFromStart<=getBackingPipe(this).maxVarLen) : "index position "+posFromStart+" is out of bounds "+getBackingPipe(this).maxVarLen;
				assert(posFromStart>=0) : "index position must be zero or positive";
				
				setPositionBytesFromStart(posFromStart);
				
				headReader.read(this);//HTTPRequestReader
				
				return true;
			}
		}
		return false;
		
	}
	
	public boolean openPayloadData(Payloadable reader) {
		
		if (hasRemainingBytes()) {		
			//logger.trace("reading the position of the body {} from position {} ",readFromEndLastInt(paraIndexCount + IntHashTable.count(headerHash)),paraIndexCount + IntHashTable.count(headerHash));
			setPositionBytesFromStart(readFromEndLastInt(paraIndexCount + IntHashTable.count(headerHash)));
			reader.read(this);//even when we have zero length...
			return true;
		} else {
			return false;
		}
	}



	
}
