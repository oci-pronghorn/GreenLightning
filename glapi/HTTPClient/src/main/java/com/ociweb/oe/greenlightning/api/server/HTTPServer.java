package com.ociweb.oe.greenlightning.api.server;


import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.HTTPServerConfig;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPServer implements GreenApp
{
	//private byte[] cookieHeader = HTTPHeaderDefaults.COOKIE.rootBytes();
	
	//private int emptyResponseRouteId;
	private int smallResponseRouteId;
	//private int largeResponseRouteId;
	//private int splitResponseRouteId;
	private int shutdownRouteId;
	private int port;	
	private AppendableProxy console;
	private final String host;
	private long keyFieldId;
	
	public HTTPServer(String host, Appendable console, int port) {
		this.host = host;
		this.port = port;
		this.console = Appendables.proxy(console);
	}
	
	public HTTPServer(Appendable console) {
		this.host = null;
		this.console = Appendables.proxy(console);
	}
	
    @Override
    public void declareConfiguration(GreenFramework c) {
        
    	
    	HTTPServerConfig conf = c.useHTTP1xServer(port)
    			.setHost(host)
    			
    			.useInsecureServer()
    			
    			.setConcurrentChannelsPerDecryptUnit(4)
    			.setConcurrentChannelsPerEncryptUnit(4);
    			
    			
		
		//emptyResponseRouteId = c.registerRoute("/testpageA?arg=#{myarg}", cookieHeader);
		smallResponseRouteId = c.defineRoute().path("/testpageB").routeId();
		//largeResponseRouteId = c.registerRoute("/testpageC", cookieHeader);
		//splitResponseRouteId = c.registerRoute("/testpageD");
		
		//only do in test mode... 
		//in production it is a bad idea to let clients turn off server.
		shutdownRouteId = c.defineRoute().path("/shutdown?key=${key}").routeId();
				
		keyFieldId = c.lookupFieldByName(shutdownRouteId, "key");
		
    }


    @Override
    public void declareBehavior(GreenRuntime runtime) {
    	
        //runtime.addRestListener(new RestBehaviorEmptyResponse(runtime, "myarg", console))
        //         .includeRoutes(emptyResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorSmallResponse(runtime, console))
        		.includeRoutes(smallResponseRouteId);
        
       // runtime.addRestListener(new RestBehaviorLargeResponse(runtime, console))
        //		 .includeRoutes(largeResponseRouteId);
        
       // runtime.addRestListener(new RestBehaviorHandoff(runtime, "responder"))
       // 		 .includeRoutes(splitResponseRouteId);
        
        //runtime.addPubSubListener(new RestBehaviorHandoffResponder(runtime, console))
		//         .addSubscription("responder");
        


        
        //splitResponseRouteId
        
        runtime.addRestListener(new ShutdownRestListener(runtime, keyFieldId))
                  .includeRoutes(shutdownRouteId);
        
        //NOTE .includeAllRoutes() can be used to write a behavior taking all routes

    }
   
}
