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
import com.ociweb.gl.api.HTTPResponseService;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;

public class FrameworkTest implements GreenApp {

    private int bindPort = 8080;
    private String host = "localhost";
    static byte[] payload = "Hello, World!".getBytes();

	@Override
    public void declareConfiguration(GreenFramework framework) {

		framework.useHTTP1xServer(bindPort,this::parallelBehavior) //standard auto-scale
    			 .setHost(host)
    			 .setMaxQueueIn(1<<15)
    			 .setMaxQueueOut(1<<15)
    	         .useInsecureServer(); //turn off TLS
        
		framework.defineRoute()
		         .path("/plaintext")
		         .routeId(Struct.PLAINTEXT_ROUTE);
		
		framework.defineRoute()
		        .path("/json")
		        .routeId(Struct.JSON_ROUTE);
		
		framework.enableTelemetry();
		
    }

	public void parallelBehavior(GreenRuntime runtime) {
		 
		//plain text is so simple we can use a lambda
		final HTTPResponseService plainResponseService = runtime.newCommandChannel().newHTTPResponseService();    	
		runtime.addRestListener((r)->{
			
			return plainResponseService.publishHTTPResponse(r, 	
						HTTPContentTypeDefaults.PLAIN,
						(w) -> w.write(payload)
					);
			
		}).includeRoutes(Struct.PLAINTEXT_ROUTE);
		
		//JSON test is a little more complex and needs a behavior object unlike the lambda above
		runtime.addRestListener(new JSONBehaviorInstance(runtime)).includeRoutes(Struct.JSON_ROUTE);

	}
	 
    @Override
    public void declareBehavior(GreenRuntime runtime) {   
    	
    }
  
}
