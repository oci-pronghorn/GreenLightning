package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.json.JSONType;
import com.ociweb.json.decode.JSONExtractor;
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
		 .setTracks(2)
		 .setMaxResponseSize(1<<18);
		
		if (!isTLS) {
			server.useInsecureServer();
		}
		
		if (telemetryPort>0) {
			c.enableTelemetry(telemetryPort);
		}
		
		c.defineRoute(HTTPHeaderDefaults.COOKIE)
				                 .path("/testpageA?arg=#{myarg}")
				    //             .path("/testpageA?A=#{myarg}")////TODO: this should be possible  but not working why?
				                 .defaultInteger("myarg", 111)
				                 .associatedObject("myarg", Params.MYARG)
				                 .routeId(Routes.EMPTY_EXAMPLE);
		
		c.defineRoute().path("/testpageB").routeId(Routes.SMALL_EXAMPLE);
		c.defineRoute(HTTPHeaderDefaults.COOKIE)
				                  .path("/testpageC").routeId(Routes.LARGE_EXAMPLE);
		c.defineRoute().path("/testpageD").routeId(Routes.SPLIT_EXAMPLE);

		c.defineRoute()
		    .parseJSON()
		    	.stringField( "person.name", Params.PERSON_NAME)
		    	.integerField("person.age",  Params.PERSON_AGE)
		    .path("/testJSON")
			.routeId(Routes.JSON_EXAMPLE);
		
		c.defineRoute()
		     .path("/resources/${path}")
		     .routeId(Routes.RESOURCES_EXAMPLE);

		c.defineRoute()
	     	.path("/files/${path}")
	     	.routeId(Routes.FILES_EXAMPLE);

    }
    
    @Override
    public void declareBehavior(GreenRuntime runtime) {

        runtime.addRestListener(new RestBehaviorEmptyResponse(runtime, console))
                 .includeRoutesByAssoc(Routes.EMPTY_EXAMPLE);
        
        runtime.addRestListener(new RestBehaviorSmallResponse(runtime, console))
        		.includeRoutesByAssoc(Routes.SMALL_EXAMPLE);
        
        runtime.addRestListener(new RestBehaviorLargeResponse(runtime, console))
        		 .includeRoutesByAssoc(Routes.LARGE_EXAMPLE);
        
        
        String topic = "httpData";

        runtime.registerListener(new RestBehaviorHandoff(runtime, topic))
        		 .includeRoutesByAssoc(Routes.SPLIT_EXAMPLE);
		
        runtime.registerListener(new RestBehaviorHandoffResponder(runtime, console))
                 .addSubscription(topic);
  
        runtime.addRestListener(new RestBehaviorJsonResponce(runtime, console))
        		  .includeRoutesByAssoc(Routes.JSON_EXAMPLE);
        
    	runtime.addResourceServer("exampleSite")
		         .includeRoutesByAssoc(Routes.RESOURCES_EXAMPLE);

    	runtime.addFileServer("./src/main/resources/exampleSite") 
				 .includeRoutesByAssoc(Routes.FILES_EXAMPLE);
				        
        
        //NOTE .includeAllRoutes() can be used to write a behavior taking all routes

    }
   
}
