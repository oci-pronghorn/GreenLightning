package com.ociweb.oe.greenlightning.api;


import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.json.JSONType;
import com.ociweb.gl.api.ClientHostPortInstance;

public class HTTPClient implements GreenApp
{
    private ClientHostPortInstance session;
    private boolean telemetry;
    
    public HTTPClient(boolean telemetry) {
    	this.telemetry = telemetry;
    }

    @Override
    public void declareConfiguration(Builder c) {
    	//c.useInsecureNetClient();
        JSONExtractorCompleted extractor =
        		c.defineJSONSDecoder()
        		 .begin()
        		 .element(JSONType.TypeInteger)
        		 .asField("ID1", Fields.ID1)
        		 
        		 .element(JSONType.TypeString)
        		 .asField("ID2", Fields.ID2)
        		 .finish();
        		 
		session = c.useNetClient()
        		   .createHTTP1xClient("127.0.0.1", 8088)
        		   .setExtractor(extractor)
        		   .finish();
        
        if (telemetry) {
        	c.enableTelemetry();
        }
        
        //{"ID1":123,"ID2":"hello"}
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {
    	
    	HTTPGetBehaviorSingle temp = new HTTPGetBehaviorSingle(runtime, session);
		runtime.addStartupListener("startupBehavior",temp)
		               .acceptHostResponses(session)  //this line is required to use JSON extraction even to self behavior as consumer 
		               .addSubscription("next");
			   	
		//HTTPSession session = new HTTPSession("127.0.0.1",8088,0);
    	//runtime.addResponseListener(new HTTPResponse()).includeHTTPSession(session);    	
    	//runtime.addStartupListener(new HTTPGetBehaviorChained(runtime, session));
    	    	
    	
    	runtime.addPubSubListener("shutdownBehavior",new ShutdownBehavior(runtime)).addSubscription("shutdown");
    	
    }
          
}
