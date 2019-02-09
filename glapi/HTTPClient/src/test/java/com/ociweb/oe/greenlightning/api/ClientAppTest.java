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
public class ClientAppTest { 
	
	 private static final String host = "127.0.0.1";
	 private static int port = (int) (3000 + (System.nanoTime()%12000));
	 
	 private static StringBuilder result;
 	 private static GreenRuntime runtime;
	 
	 @BeforeClass
	 public static void startup() {
		 
		 result = new StringBuilder();		
		 runtime = GreenRuntime.run(new HTTPServer(host, result, port));
		 
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
		 			1, 200, 
		 			host, port, 
		 			10_000, System.out);
		 
	 }
	

}
