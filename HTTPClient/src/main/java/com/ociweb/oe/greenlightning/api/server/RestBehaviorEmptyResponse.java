package com.ociweb.oe.greenlightning.api.server;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class RestBehaviorEmptyResponse implements RestListener {

	private final int cookieHeader = HTTPHeaderDefaults.COOKIE.ordinal();
	private final byte[] fieldName;
	private final GreenCommandChannel cmd;
	private final AppendableProxy console;
	
	public RestBehaviorEmptyResponse(GreenRuntime runtime, String myArgName, AppendableProxy console) {
		this.fieldName = myArgName.getBytes();		
		this.cmd = runtime.newCommandChannel(NET_RESPONDER);
		this.console = console;
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
	    int argInt = request.getInt(fieldName);
	    Appendables.appendValue(console, "Arg Int: ", argInt, "\n");
	    		
		request.openHeaderData(cookieHeader, (id,reader)-> {
			
			console.append("COOKIE: ");
			reader.readUTF(console).append('\n');
					
		});
		
		if (request.isVerbPost()) {
			request.openPayloadData((reader)->{
				
				console.append("POST: ");
				reader.readUTFOfLength(reader.available(), console);
				console.append('\n');
									
			});
		}
		
		//no body just a 200 ok response.
		return cmd.publishHTTPResponse(request, 200);

	}

}
