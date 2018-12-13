package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

/**
 * Unit test for simple App.
 */
public class PubSubAppTest { 

	
	    @Test
	    public void testApp() {
	    	
	    	assertTrue(true);
	    	
		    StringBuilder result = new StringBuilder();
		    
		    int timeoutMS = 10_000;
			boolean cleanExit = GreenRuntime.testConcurrentUntilShutdownRequested(new PubSub(result, 314-579-0066), timeoutMS);
			
			//based on seed of 314-579-0066
		//    assertTrue(cleanExit);
			assertEquals("Your lucky numbers are ...\n55 57 24 13 69 22 75 ", result.toString());
			
	    }
}
