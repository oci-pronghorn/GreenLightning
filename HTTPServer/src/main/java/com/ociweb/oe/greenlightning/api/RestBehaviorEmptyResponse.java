package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.gl.api.RestListener;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class RestBehaviorEmptyResponse implements RestListener {

	private final int cookieHeader = HTTPHeaderDefaults.COOKIE.ordinal();

	private final HTTPResponseService cmd;
	private final AppendableProxy console;
	
	public RestBehaviorEmptyResponse(GreenRuntime runtime,AppendableProxy console) {

		this.cmd = runtime.newCommandChannel().newHTTPResponseService();
		this.console = console;
	}

	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
	    int argInt = request.structured().readInt(Field.MYARG);
	    
	    if (-1 == argInt) {
		    if (request.structured().isNull(Field.MYARG)) {
		    	console.append("Arg is NULL\n ");
		    } else {
		    	Appendables.appendValue(console, "Arg Int: ", argInt, "\n");
		    }
	    } else {	    
	    	Appendables.appendValue(console, "Arg Int: ", argInt, "\n");
	    }
	    
	    request.structured().identityVisit(HTTPHeaderDefaults.COOKIE, (id,reader,field)-> {
			
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
