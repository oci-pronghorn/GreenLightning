package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.Appendables;

public class AppTest { 
	
	 @Test
	    public void testApp()
	    {
		    StringBuilder result = new StringBuilder();
		    		    
		    long timeoutMS = 100;
			boolean cleanExit = GreenRuntime.testUntilShutdownRequested(new Timer(result, 1), timeoutMS);

			////////////////////////////
			//System.out.println(builder);
			////////////////////////////			
			
			assertTrue("Test did not exit", cleanExit);
			
			CharSequence[] rows = Appendables.split(result, '\n');
			
			assertEquals(7, rows.length);
			assertEquals("Event Triggered",rows[5]);
			
	    }
}
