package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;

public class AllRoutesExample implements GreenApp {

	public static void main(String[] args) {
		GreenRuntime.run(new AllRoutesExample(),args);
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		builder.enableServer(8082);
//		builder.registerRoute("/xx${path}");
//		builder.registerRoute("/tt${path}");
		builder.enableTelemetry();
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
		
		final GreenCommandChannel cmd = runtime.newCommandChannel(NET_RESPONDER);
		
		RestListener listener = new RestListener() {
			@Override
			public boolean restRequest(HTTPRequestReader request) {				
				request.getText("path".getBytes(), System.out);
				
				//TODO: if we consume but do not return ack this should be an error
				return cmd.publishHTTPResponse(request, 200);
				
			}			
		};
		runtime.addRestListener(listener).includeAllRoutes();
	}

}
