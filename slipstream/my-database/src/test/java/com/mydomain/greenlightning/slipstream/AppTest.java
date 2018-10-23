package com.mydomain.greenlightning.slipstream;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	
	 @Test
	 public void testApp() {
		    int timeoutMS = 1000;
		    GreenRuntime.testUntilShutdownRequested(new MyDBMicroservice(true, 1443, false), timeoutMS);
		    	    
			
	 }
	 
	 
}
