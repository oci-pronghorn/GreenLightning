package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertThat;
import static org.hamcrest.core.StringStartsWith.startsWith;
import static org.hamcrest.core.StringEndsWith.endsWith;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.Appendables;


public class AppTest { 
	
	 @Test
	    public void testApp()
	    {
		    StringBuilder result = new StringBuilder();
		    
		    boolean cleanExit = GreenRuntime.testUntilShutdownRequested(new StateMachine(result,1), 500);
		    
		    /////////////////////////////
		    //System.out.println(result);
		    /////////////////////////////

		    CharSequence[] rows = Appendables.split(result, '\n');
		    
		    assertTrue(cleanExit);
		    assertEquals(17, rows.length);	
		    int i = 0;
		    int iterations = 3;
		    while (--iterations>=0) {
			    assertThat(rows[i++].toString(), startsWith("Green"));
			    assertThat(rows[i++].toString(), endsWith("Go"));
			    assertThat(rows[i++].toString(), startsWith("Yellow"));
			    assertThat(rows[i++].toString(), startsWith("Red"));
			    assertThat(rows[i++].toString(), endsWith("Stop"));
		    }
	    }
	
}
