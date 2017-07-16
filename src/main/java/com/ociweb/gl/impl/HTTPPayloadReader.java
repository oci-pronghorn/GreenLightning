package com.ociweb.gl.impl;
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
	
	
	public int headerId(byte[] header) {		
		int result = (int)TrieParserReader.query(reader, headerTrieParser, header, 0, header.length, Integer.MAX_VALUE);
		//System.out.println(result+"  "+new String(header));
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
				setPositionBytesFromStart(readFromEndLastInt(paraIndexCount + 1 + (0xFFFF & item)));
				
				headReader.read(this);//HTTPRequestReader
				
				return true;
			}
		}
		return false;
		
	}
	
	public boolean openPayloadData(Payloadable reader) {
		setPositionBytesFromStart(readFromEndLastInt(paraIndexCount + IntHashTable.count(headerHash)));
		reader.read(this);//even when we have zero length...
		return true;
	}



	
}
