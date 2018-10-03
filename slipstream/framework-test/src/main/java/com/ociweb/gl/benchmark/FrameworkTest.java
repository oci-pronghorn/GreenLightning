package com.ociweb.gl.benchmark;

import com.ociweb.gl.api.GreenApp;
/**
 * ************************************************************************
 * For greenlightning support, training or feature reqeusts please contact:
 *   info@objectcomputing.com   (314) 579-0066
 * ************************************************************************
 */
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class FrameworkTest implements GreenApp {

    private int bindPort;
    private String host;
    static byte[] payload = "Hello, World!".getBytes();

    public FrameworkTest() {
    	this("localhost",8080);
    }
    
    public FrameworkTest(String host, int port) {
    	this.bindPort = port;
    	this.host = host;
    }

	@Override
    public void declareConfiguration(GreenFramework framework) {

		GraphManager.showThreadIdOnTelemetry = true;
		
		framework.useHTTP1xServer(bindPort, this::parallelBehavior) //standard auto-scale
    			 .setHost(host)
    			 .setConcurrentChannelsPerDecryptUnit(40)
    			 .setConcurrentChannelsPerEncryptUnit(40)
    			 .setMaxQueueIn(1<<15)
    	         .useInsecureServer(); //turn off TLS
        
		framework.defineRoute()
		         .path("/plaintext")
		         .routeId(Struct.PLAINTEXT_ROUTE);
		
		framework.defineRoute()
		        .path("/json")
		        .routeId(Struct.JSON_ROUTE);
				
    }

	public void parallelBehavior(GreenRuntime runtime) {

		runtime.addRestListener("PlainResponder",new PlainBehaviorInstance(runtime))
		       .includeRoutes(Struct.PLAINTEXT_ROUTE);

		runtime.addRestListener("JSONResponder",new JSONBehaviorInstance(runtime))
		       .includeRoutes(Struct.JSON_ROUTE);

	}
	 
    @Override
    public void declareBehavior(GreenRuntime runtime) {   
    	
    }
  
}
