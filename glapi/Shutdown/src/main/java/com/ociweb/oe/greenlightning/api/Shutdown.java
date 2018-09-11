package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.HTTPServerConfig;
import com.ociweb.pronghorn.network.NetGraphBuilder;


public class Shutdown implements GreenApp
{	
	private final String host;
	private long keyFieldId;
	
	public Shutdown(String host) {
		this.host = host;
	}
	
	public Shutdown() {
		this.host = null;
	}
	
    @Override    
    public void declareConfiguration(GreenFramework c) {
    	
    	HTTPServerConfig conf = c.useHTTP1xServer(8443)
    			.setHost(NetGraphBuilder.bindHost(host))
    			.setDecryptionUnitsPerTrack(4)
    			.setDefaultPath("");
    	    	
    	int aRouteId = c.defineRoute().path("/shutdown?key=${key}").routeId();
    	
    	keyFieldId = c.lookupFieldByName(aRouteId, "key");
    }
  
    @Override
    public void declareBehavior(final GreenRuntime runtime) {
    	runtime.registerListener(new ShutdownBehavior(runtime, keyFieldId)).includeAllRoutes();	
    }          
          
}
