package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;


public class Shutdown implements GreenApp
{	
    @Override
    public void declareConfiguration(Builder c) {
    	c.enableServer(8443);    	
    	c.defineRoute("/shutdown?key=${key}");
    }
  
    @Override
    public void declareBehavior(final GreenRuntime runtime) {
    	runtime.registerListener(new ShutdownBehavior(runtime)).includeAllRoutes();	
    }          
          
}
