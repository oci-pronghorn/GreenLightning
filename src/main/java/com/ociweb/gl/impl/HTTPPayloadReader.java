package com.ociweb.gl.impl;

import java.util.Optional;

import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HeaderReader;
import com.ociweb.pronghorn.network.config.HTTPHeaderKey;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class HTTPPayloadReader<S extends MessageSchema<S>> extends PayloadReader<S> implements HTTPFieldReader, HeaderReader {

	private IntHashTable headerHash;
	private int paraIndexCount;
	private int revisionId;
	private int requestContext;
	private HTTPVerbDefaults verb;
	private int routeId;
	private long connectionId;
	private long sequenceCode;
	private final Optional<HeaderReader> optionalHeaderReader;
	private final Optional<DataInputBlobReader<S>> optionalPostReader;
	private TrieParser headerTrieParser;
	private TrieParserReader reader = new TrieParserReader(0, true);
	
	
	
	
	public int headerId(byte[] header) {		
		return (int)TrieParserReader.query(reader, headerTrieParser, header, 0, header.length, Integer.MAX_VALUE);
	}

	public HTTPPayloadReader(Pipe<S> pipe) {
		super(pipe);
		optionalHeaderReader = Optional.of(this);
		optionalPostReader = Optional.of(this);
	}
	
	public void setHeaderTable(IntHashTable table, int paraIndexCount, TrieParser headerTrieParser) {
		this.headerHash = table;
		this.paraIndexCount = paraIndexCount;
		this.headerTrieParser = headerTrieParser;
	}

	public Optional<HeaderReader> openHeaderData(byte[] header) {
		return openHeaderData(headerId(header));
	}

	public Optional<HeaderReader> openHeaderData(int headerId) {
	
		if (headerId>=0) {
			int item = IntHashTable.getItem(headerHash, HTTPHeaderKey.HEADER_BIT | headerId);
			if (item!=0) {				
				position(readFromEndLastInt(paraIndexCount + (0xFFFF & item)));	//TODO: base must be just count!!
				return optionalHeaderReader;
			}
		}
		return Optional.empty();
		
	}
	
	public Optional<DataInputBlobReader<S>> openPayloadData() {
		position(readFromEndLastInt(paraIndexCount + IntHashTable.count(headerHash)));
		return optionalPostReader;
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
