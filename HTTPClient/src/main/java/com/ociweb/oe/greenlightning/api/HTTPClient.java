package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.ClientHostPortInstance;

public class HTTPClient implements GreenApp
{
    ClientHostPortInstance session;

    @Override
    public void declareConfiguration(Builder c) {
    	//c.useInsecureNetClient();
        session = c.useNetClient().createHTTP1xClient("127.0.0.1", 8088).finish();
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {
    	
    	HTTPGetBehaviorSingle temp = new HTTPGetBehaviorSingle(runtime, session);
		runtime.addStartupListener(temp).addSubscription("next");
			   	
		//HTTPSession session = new HTTPSession("127.0.0.1",8088,0);
    	//runtime.addResponseListener(new HTTPResponse()).includeHTTPSession(session);    	
    	//runtime.addStartupListener(new HTTPGetBehaviorChained(runtime, session));
    	    	
    	
    	runtime.addPubSubListener(new ShutdownBehavior(runtime)).addSubscription("shutdown");
    	
    }
          
}
