package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;

public class HTTPServer implements GreenApp
{
	byte[] cookieHeader = HTTPHeaderDefaults.COOKIE.rootBytes();
	
	int emptyResponseRouteId;
	int smallResponseRouteId;
	int largeResponseRouteId;
	int fileServerId;
	
	
	byte[] myArgName = "myarg".getBytes();
	
    @Override
    public void declareConfiguration(Builder c) {
        
		c.enableServer(false, 8088);    	
		emptyResponseRouteId = c.registerRoute("/testpageA?arg=#{myarg}", cookieHeader);
		smallResponseRouteId = c.registerRoute("/testpageB");
		largeResponseRouteId = c.registerRoute("/testpageC", cookieHeader);
		fileServerId         = c.registerRoute("/file${path}");
		c.enableTelemetry();
		
    }


    @Override
    public void declareBehavior(GreenRuntime runtime) {
        runtime.addRestListener(new RestBehaviorEmptyResponse(runtime, myArgName))
                 .includeRoutes(emptyResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorSmallResponse(runtime))
        		.includeRoutes(smallResponseRouteId);
        
        runtime.addRestListener(new RestBehaviorLargeResponse(runtime))
        		 .includeRoutes(largeResponseRouteId);
        
        //NOTE .includeAllRoutes() can be used to write a behavior taking all routes
        
        //NOTE when using the above no routes need to be registered and if they are
        //     all other routes will return a 404

    }
   
}
