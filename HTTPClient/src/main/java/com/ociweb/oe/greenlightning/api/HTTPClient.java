package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPSession;

public class HTTPClient implements GreenApp
{

    @Override
    public void declareConfiguration(Builder c) {
    	c.useInsecureNetClient();
    	//c.NetClient();
    	
    	//c.enableTelemetry();
    	//c.enableTelemetry("127.0.0.1",8099);
    	
    	//ClientCoordinator.showHistogramResults = true;
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {       
    	
    	HTTPGetBehaviorSingle temp = new HTTPGetBehaviorSingle(runtime);
		runtime.addStartupListener(temp).addSubscription("next");
			   	
		//HTTPSession session = new HTTPSession("127.0.0.1",8088,0);
    	//runtime.addResponseListener(new HTTPResponse()).includeHTTPSession(session);    	
    	//runtime.addStartupListener(new HTTPGetBehaviorChained(runtime, session));
    	    	
    	
    	runtime.addPubSubListener(new ShutdownBehavior(runtime)).addSubscription("shutdown");
    	
    }
          
}
