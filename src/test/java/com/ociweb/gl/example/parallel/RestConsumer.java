package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;

public class RestConsumer implements RestListener {

	private GreenCommandChannel cmd1;
	private GreenCommandChannel cmd2;
	
	public RestConsumer(GreenRuntime runtime) {
		
		cmd1 = runtime.newCommandChannel();
		cmd2 = runtime.newCommandChannel();
		
		cmd1.ensureHTTPServerResponse();
		cmd2.ensureDynamicMessaging();
		
	}


	@Override
	public boolean restRequest(HTTPRequestReader request) {
		
		cmd1.publishHTTPResponse(request, 200);
		cmd2.publishTopic("/sent/200"); //what should this do?
		
		return true;
	}

}
