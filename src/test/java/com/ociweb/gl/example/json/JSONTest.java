package com.ociweb.gl.example.json;

import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ConnectException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;

public class JSONTest {

	
	@Test
	public void testSimpleCall() {
		
		//startup backing server
		GreenRuntime.run(new ExampleJSONConsume());
    	waitForServer("http://127.0.0.1:8078/");
    	
    	String post = "{\"root\": {\"keyb\":\"hello\", \"keya\":123} }";
    	String route = "/test";
    	long timeoutMS = 2_000;
		//GreenRuntime.testConcurrentUntilShutdownRequested(new TestClientParallel(12000,8786), timeoutMS);	 
	    GreenRuntime.testUntilShutdownRequested(new ParallelClientLoadTester(2, 8078, route, post, false), timeoutMS);	

	}
	
	private void waitForServer(String url) {
		try {
		
			waitForServer(new URL(url));
		
		} catch (MalformedURLException e) {
			
			e.printStackTrace();
			fail();
		}
	}
	
	private void waitForServer(URL url) {
		try {
			boolean waiting = true;				
			while (waiting) {
				try {
					URLConnection con = url.openConnection();				
					con.connect();
				} catch (ConnectException ce) {
					continue;
				}
				waiting = false;
			}
		} catch (IOException ex) {
			throw new RuntimeException(ex);
		}
	}
	
}
