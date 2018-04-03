package com.ociweb.oe.greenlightning.api.server;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;

public class RestBehaviorLargeResponse implements RestListener {

	private final int cookieHeader = HTTPHeaderDefaults.COOKIE.ordinal();
	private final GreenCommandChannel cmd;
	private int partNeeded = 0;
	private final AppendableProxy console;
	
	public RestBehaviorLargeResponse(GreenRuntime runtime, AppendableProxy console) {	
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
		this.console = console;
	}
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isVerbPost()) {
			request.openPayloadData((reader)->{
				
				console.append("POST: ");
				//TODO: why is this payload pointing to the cookie??
				//reader.readUTF(console);
				reader.readUTFOfLength(reader.available(),console);
				console.append('\n');
				
			});
		}
		
		request.structured().identityVisit(HTTPHeaderDefaults.COOKIE, (id,reader)-> {
			
			console.append("COOKIE: ");
			reader.readUTF(console).append('\n');
					
		});
		
		if (0 == partNeeded) {
			boolean okA = cmd.publishHTTPResponse(request, 200, 
									true,
					                HTTPContentTypeDefaults.TXT,
					                (writer)->{
					                	writer.writeUTF8Text("beginning of text file\n");
					                });
			if (!okA) {
				return false;
			} 
		}
				
		//////
		//NB: this block is here for demo reasons however one could
		//    publish a topic back to this behavior to complete the
		//    continuation at a future time
		//////
	
		boolean okB = cmd.publishHTTPResponseContinuation(request,
						 		false,
						 		(writer)-> {
						 			writer.writeUTF8Text("ending of text file\n");
						 		});
		if (okB) {
			partNeeded = 0;
			return true;
		} else {
			partNeeded = 1;
			return false;
		}
	}

}
