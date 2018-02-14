package com.ociweb.gl.example;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;

public class AllRoutesExample2 implements GreenApp {

	public static void main(String[] args) {
		GreenRuntime.run(new AllRoutesExample2(),args);
	}

	@Override
	public void declareConfiguration(Builder builder) {
		builder.useHTTP1xServer(8082);
		int a = builder.defineRoute("/routeOne");
		int b = builder.defineRoute("/second?a=#{value}");
		int c = builder.defineRoute("/woot");
		
		System.err.println(a+"  "+b+"  "+c);

	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
		
		final GreenCommandChannel cmd = runtime.newCommandChannel(NET_RESPONDER);
		
		RestListener listener = new RestListener() {
			@Override
			public boolean restRequest(HTTPRequestReader request) {	
				
				//TODO: this route is the wrong value
				int id = request.getRouteId();
				System.out.println(id);
			//	request.getText("value".getBytes(), System.out);
				
				return cmd.publishHTTPResponse(request, 200);				
			}			
		};
		runtime.addRestListener(listener).includeAllRoutes();
	}
}
