package com.ociweb.gl.benchmark;
/**
 * ************************************************************************
 * For greenlightning support, training or feature reqeusts please contact:
 *   info@objectcomputing.com   (314) 579-0066
 * ************************************************************************
 */
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	
	 @Test
	 public void testApp() {
		    int timeoutMS = 2000;
		    GreenRuntime.testUntilShutdownRequested(new FrameworkTest(), timeoutMS);
	
	 }
	 
	 
}
