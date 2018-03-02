package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPServerConfig;
import com.ociweb.pronghorn.network.NetGraphBuilder;


public class Shutdown implements GreenApp
{	
	private final String host;
	public Shutdown(String host) {
		this.host = host;
	}
	
	public Shutdown() {
		this.host = null;
	}
	
    @Override    
    public void declareConfiguration(Builder c) {
    	
    	HTTPServerConfig conf = c.useHTTP1xServer(8443)
    			.setHost(NetGraphBuilder.bindHost(host))
    			.setDefaultPath("");
    	    	
    	c.defineRoute().path("/shutdown?key=${key}");
    }
  
    @Override
    public void declareBehavior(final GreenRuntime runtime) {
    	runtime.registerListener(new ShutdownBehavior(runtime)).includeAllRoutes();	
    }          
          
}
