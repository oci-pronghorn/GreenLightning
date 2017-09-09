package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.Writable;
import com.ociweb.gl.api.Payloadable;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.pipe.BlobWriter;

public class RestBehaviorLargeResponse implements RestListener {

	private final GreenCommandChannel cmd;
	private int partNeeded = 0;
	
	public RestBehaviorLargeResponse(GreenRuntime runtime) {	
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
	}
	
	Payloadable reader = new Payloadable() {
		
		@Override
		public void read(BlobReader reader) {
			
			System.out.println("POST: "+reader.readUTFOfLength(reader.available()));
			
		}			
	};


	Writable writableA = new Writable() {
		
		@Override
		public void write(BlobWriter writer) {
			writer.writeUTF8Text("beginning of text file\n");//23 in length
		}
		
	};
	
	Writable writableB = new Writable() {
		
		@Override
		public void write(BlobWriter writer) {
			writer.writeUTF8Text("ending of text file\n");//20 in length
		}
		
	};
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isVerbPost()) {
			request.openPayloadData(reader);
		}
		
		if (0 == partNeeded) {
			boolean okA = cmd.publishHTTPResponse(request, 200, 
									request.getRequestContext(),
					                HTTPContentTypeDefaults.TXT,
					                writableA);
			if (!okA) {
				return false;
			} 
		}
				
		//////
		//NB: this block is here for demo reasons however one could
		//    publish a topic back to this behavior to complete the
		//    continuaton at a future time
		//////
	
		boolean okB = cmd.publishHTTPResponseContinuation(request,
						 		request.getRequestContext() | HTTPFieldReader.END_OF_RESPONSE,
						 		writableB);
		if (okB) {
			partNeeded = 0;
			return true;
		} else {
			partNeeded = 1;
			return false;
		}
	}

}
