package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.oe.greenlightning.api.server.HTTPServer;

/**
 * Unit test for simple App.
 */
public class AppTest { 
	
	 private static final String host = "127.0.0.1";
	
	 private static StringBuilder result;
 	 private static GreenRuntime runtime;
	 
	 @BeforeClass
	 public static void startup() {
		 
		 result = new StringBuilder();		
		 runtime = GreenRuntime.run(new HTTPServer(host, result));
		 
	 }
	 
	 @AfterClass
	 public static void shutdown() {
		 runtime.shutdownRuntime();
	 }
	 
	 @Test
	 public void testServer() {
		 
		 	LoadTester.runClient(null, 
		 			(i,r)-> {return r.structured().readPayload().equalBytes("{\"age\":123,\"name\":\"bob\"}".getBytes());},
		 			"/testPageB", 
		 			false, false, 
		 			1, 10_000, 
		 			host, 8088, 
		 			10_000, System.out);
		 
	 }
	 
	 @Test
	 public void testApp() {
			
		    boolean telemetry = false;
		 	final long timeoutMS = 10_000*60;
		    
		    //System.out.println("starting up client");
	   	    
	   	    //this test will hit the above server until it calls shutdown.
		    boolean cleanExit =  GreenRuntime.testConcurrentUntilShutdownRequested(new HTTPClient(telemetry), timeoutMS);
		
		    assertTrue(cleanExit);
		    
	 }
	 


}
