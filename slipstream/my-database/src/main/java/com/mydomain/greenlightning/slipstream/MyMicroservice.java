package com.mydomain.greenlightning.slipstream;

import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.json.JSONRequired;
import com.ociweb.pronghorn.network.HTTPServerConfig;

public class MyMicroservice implements GreenApp {

	private final static int maxProductId = 99999;
	
	private static final String BEGIN_TOPIC = "begin";
	private static final String FINISHED_TOPIC = "finish";
	
	private final int port;	
	private final boolean tls;
	private final boolean telemetry;
	
	public MyMicroservice(boolean tls, int port, boolean telemetry) {
		this.port = port;
		this.tls = tls;
		this.telemetry = telemetry;
	}
	
    @Override
    public void declareConfiguration(GreenFramework builder) {

    	HTTPServerConfig c = builder
    	  .useHTTP1xServer(port)
    	  .setMaxConnectionBits(9) 
    	  .setConcurrentChannelsPerDecryptUnit(4)
    	  .setConcurrentChannelsPerEncryptUnit(4)
    	  .setHost("127.0.0.1");
    	
    	if (!tls) {
    		c.useInsecureServer();
    	}
    	
        if (telemetry) {
        	builder.enableTelemetry();
        }
        
    	builder
    	  .defineRoute()
    	  .path("/query?id=#{ID}")
    	  .refineInteger("ID", Field.ID, v-> v>=0 & v<=maxProductId) 
    	  .routeId(Struct.PRODUCT_QUERY);
    	
    	builder
	  	  .defineRoute()
	  	  .parseJSON()
	  	    .integerField("id", Field.ID, JSONRequired.REQUIRED, v -> v>=0 & v<=maxProductId)
	  	    .stringField("name", Field.NAME, JSONRequired.REQUIRED, (b,p,l,m) -> l>0 & l<=4000)
	  	    .booleanField("disabled", Field.DISABLED, JSONRequired.REQUIRED)
	  	    .integerField("quantity", Field.QUANTITY, JSONRequired.REQUIRED, v -> v>=0 && v<=1_000_000) //if missing not returning 404? get exception?
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
    	
    	builder
    	  .defineStruct()
    	  .longField(Field.CONNECTION)
    	  .longField(Field.SEQUENCE)
    	  .integerField(Field.ID)
    	  .integerField(Field.QUANTITY)
    	  .booleanField(Field.DISABLED)
    	  .stringField(Field.NAME)
    	  .register(Struct.DB_PRODUCT_UPDATE);
    	
    	builder
	  	  .defineStruct()
	  	  .longField(Field.CONNECTION)
	  	  .longField(Field.SEQUENCE)
	  	  .integerField(Field.ID)
	  	  .register(Struct.DB_PRODUCT_QUERY);
	    	
    	builder
	  	  .defineStruct()
	  	  .longField(Field.CONNECTION)
	  	  .longField(Field.SEQUENCE)
	  	  .register(Struct.DB_ALL_QUERY);
    	
    	builder
	  	  .defineStruct()
	  	  .longField(Field.CONNECTION)
	  	  .longField(Field.SEQUENCE)
	  	  .integerField(Field.STATUS)
	  	  .stringField(Field.PAYLOAD)
	  	  .register(Struct.RESPONSE);
    	
    	builder.setStartupLimitMS(300);
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) { 
        ProductsBehavior listener = new ProductsBehavior(runtime, maxProductId, BEGIN_TOPIC);
		runtime.registerListener(listener)
				.includeRoutes(Struct.PRODUCT_UPDATE, listener::productUpdate)
				.includeRoutes(Struct.ALL_PRODUCTS, listener::productAll)				
                .includeRoutes(Struct.PRODUCT_QUERY, listener::productQuery);
   
		int threads = 40;
		long timeoutNS = 10_000_000_000L; //10 sec		
		
		runtime.registerBlockingListener(new BlockingProducer(), Field.CONNECTION, 
				                         threads, timeoutNS, BEGIN_TOPIC, FINISHED_TOPIC);
		
		runtime.addPubSubListener(new RestResponder(runtime)).addSubscription(FINISHED_TOPIC);
		
		runtime.addResourceServer("/site","index.html").includeRoutesByAssoc(Struct.STATIC_PAGES);
		
    }
}
