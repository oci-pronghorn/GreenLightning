package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;


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
    	c.enableServer(host, 8443);    	
    	c.defineRoute("/shutdown?key=${key}");
    }
  
    @Override
    public void declareBehavior(final GreenRuntime runtime) {
    	runtime.registerListener(new ShutdownBehavior(runtime)).includeAllRoutes();	
    }          
          
}
