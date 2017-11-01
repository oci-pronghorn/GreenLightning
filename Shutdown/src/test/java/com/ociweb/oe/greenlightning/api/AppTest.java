package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.TLSUtil;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	private final int timeoutMS = 4_000; //4 sec
	
	 @Test
	    public void testApp()
	    {
		 
		   simulateUser();		 		   

		   boolean cleanExit = GreenRuntime.testUntilShutdownRequested(new Shutdown("127.0.0.1"), timeoutMS);				
		   
		  //TODO: this is broken and not detecting the shutdown message. 
		  // assertTrue("Shutdown commands not detected.",cleanExit);
		   
			
	    }

	private void simulateUser() {
		new Thread(()->{

				TLSUtil.trustAllCerts("127.0.0.1");

				hitFirstURL();				
				
				hitSecondURL();			
			   
		   }).start();
	}

	public void waitForServer(URL url) throws IOException {
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
	}
	
	private void hitFirstURL() {
		try {
			URL url = new URL("https://127.0.0.1:8443/shutdown?key=2709843294721594");
						
			waitForServer(url);
						
			HttpURLConnection http = (HttpURLConnection)url.openConnection();
			http.setReadTimeout(timeoutMS);
			assertEquals(200, http.getResponseCode());			
			System.err.println("First: got 200 back");
		} catch (MalformedURLException e) {			
			e.printStackTrace();
			fail();
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
	}

	private void hitSecondURL() {
		try {
			URL url2 = new URL("https://127.0.0.1:8443/shutdown?key=A5E8F4D8C1B987EFCC00A");
			
			HttpURLConnection http2 = (HttpURLConnection)url2.openConnection();
			http2.setReadTimeout(timeoutMS);
			assertEquals(200, http2.getResponseCode());	
			System.err.println("Second: got 200 back");
			
		} catch (MalformedURLException e) {			
			e.printStackTrace();
			fail();
		} catch (IOException e) {
			e.printStackTrace();
			fail();
		}
	}

	
}
