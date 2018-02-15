package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPServer implements GreenApp
{
	private byte[] cookieHeader = HTTPHeaderDefaults.COOKIE.rootBytes();
	
	private int emptyResponseRouteId;
	private int smallResponseRouteId;
	private int largeResponseRouteId;
	private int splitResponseRouteId;
	private int shutdownRouteId;
		
	private AppendableProxy console;
	private final String host;
	private final int port;
	
	public HTTPServer(String host, int port, Appendable console) {
		this.host = host;
		this.console = Appendables.proxy(console);
		this.port = port;
	}
	
	public HTTPServer(int port, Appendable console) {
		this.host = null;
		this.console = Appendables.proxy(console);
		this.port = port;
	}
	
    @Override
    public void declareConfiguration(Builder c) {
        
		c.useHTTP1xServer(port).setHost(host);
		
		emptyResponseRouteId = c.defineRoute("/testpageA?arg=#{myarg}", cookieHeader);
		smallResponseRouteId = c.defineRoute("/testpageB");
		largeResponseRouteId = c.defineRoute("/testpageC", cookieHeader);
		splitResponseRouteId = c.defineRoute("/testpageD");
		
		//only do in test mode... 
		//in production it is a bad idea to let clients turn off server.
		shutdownRouteId = c.defineRoute("/shutdown?key=${key}");
				
		c.enableTelemetry();
		
    }


    @Override
    public void declareBehavior(GreenRuntime runtime) {
    	
        runtime.addRestListener(new RestBehaviorEmptyResponse(runtime, "myarg", console))
                 .includeRoutes(emptyResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorSmallResponse(runtime, console))
        		.includeRoutes(smallResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorLargeResponse(runtime, console))
        		 .includeRoutes(largeResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorHandoff(runtime, "responder"))
        		 .includeRoutes(splitResponseRouteId);
        
        runtime.addPubSubListener(new RestBehaviorHandoffResponder(runtime, console))
		         .addSubscription("responder");
        


        
        //splitResponseRouteId
        
        runtime.addRestListener(new ShutdownRestListener(runtime))
                  .includeRoutes(shutdownRouteId);
        
        //NOTE .includeAllRoutes() can be used to write a behavior taking all routes

    }
   
}
