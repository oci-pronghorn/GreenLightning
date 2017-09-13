package com.ociweb.oe.greenlightning.api;

import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

/**
 * Unit test for simple App.
 */
public class AppTest { 
	
	 //@Test
	 @Ignore
	 public void testApp() {
				
		   long timeoutMS = 10_000;
		   GreenRuntime.testUntilShutdownRequested(new HTTPClient(), timeoutMS);
		
	 }
}
