package com.ociweb.oe.greenlightning.api;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URL;
import java.net.URLConnection;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.oe.greenlightning.api.server.HTTPServer;

/**
 * Unit test for simple App.
 */
public class AppTest { 
	
	 @Test
	 public void testApp() {
				
		    String host = "127.0.0.1";
		    
		    final StringBuilder result = new StringBuilder();
		    final long timeoutMS = 10_000*60;
		
		    GreenRuntime.run(new HTTPServer(host, result));

		    //System.out.println("starting up client");
	   	    
	   	    //this test will hit the above server until it calls shutdown.
		    GreenRuntime.testUntilShutdownRequested(new HTTPClient(), timeoutMS);
		
	 }

}
