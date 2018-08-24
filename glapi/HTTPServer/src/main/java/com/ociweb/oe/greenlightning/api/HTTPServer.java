package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.HTTPServerConfig;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class HTTPServer implements GreenApp
{
		
	private AppendableProxy console;
	private final String host;
	private final int port;
	private final int telemetryPort;
	private final boolean isTLS;
	
	public HTTPServer(String host, int port, Appendable console, int telemetryPort, boolean isTLS) {
		this.host = host;
		this.console = Appendables.proxy(console);
		this.port = port;
		this.telemetryPort = telemetryPort;
		this.isTLS = isTLS;
	}

	public HTTPServer(int port, Appendable console, int telemetryPort, boolean isTLS) {
		this.host = null;
		this.console = Appendables.proxy(console);
		this.port = port;
		this.telemetryPort = telemetryPort;
		this.isTLS = isTLS;
	}
	
    @Override
    public void declareConfiguration(Builder c) {
        
		HTTPServerConfig server = c.useHTTP1xServer(port)
		 .setHost(host)
		 .setConcurrentChannelsPerDecryptUnit(12)
		 .setConcurrentChannelsPerEncryptUnit(12)
		 .setMaxResponseSize(1<<18);
		
		if (!isTLS) {
			server.useInsecureServer();
		}
		
		if (telemetryPort>0) {
			c.enableTelemetry(telemetryPort);
		}
				
		c.defineRoute(HTTPHeaderDefaults.COOKIE)
                .path("/testpageA?arg=#{myarg}")
                .path("/testpagesA?arg=#{myarg}&name=${name}")
                .path("/testpageA?f=g")
                .refineInteger("myarg", Field.MYARG, 111, (v) -> v>0)
                .associatedObject("name", Field.PERSON_NAME)
                .routeId(Struct.EMPTY_EXAMPLE);
                
                
       	c.defineRoute()
    		    .parseJSON()
      		    	.stringField( "person.name", Field.PERSON_NAME)
    		    	.integerField("person.age",  Field.PERSON_AGE)
    		    .path("/testJSON")
    			.routeId(Struct.JSON_EXAMPLE);       
                
				                 
        c.defineRoute().path("/resources/${path}").routeId(Struct.RESOURCES_EXAMPLE);
        c.defineRoute().path("/files/${path}").routeId(Struct.FILES_EXAMPLE);
        c.defineRoute().path("/testpageB").routeId(Struct.SMALL_EXAMPLE);
		c.defineRoute(HTTPHeaderDefaults.COOKIE).path("/testpageC").routeId(Struct.LARGE_EXAMPLE);				                 
		c.defineRoute().path("/testpageD").routeId(Struct.SPLIT_EXAMPLE);
    }
    
    @Override
    public void declareBehavior(GreenRuntime runtime) {

        runtime.registerListener(new RestBehaviorEmptyResponse(runtime, console))
                 .includeRoutesByAssoc(Struct.EMPTY_EXAMPLE);
        
        runtime.registerListener(new RestBehaviorSmallResponse(runtime, console))
        		.includeRoutesByAssoc(Struct.SMALL_EXAMPLE);
        
        runtime.registerListener(new RestBehaviorLargeResponse(runtime, console))
        		 .includeRoutesByAssoc(Struct.LARGE_EXAMPLE);
                
        
		//TODO: add blocking exmple here
		//runtime.registerBlockingListener(()->new BlockingExample(), Field.CONNECTION_ID, threadsCount, timeoutNS);
        
        
        String topic = "httpData";

        runtime.registerListener(new RestBehaviorHandoff(runtime, topic))
        		 .includeRoutesByAssoc(Struct.SPLIT_EXAMPLE);
		
        runtime.registerListener(new RestBehaviorHandoffResponder(runtime, console))
                 .addSubscription(topic);
  
        runtime.registerListener(new RestBehaviorJsonResponce(runtime, console))
        		  .includeRoutesByAssoc(Struct.JSON_EXAMPLE);
        
    	runtime.addResourceServer("exampleSite")
		         .includeRoutesByAssoc(Struct.RESOURCES_EXAMPLE);

    	runtime.addFileServer("./src/main/resources/exampleSite") 
				 .includeRoutesByAssoc(Struct.FILES_EXAMPLE);
				        
        
        //NOTE .includeAllRoutes() can be used to write a behavior taking all routes

    }
   
}
