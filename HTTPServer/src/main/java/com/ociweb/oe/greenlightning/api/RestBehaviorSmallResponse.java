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

public class RestBehaviorSmallResponse implements RestListener {

	private final GreenCommandChannel cmd;
	
	public RestBehaviorSmallResponse(GreenRuntime runtime) {	
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
			writer.writeUTF8Text("beginning of text file\n");
		}
		
	};
	
	Writable writableB = new Writable() {
		
		@Override
		public void write(BlobWriter writer) {
			writer.writeUTF8Text("this is some text\n");
		}
		
	};
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isVerbPost()) {
			request.openPayloadData(reader );
		}

		//if this can not be published then we will get the request again later to be reattempted.
		return cmd.publishHTTPResponse(request, 200, 
								request.getRequestContext() | HTTPFieldReader.END_OF_RESPONSE,
				                HTTPContentTypeDefaults.TXT,
				                writableA);

	}

}
