package com.mydomain.greenlightning.slipstream;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	static int port = (int) (3000 + (System.nanoTime()%12000));
	
	 @Test
	 public void testApp() {
		    int timeoutMS = 1000;
		    GreenRuntime.testUntilShutdownRequested(new MyMicroservice(false, port, false), timeoutMS);
		    	    
			
	 }
	 
	 
}
