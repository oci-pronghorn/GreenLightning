package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.Appendables;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	
	    @Test
	    public void testApp() {
		 
		     StringBuilder result = new StringBuilder();
		     StringBuilder result2 = new StringBuilder();
		     		 
			 long timeoutMS = 10_000;
			 boolean cleanExit = GreenRuntime.testUntilShutdownRequested(new PubSubStructured(result, result2), timeoutMS);
			
			 ////////////	 
			 //System.out.println(result);
			 //System.err.println(result2);		 
			 ////////////
			 
			 CharSequence[] rows = Appendables.split(result, '\n');
			 		 
			 assertTrue(cleanExit);
			 assertEquals(101, rows.length);
			 
			 CharSequence[] rows2 = Appendables.split(result2, '\n');
			 
			 assertEquals(34, rows2.length);
			 assertEquals("3",rows2[0]);
			 assertEquals("99",rows2[rows2.length-2]);
		 
		 
	    }
}
