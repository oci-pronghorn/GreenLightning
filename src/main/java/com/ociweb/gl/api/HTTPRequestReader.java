package com.ociweb.gl.api;

import com.ociweb.gl.impl.http.server.HTTPPayloadReader;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.http.FieldExtractionDefinitions;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStageConfig;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.math.Decimal;

public class HTTPRequestReader extends HTTPPayloadReader<HTTPRequestSchema> implements HTTPFieldReader<HTTPRequestSchema> {

	private long connectionId;
	private long sequenceCode;
	private int revisionId;
	private int routeId;
	private int requestContext;
	private HTTPVerbDefaults verb;
	private final boolean hasNoRoutes;

	//TODO: rename this, it should not be HTTP1.x specific but more general...
	private HTTP1xRouterStageConfig<?, ?, ?, ?> http1xRouterStageConfig;
	
	
	public HTTPRequestReader(Pipe<HTTPRequestSchema> pipe, boolean hasNoRoutes,
			                 HTTPSpecification httpSpec,
			                 HTTP1xRouterStageConfig<?, ?, ?, ?> http1xRouterStageConfig) {
		super(pipe);
		this.hasNoRoutes = hasNoRoutes;
		this.httpSpec = httpSpec;
		this.http1xRouterStageConfig = http1xRouterStageConfig;
	}
	
	public void setVerb(HTTPVerbDefaults verb) {
		this.verb = verb;
	}

	public HTTPVerbDefaults getVerb() {
		return this.verb;
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


	public void setRequestContext(int value) {
		requestContext = value;
	}
	
	public int getRequestContext() {
		return requestContext;
	}

	public void setRouteId(int pathId) {
		this.http1xRouterStageConfig.getRouteIdForPathId(pathId);		
	}

	public int getRouteId() {
		return routeId;
	}
	
	public void setConnectionId(long connectionId, long sequenceCode) {
		this.connectionId = connectionId;
		this.sequenceCode = sequenceCode;
	}
	
	public void handoff(ChannelWriter writer) {
		writer.writePackedLong(connectionId);
		writer.writePackedLong(sequenceCode);
	}
	
	public long getConnectionId() {
		return connectionId;
	}
	
	public long getSequenceCode() {
		return sequenceCode;
	}


	public void setRevisionId(int value) {
		revisionId = value;
	}
	
	public int getRevisionId() {
		return revisionId;
	}
	
	private FieldExtractionDefinitions fieldDefs;
	

	
	/**
	 * Only call this method when NO routes have been defined.
	 * @param appendable
	 * 
	 */
	public <A extends Appendable> A getRoutePath(A appendable) {
    	assert(isStructured());
    	assert(getStructType(this) == http1xRouterStageConfig.UNMAPPED_STRUCT);
    	
		if (hasNoRoutes) {		
			return structured().readText(http1xRouterStageConfig.unmappedPathField,appendable);		
		} else {
			throw new UnsupportedOperationException("this method can only be used when no routes have been defined.");
		}
	}
	




}
