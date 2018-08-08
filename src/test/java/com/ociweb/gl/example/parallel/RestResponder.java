package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class RestResponder implements PubSubListener{

	private final HTTPResponseService cmd;
	private final GreenCommandChannel newCommandChannel;
	private final boolean useChunked;
	
	
    private static final JSONRenderer<ChannelReader> jsonRenderer = new JSONRenderer<ChannelReader>()
            .beginObject()
            .integer("value", o->o.readPackedInt())
            .string("other", (o,t)->t.append("text"))
            .endObject();
	
	private ChannelReader payloadW;	
	private final Writable w = new Writable() {
		@Override
		public void write(ChannelWriter writer) {
			jsonRenderer.render(writer, payloadW);
		}
	};

	public RestResponder(GreenRuntime runtime, boolean chunked) {
		newCommandChannel = runtime.newCommandChannel();
		cmd = newCommandChannel.newHTTPResponseService(1<<14,250);
		useChunked = chunked;
	}
	
	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		payloadW = payload;
		
		if (!useChunked) {
			return cmd.publishHTTPResponse(
					payload.readPackedLong(), 
					payload.readPackedLong(), 
					200, false, HTTPContentTypeDefaults.JSON, w);
		
		} else {
			if (cmd.hasRoomFor(2)) {
				
				long connectionId = payload.readPackedLong();
				long sequenceCode = payload.readPackedLong();
				
				cmd.publishHTTPResponse(
						connectionId, 
						sequenceCode, 
						200, true, HTTPContentTypeDefaults.JSON, w);
				
				//TODO: another issue, The end of the continuation MUST be non zero length!!.
				cmd.publishHTTPResponseContinuation(
						connectionId, 
						sequenceCode, 
						false, (w)->{w.append("hello");});
				
				
				return true;
			} else {
				return false;
			}
		}
	}

}
