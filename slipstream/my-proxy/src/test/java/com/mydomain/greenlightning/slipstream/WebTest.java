package com.mydomain.greenlightning.slipstream;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.pronghorn.util.Appendables;

public class WebTest {
	
	static GreenRuntime runtime;
	
	static int port = 8080;
	static int telemetryPort = 8097;
	static String host = "127.0.0.1";	
	static int timeoutMS = 240_000;	
	static boolean telemetry = false;
	static int cyclesPerTrack = 2_000; 
	static boolean useTLS = true;
	static int parallelTracks = 2;
	
	
	@BeforeClass
	public static void startServer() {
		
		runtime = GreenRuntime.run(new MyProxy(useTLS));
		
	}

	@AfterClass
	public static void stopServer() {
		runtime.shutdownRuntime();	
		runtime = null;
	}
	
	@Test
	public void testProxy() {
		
		StringBuilder results = new StringBuilder();
		
		LoadTester.runClient(null, //nothing to post in this test
				             (i,r) -> {				            	 
				            	 return 200==r.statusCode() 
				            			&& r.structured().readPayload().equalBytes("Hello World".getBytes());
				             } ,
				             "/proxy", 
							 useTLS, telemetry, 
				             parallelTracks, cyclesPerTrack, 
				             host, port, 
				             timeoutMS, 3,
				             Appendables.join(results,System.out));
		
		System.out.println(results);
		
	}
	
	
}
