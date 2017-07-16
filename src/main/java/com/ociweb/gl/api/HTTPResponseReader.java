package com.ociweb.gl.api;

import com.ociweb.gl.impl.HTTPPayloadReader;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.util.TrieParser;

public class HTTPResponseReader extends HTTPPayloadReader<NetResponseSchema> {

	
	public HTTPResponseReader(Pipe<NetResponseSchema> pipe) {
		super(pipe);
	}

	public void setParseDetails(IntHashTable table, TrieParser headerTrieParser) {
		this.paraIndexCount = 0; //count of fields before headers which are before the payload
		this.headerHash = table;
		this.headerTrieParser = headerTrieParser;
	}
}