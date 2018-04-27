package com.ociweb.gl.impl.http.server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.FailablePayloadReading;
import com.ociweb.gl.api.HeaderReader;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.config.HTTPRevision;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerb;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.StructuredReader;
import com.ociweb.pronghorn.util.TrieParser;

public class HTTPPayloadReader<S extends MessageSchema<S>> extends PayloadReader<S> implements HeaderReader {


	protected HTTPSpecification<
			? extends Enum<? extends HTTPContentType>,
			? extends Enum<? extends HTTPRevision>,
			? extends Enum<? extends HTTPVerb>,
			? extends Enum<? extends HTTPHeader>> httpSpec;

	
	private static final Logger logger = LoggerFactory.getLogger(HTTPPayloadReader.class);
	

	public HTTPPayloadReader(Pipe<S> pipe) {
		super(pipe);
	}

	public HTTPSpecification<
			? extends Enum<? extends HTTPContentType>,
			? extends Enum<? extends HTTPRevision>,
			? extends Enum<? extends HTTPVerb>,
			? extends Enum<? extends HTTPHeader>> getSpec() {
		return this.httpSpec;
	}

	
	public boolean openPayloadData(Payloadable reader) {
		if (hasRemainingBytes()) {
			position(this, readFromEndLastInt(StructuredReader.PAYLOAD_INDEX_LOCATION));
			reader.read(this);//even when we have zero length...
			return true;
		} else {
			return false;
		}
	}


	public boolean openPayloadDataFailable(FailablePayloadReading reader) {
		if (hasRemainingBytes()) {
			position(this, readFromEndLastInt(StructuredReader.PAYLOAD_INDEX_LOCATION));
			return reader.read(this);//even when we have zero length...
		} else {
			return false;
		}
	}
}
