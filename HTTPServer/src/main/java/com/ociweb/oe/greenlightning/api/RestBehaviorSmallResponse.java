package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPFieldReader;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;

public class RestBehaviorSmallResponse implements RestListener {

	private final GreenCommandChannel cmd;
	private final AppendableProxy console;
	
	public RestBehaviorSmallResponse(GreenRuntime runtime, AppendableProxy console) {	
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
		this.console = console;
	}
	
	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		if (request.isVerbPost()) {
			request.openPayloadData((reader)->{
				
				console.append("POST: ");
				reader.readUTFOfLength(reader.available(),console);
								
			});
		}

		//if this can not be published then we will get the request again later to be reattempted.
		return cmd.publishHTTPResponse(request, 200, 
								false,
				                HTTPContentTypeDefaults.TXT,
				                (writer)-> {
				                	writer.writeUTF8Text("beginning of text file\n");
				                });

	}

}
