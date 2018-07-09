package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPServer implements GreenApp
{
	private int emptyResponseRouteId;
	private int smallResponseRouteId;
	private int largeResponseRouteId;
	private int splitResponseRouteId;
	private int shutdownRouteId;
		
	private AppendableProxy console;
	private final String host;
	private final int port;
	
	private long keyFieldId;
	private long nameFieldId;
	
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
        
		c.useHTTP1xServer(port).setHost(host).setMaxResponseSize(1<<18);
		
		emptyResponseRouteId = c.defineRoute(HTTPHeaderDefaults.COOKIE)
				                 .path("/testpageA?arg=#{myarg}").routeId();
		smallResponseRouteId = c.defineRoute().path("/testpageB").routeId();
		largeResponseRouteId = c.defineRoute(HTTPHeaderDefaults.COOKIE)
				                  .path("/testpageC").routeId();
		splitResponseRouteId = c.defineRoute().path("/testpageD").routeId();
		
		//only do in test mode... 
		//in production it is a bad idea to let clients turn off server.
		shutdownRouteId = c.defineRoute().path("/shutdown?key=${key}").routeId();
				
		c.enableTelemetry();
		
		keyFieldId = c.lookupFieldByName(shutdownRouteId, "key");
		nameFieldId = c.lookupFieldByName(emptyResponseRouteId, "myarg");
    }


    @Override
    public void declareBehavior(GreenRuntime runtime) {
    	
        runtime.addRestListener(new RestBehaviorEmptyResponse(runtime, nameFieldId, console))
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
        
        runtime.addRestListener(new ShutdownRestListener(runtime, keyFieldId))
                  .includeRoutes(shutdownRouteId);
        
        //NOTE .includeAllRoutes() can be used to write a behavior taking all routes

    }
   
}
