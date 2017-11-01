package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.gl.api.Builder;

public class HTTPClient implements GreenApp
{

    @Override
    public void declareConfiguration(Builder c) {
    	c.useInsecureNetClient();
    	//c.NetClient();
    	
    	//c.enableTelemetry();
    	
    	//ClientCoordinator.showHistogramResults = true;
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {       
    	
    	HTTPGetBehaviorSingle temp = new HTTPGetBehaviorSingle(runtime);
		runtime.addStartupListener(temp).addSubscription("next");
			   	
    	
    //	int responseId = runtime.addResponseListener(new HTTPResponse()).getId();    	
    //	runtime.addStartupListener(new HTTPGetBehaviorChained(runtime, responseId));
    	    	
    	
    	runtime.addPubSubListener(new ShutdownBehavior(runtime)).addSubscription("shutdown");
    	
    }
          
}
