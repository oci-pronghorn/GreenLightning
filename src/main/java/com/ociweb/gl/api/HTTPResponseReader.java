package com.ociweb.gl.api;

import com.ociweb.gl.impl.HTTPPayloadReader;
import com.ociweb.gl.impl.stage.HeaderTypeCapture;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.TrieParser;

public class HTTPResponseReader extends HTTPPayloadReader<NetResponseSchema> {

	private short status;
	private HTTPContentType httpContentType;
	private int flags;
	private long connectionId;
	private HeaderTypeCapture htc;
	 
    	
	public HTTPResponseReader(Pipe<NetResponseSchema> pipe) {
		super(pipe);
	}

	public void setParseDetails(IntHashTable table, 
			                    TrieParser headerTrieParser,
			                    HTTPSpecification<?,?,?,?> httpSpec) {
		//there is 1 field which holds the index to where the payload begins, eg after the headers.
		this.paraIndexCount = 1; //count of fields before headers which are before the payload
		this.headerHash = table;
		this.headerTrieParser = headerTrieParser;
		this.httpSpec = httpSpec;
		this.payloadIndexOffset = 1;
		if (null==htc) {
			this.htc  = new HeaderTypeCapture(httpSpec);
		}
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
	
	public HTTPContentType contentType() {
		
	   	 if (openHeaderData(HTTPHeaderDefaults.CONTENT_TYPE.ordinal(), htc)) {
			 return htc.type();
		 } else {
			 return HTTPContentTypeDefaults.UNKNOWN;
		 }
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