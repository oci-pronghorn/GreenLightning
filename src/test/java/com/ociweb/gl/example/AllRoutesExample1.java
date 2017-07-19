package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;

public class AllRoutesExample1 implements GreenAppParallel {

	public static void main(String[] args) {
		GreenRuntime.run(new AllRoutesExample1(),args);
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		builder.enableServer(8082);
		builder.parallelism(4);
		builder.enableTelemetry();
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {		
		final GreenCommandChannel cmd = runtime.newCommandChannel(NET_RESPONDER);		
		RestListener listener = new RestListener() {
			@Override
			public boolean restRequest(HTTPRequestReader request) {				
				request.getRoutePath(System.out);
				
				return cmd.publishHTTPResponse(request, 200);				
			}			
		};		
		runtime.addRestListener(listener).includeAllRoutes();
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
	}
}
