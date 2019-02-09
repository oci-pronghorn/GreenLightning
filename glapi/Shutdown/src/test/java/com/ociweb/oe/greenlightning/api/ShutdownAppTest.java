package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenRuntime;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.*;

import static com.ociweb.pronghorn.network.TLSCertificateTrust.httpsURLConnectionTrustAllCerts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Unit test for simple App.
 */
public class ShutdownAppTest { 

	private final int timeoutMS = 240_000; //240 sec
	private final static Logger logger = LoggerFactory.getLogger(ShutdownAppTest.class);
	static int port = (int) (3000 + ((131+System.nanoTime())%12000));
		
	 @Test
	 public void testApp() {
	 
	   simulateUser();

	   boolean cleanExit = GreenRuntime.testConcurrentUntilShutdownRequested(new Shutdown("127.0.0.1",port), timeoutMS);				

	   assertTrue("Shutdown commands not detected.",cleanExit);
	   //TODO: second URL should have shut down??
		
	 }

	private void simulateUser() {
		new Thread(()->{

				httpsURLConnectionTrustAllCerts("127.0.0.1");

				hitFirstURL();				
	
				hitSecondURL();			
			   
		   }).start();
	}

	public void waitForServer(URL url) throws IOException {
		boolean waiting = true;				
		while (waiting) {
			try {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
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
			URL url = new URL("https://127.0.0.1:"+port+"/shutdown?key=2709843294721594");
						
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
			URL url2 = new URL("https://127.0.0.1:"+port+"/shutdown?key=A5E8F4D8C1B987EFCC00A");
			
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
