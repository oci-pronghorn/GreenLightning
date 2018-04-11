package com.ociweb.gl.example;

import com.ociweb.gl.api.*;

public class AllRoutesExample1 implements GreenAppParallel {

	public static void main(String[] args) {
		GreenRuntime.run(new AllRoutesExample1(),args);
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		builder.useHTTP1xServer(8082).setHost("localhost").useInsecureServer(); //127.0.0.1
		builder.parallelTracks(2);
		builder.enableTelemetry();
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {		
		final GreenCommandChannel cmd = runtime.newCommandChannel();	
		final HTTPResponseService responseService = cmd.newHTTPResponseService();
		
		RestListener listener = new RestListener() {
			@Override
			public boolean restRequest(HTTPRequestReader request) {				
				request.getRoutePath(System.out);
				
				return responseService.publishHTTPResponse(request, 200);				
			}			
		};		
		runtime.addRestListener(listener)
		       .includeAllRoutes();
		       
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
	}
}
