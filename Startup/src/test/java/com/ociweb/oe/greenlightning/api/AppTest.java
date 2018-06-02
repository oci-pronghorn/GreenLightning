package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	
	 @Test
	    public void testApp()
	    {
		 	StringBuilder result = new StringBuilder();
		 	boolean cleanExit = 
		 			GreenRuntime.testConcurrentUntilShutdownRequested(new Startup(result), 100);

		 	/////////////////////
		 	//System.out.println(result);
		    ////////////////////
		 	
		 	assertTrue(cleanExit);
		 	assertEquals("Hello, this message will display once at start\n",result.toString());
		 	
			
	    }
}
