package com.ociweb.gl.api;

import com.ociweb.gl.impl.http.server.HTTPPayloadReader;
import com.ociweb.gl.impl.stage.HeaderTypeCapture;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;

public class HTTPResponseReader extends HTTPPayloadReader<NetResponseSchema> {

	private short status;
	private int flags;
	private long connectionId;
	private HeaderTypeCapture htc;
	 
    	
	public HTTPResponseReader(Pipe<NetResponseSchema> pipe, HTTPSpecification<?,?,?,?> httpSpec) {
		super(pipe);
		this.httpSpec = httpSpec;
		
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
					
	   	 if (structured().identityVisit(HTTPHeaderDefaults.CONTENT_TYPE, htc)) {
			 return htc.type();
		 } else {
			 return HTTPContentTypeDefaults.UNKNOWN;
		 }
	}

	public void setFlags(int flags) {
		this.flags = flags;
	}
	
	public final boolean isBeginningOfResponse() {
		return 0 != (this.flags&ServerCoordinator.BEGIN_RESPONSE_MASK);
	}
	
	public final boolean isEndOfResponse() {
		return 0 != (this.flags&ServerCoordinator.END_RESPONSE_MASK);
	}
	
	public final boolean isConnectionClosed() {
		return 0 != (this.flags&ServerCoordinator.CLOSE_CONNECTION_MASK);
	}

	public void setConnectionId(long ccId1) {
		connectionId = ccId1;
	}
	
	public long connectionId() {
		return connectionId;
	}

	
	
}