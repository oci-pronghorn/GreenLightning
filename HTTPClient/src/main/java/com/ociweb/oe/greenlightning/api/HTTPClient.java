package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.Builder;

public class HTTPClient implements GreenApp
{

    @Override
    public void declareConfiguration(Builder c) {
    	c.useNetClient();
    	c.enableTelemetry();
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {       
    	
    	HTTPGetBehaviorSingle temp = new HTTPGetBehaviorSingle(runtime);
		runtime.addStartupListener(temp);
			   	
    	
    	int responseId = runtime.addResponseListener(new HTTPResponse()).getId();    	
    	runtime.addStartupListener(new HTTPGetBehaviorChained(runtime, responseId));
    	
    	
    	
    }
          
}
