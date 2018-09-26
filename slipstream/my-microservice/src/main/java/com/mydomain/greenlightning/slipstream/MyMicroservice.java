package com.mydomain.greenlightning.slipstream;

import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.json.JSONRequired;
import com.ociweb.pronghorn.network.HTTPServerConfig;

//applications must extend GreenApp
public class MyMicroservice implements GreenApp {

	private final static int maxProductId = 99999;
	
	private int port;	
	private boolean tls;
	private boolean telemetry;
	
	public MyMicroservice() {
		this (true, 1443, false);
	}
	
	//the app can take optional arguments to make testing easier
	public MyMicroservice(boolean defaultTLS, int defaultPort, boolean defaultTelemetry) {
		this.port = defaultPort;
		this.tls = defaultTLS;
		this.telemetry = defaultTelemetry;
	}
	
    @Override
    public void declareConfiguration(GreenFramework builder) {

    	//override arguments if provided by the command line
    	//note that GreenFramework holds arg[] 
    	tls = builder.getArgumentValue("encrypt", "-e", tls);
    	telemetry = builder.getArgumentValue("telemetry", "-t", telemetry);
    	port = builder.getArgumentValue("port", "-p", port);
    	
    	//declare web server and its port
    	HTTPServerConfig c = builder
    	  .useHTTP1xServer(port)
    	  
    	  //sets (1<<6) or 64 max connections
    	  //Note that the default is 32K connections when not set
    	  .setMaxConnectionBits(6) 

    	  //bump up the number of concurrent connections all using the same
    	  //decryption stage.  This increases how many concurrent writes are 
    	  //supported.  The count supported is reported in the console at startup.
    	  .setConcurrentChannelsPerDecryptUnit(4)
    	  
    	  .setDecryptionUnitsPerTrack(1)
    	  .setEncryptionUnitsPerTrack(1)
    	  
    	  //IP home where this site is hosted.  This is the internal IP.
    	  //This value supports stars so "10.*.*.*" is a common value used.
    	  //If this is unset the system will pick the most public IP  it can find.
    	  .setHost("127.0.0.1");
    	
    	//by default all servers have TLS turned on, By calling this method
    	//TLS is disabled and a warning will be logged.
    	if (!tls) {
    		c.useInsecureServer();
    	}
    	
    	//by default telemetry is not enabled but it is recommended that all
    	//projects enable it.  Telemetry does not impact performance yet it provides
    	//details about the system work flow which can help diagnose many issues.
        if (telemetry) {
        	builder.enableTelemetry();
        }
        
        //define a route. This is a simple get which accepts 1 argument id 
        //which is an integer and must valid, eg positive and <= max product Id        
    	builder
    	  .defineRoute()
    	  .path("/query?id=#{ID}")
    	  .refineInteger("ID", Field.ID, v-> v>=0 & v<=maxProductId)
    	  .routeId(Struct.PRODUCT_QUERY);
    	
    	//define route for doing updates
    	//NOTE: inside the framework Strings are not passed around instead we use 4 fields as seen in the name field validator
    	//      b - backing array made of utf8 encoded bytes, p - position in array where text starts, l - count of bytes making up text
    	//      m - mask which is used when walking the distance l from p.  The mask allows the index to wrap back to the beginning of the array
    	builder
	  	  .defineRoute()
	  	  .parseJSON()
	  	    .integerField("id", Field.ID, JSONRequired.REQUIRED, v -> v>=0 & v<=maxProductId)
	  	    .stringField("name", Field.NAME, JSONRequired.REQUIRED, (b,p,l,m) -> l>0 & l<=4000) 
	  	    .booleanField("disabled", Field.DISABLED, JSONRequired.REQUIRED)
	  	    .integerField("quantity", Field.QUANTITY, JSONRequired.REQUIRED, v -> v>=0 && v<=1_000_000) 
	  	  .path("/update")
	  	  .routeId(Struct.PRODUCT_UPDATE);
	  	
    	builder
    	  .defineRoute()
    	  .path("/${path}")
    	  .routeId(Struct.STATIC_PAGES);
    	
    	builder
	  	  .defineRoute()
	  	  .path("/all")
	  	  .routeId(Struct.ALL_PRODUCTS);
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) { 
    	//in most cases the behavior need not be a local variable but we are using methods off the object
    	//to respond to specific published topics.  To do this the object is required.
        ProductsBehavior listener = new ProductsBehavior(runtime, maxProductId);
		runtime.registerListener(listener)
				.includeRoutes(Struct.PRODUCT_UPDATE, listener::productUpdate)
				.includeRoutes(Struct.ALL_PRODUCTS, listener::productAll)				
                .includeRoutes(Struct.PRODUCT_QUERY, listener::productQuery);
        
		runtime.addResourceServer("/site","index.html").includeRoutesByAssoc(Struct.STATIC_PAGES);
		
    }
}
