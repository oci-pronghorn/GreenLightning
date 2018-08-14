package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPClientConfig;

public class HTTPClient implements GreenApp
{
    private ClientHostPortInstance session1;
    private ClientHostPortInstance session2;
    
    private boolean telemetry;
    private StringBuilder console = new StringBuilder();
    
    public HTTPClient(boolean telemetry) {
    	this.telemetry = telemetry;
    }

    @Override
    public void declareConfiguration(Builder c) {
    	//c.useInsecureNetClient();

		HTTPClientConfig netClientConfig = c.useInsecureNetClient();//NetClient();
		session1 = netClientConfig
        		   .createHTTP1xClient("127.0.0.1", 8088)
	       		   .parseJSON()
	       		    .integerField("age", Fields.AGE) 
			    	.stringField("name", Fields.NAME)
        		   .finish();
		
		session2 = netClientConfig
     		   		.createHTTP1xClient("127.0.0.1", 8088)     		   		
	       		    .parseJSON()	       		   
	       		     .integerField("age", Fields.AGE)
			      	 .stringField("name", Fields.NAME)     		   
			      	.finish();
        
        if (telemetry) {
        	c.enableTelemetry();
        }
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {
    	
    	HTTPGetBehaviorSingle temp = new HTTPGetBehaviorSingle(runtime, session1);
			   	
    	runtime.addResponseListener(new HTTPResponse(console)).acceptHostResponses(session2);    	
    	runtime.addStartupListener(new HTTPGetBehaviorChained(runtime, session2));

    	runtime.addStartupListener("startupBehavior",temp)
							    	.acceptHostResponses(session1)  //this line is required to use JSON extraction even to self behavior as consumer 
							    	.addSubscription("next");
    	
    	runtime.addPubSubListener("shutdownBehavior",new ShutdownBehavior(runtime)).addSubscription("shutdown");
    	
    }
          
}
