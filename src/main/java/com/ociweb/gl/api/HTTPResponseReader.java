package com.ociweb.gl.api;

import com.ociweb.gl.impl.HTTPPayloadReader;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.TrieParser;

public class HTTPResponseReader extends HTTPPayloadReader<NetResponseSchema> {

	private short status;
	private HTTPContentType httpContentType;
	private int flags;
	private long connectionId;
	
	public HTTPResponseReader(Pipe<NetResponseSchema> pipe) {
		super(pipe);
	}

	public void setParseDetails(IntHashTable table, TrieParser headerTrieParser) {
		this.paraIndexCount = 0; //count of fields before headers which are before the payload
		this.headerHash = table;
		this.headerTrieParser = headerTrieParser;
	}

	public void setStatusCode(short statusId) { //TODO: hide these so maker does not see them.
		this.status = statusId;
	}
	
	/**
    * statusCode Status code of the response. -1 indicates
    *                   the network connection was lost.
    */                   
	public short statusCode() {
		return this.status;
	}

	public void setContentType(HTTPContentType httpContentType) {
		this.httpContentType = httpContentType;
	}
	
	public HTTPContentType contentType() {
		return this.httpContentType;
	}

	public void setFlags(int flags) {
		this.flags = flags;
	}
	
	public final boolean isBeginningOfResponse() {
		return 0 != (this.flags&HTTPFieldReader.BEGINNING_OF_RESPONSE);
	}
	
	public final boolean isEndOfResponse() {
		return 0 != (this.flags&HTTPFieldReader.END_OF_RESPONSE);
	}
	
	public final boolean isConnectionClosed() {
		return 0 != (this.flags&HTTPFieldReader.CLOSE_CONNECTION);
	}

	public void setConnectionId(long ccId1) {
		connectionId = ccId1;
	}
	
	public long connectionId() {
		return connectionId;
	}
	
	
}