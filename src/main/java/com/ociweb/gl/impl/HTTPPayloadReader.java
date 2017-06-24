package com.ociweb.gl.impl;
import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HeaderReader;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class HTTPPayloadReader<S extends MessageSchema<S>> extends PayloadReader<S> implements HTTPFieldReader<S>, HeaderReader {

	private IntHashTable headerHash;
	private int paraIndexCount;
	private int revisionId;
	private int requestContext;
	private HTTPVerbDefaults verb;
	private int routeId;
	private long connectionId;
	private long sequenceCode;

	private TrieParser headerTrieParser;
	private TrieParserReader reader = new TrieParserReader(0, true);
	
	
	public int headerId(byte[] header) {		
		int result = (int)TrieParserReader.query(reader, headerTrieParser, header, 0, header.length, Integer.MAX_VALUE);
		//System.out.println(result+"  "+new String(header));
		return result;
	}

	public HTTPPayloadReader(Pipe<S> pipe) {
		super(pipe);

	}
	
	public void setHeaderTable(IntHashTable table, int paraIndexCount, TrieParser headerTrieParser) {
		this.headerHash = table;
		this.paraIndexCount = paraIndexCount;
		this.headerTrieParser = headerTrieParser;
	}

	public boolean openHeaderData(byte[] header, Headable<S> headReader) {
		return openHeaderData(headerId(header), headReader);
	}

	public boolean openHeaderData(int headerId, Headable<S> headReader) {
	
		if (headerId>=0) {
			int item = IntHashTable.getItem(headerHash, HTTPHeader.HEADER_BIT | headerId);
			
			if (item!=0) {				
				setPositionBytesFromStart(readFromEndLastInt(paraIndexCount + 1+ (0xFFFF & item)));
				
				headReader.read(this);
				
				return true;
			}
		}
		return false;
		
	}
	
	public boolean openPayloadData(Payloadable<S> reader) {
		setPositionBytesFromStart(readFromEndLastInt(paraIndexCount + IntHashTable.count(headerHash)));
		reader.read(this);//even when we have zero length...
		return true;
	}

	public void setRevisionId(int value) {
		revisionId = value;
	}

	public void setRequestContext(int value) {
		requestContext = value;
	}
	
	public int getRevisionId() {
		return revisionId;
	}
	
	public int getRequestContext() {
		return requestContext;
	}
	
	public void setRouteId(int routeId) {
		this.routeId = routeId;
	}

	public void setConnectionId(long connectionId, long sequenceCode) {
		this.connectionId = connectionId;
		this.sequenceCode = sequenceCode;
	}

	public int getRouteId() {
		return routeId;
	}
	
	public long getConnectionId() {
		return connectionId;
	}
	
	public long getSequenceCode() {
		return sequenceCode;
	}
	
	public void setVerb(HTTPVerbDefaults verb) {
		this.verb = verb;
	}
	
	public boolean isVerbGet() {
		return HTTPVerbDefaults.GET == verb;
	}
	
	public boolean isVerbConnect() {
		return HTTPVerbDefaults.CONNECT == verb;
	}
	
	public boolean isVerbDelete() {
		return HTTPVerbDefaults.DELETE == verb;
	}

	public boolean isVerbHead() {
		return HTTPVerbDefaults.HEAD == verb;
	}

	public boolean isVerbOptions() {
		return HTTPVerbDefaults.OPTIONS == verb;
	}

	public boolean isVerbPatch() {
		return HTTPVerbDefaults.PATCH == verb;
	}

	public boolean isVerbPost() {
		return HTTPVerbDefaults.POST == verb;
	}
	
	public boolean isVerbPut() {
		return HTTPVerbDefaults.PUT == verb;
	}
	
	public boolean isVerbTrace() {
		return HTTPVerbDefaults.TRACE == verb;
	}


	
}
