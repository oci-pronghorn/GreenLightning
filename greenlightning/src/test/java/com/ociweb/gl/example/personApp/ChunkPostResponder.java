package com.ociweb.gl.example.personApp;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.pronghorn.network.config.HTTPContentType;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class ChunkPostResponder implements PubSubMethodListener {

	private final HTTPResponseService resp;

	public ChunkPostResponder(GreenRuntime runtime) {
		resp = runtime.newCommandChannel().newHTTPResponseService();
	}

	public boolean beginChunks(CharSequence topic, ChannelReader payload) {
						
		return resp.publishHTTPResponse(
				                 payload.structured().readLong(GreenField.connectionId),
								 payload.structured().readLong(GreenField.sequenceId), 
								 payload.structured().readInt(GreenField.status),
								 payload.structured().readBoolean(GreenField.hasContinuation),
								 HTTPContentTypeDefaults.JSON,
								 w-> {
									 
									ChannelReader reader = payload.structured().read(GreenField.payload);
									reader.readInto(w, reader.available());
																		 
								 });
	}

	public boolean continueChunks(CharSequence topic, ChannelReader payload) {
		
		return resp.publishHTTPResponseContinuation(
				 payload.structured().readLong(GreenField.connectionId),
				 payload.structured().readLong(GreenField.sequenceId),
				 payload.structured().readBoolean(GreenField.hasContinuation),
				 w-> {
					 
					ChannelReader reader = payload.structured().read(GreenField.payload);
					reader.readInto(w, reader.available());
														 
				 });
	}
	
}
