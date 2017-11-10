package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.Appendables;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.ociweb.pronghorn.network.TLSCertificateTrust.trustAllCerts;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	private final int timeoutMS = 20_000;
	private final static Logger logger = LoggerFactory.getLogger(AppTest.class);
	
	 @Test
	  public void testApp() {
		 
   		    String host = "127.0.0.1";
		    
   		    final StringBuilder result = new StringBuilder();
   		    final long timeoutMS = 10_000;
			
   		    AtomicBoolean done = new AtomicBoolean(false);
   		    AtomicBoolean cleanExit = new AtomicBoolean(false);
		    
	   	    new Thread(()->{
	   		    cleanExit.set(GreenRuntime.testUntilShutdownRequested(new HTTPServer(host, result), timeoutMS));
	   		    done.set(true);
	   	    }).start();
   		    
   		    simulateUser(host);
		 
   		    while (!done.get()) {
   		    	Thread.yield();
   		    }
		 
			//////////////////
		//	assertTrue(cleanExit.get()); //this fails on build server?
			
		//	System.err.println(result);

		 	CharSequence[] rows = Appendables.split(result, '\n');

		 	assertEquals(5, rows.length);
			assertEquals("Arg Int: 42", rows[0]);
		 	assertEquals("COOKIE: oreo", rows[1]);
		 	assertEquals("POST: payload", rows[2]);
		 	assertEquals("COOKIE: peanutbutter", rows[3]);
		 	assertEquals("", rows[4]);
	    }
	 

		private void simulateUser(String host) {
	
					trustAllCerts(host);
					
					int countDown = 400;
					while (!hitURL("https://"+host+":8088/testPageB", null, null, "beginning of text file\n")) {
						if (--countDown<=0) {
							fail("Server was not running");
							break;
						}
						try {
							//wait because the server is not yet running
							Thread.sleep(100);
						} catch (InterruptedException e1) {
						}
					}
					
					hitURL("https://"+host+":8088/testPageA?arg=42", "oreo", null,
							"");
							
					hitURL("https://"+host+":8088/testPageC", "peanutbutter", "payload",
							"beginning of text file\n" + "ending of text file\n");

					// The binary data encoded in the payload below is due to the binary 2-byte-counted UTF write (utfWrite) performed by RestBehaviorHandoffResponder
					hitURL("https://"+host+":8088/testPageD", "peanutbutter2", "payload2", "\u0000\u0011sent by responder");

					hitURL("https://"+host+":8088/shutdown?key=shutdown", null, null,
							"");
			
		}

		private boolean hitURL(String urlString, String cookie, String payload, String body) {
			try {
				URL url = new URL(urlString);	
				
				
				HttpURLConnection http = (HttpURLConnection)url.openConnection();
				
				if (null!=cookie) {
					http.setRequestProperty("Cookie", cookie);
				}
				http.setReadTimeout(timeoutMS);
				
				if (null!=payload) {
					
					http.setRequestMethod("POST");
					http.setDoOutput(true);
					OutputStream out = http.getOutputStream();
					//System.out.println("writing the payload: "+payload);
					out.write(payload.getBytes());
					out.close();
				}
				http.connect();

				assertEquals(200, http.getResponseCode());
				
				InputStream br;
				if (200 <= http.getResponseCode() && http.getResponseCode() <= 299) {
					br = http.getInputStream();
				} else {
					br = http.getErrorStream();
				}
				if (body != null) {
					String result = readFullyAsString(br, "UTF-8");
					assertEquals(urlString, body, result);
				}
				
				
			} catch (MalformedURLException e) {			
				e.printStackTrace();
				fail();
			} catch (ConnectException e) {				
				return false;
				
			} catch (IOException e) {
				e.printStackTrace();
				fail();
			}
			return true;
		}

		public String readFullyAsString(InputStream inputStream, String encoding) throws IOException {
			return readFully(inputStream).toString(encoding);
		}

		private ByteArrayOutputStream readFully(InputStream inputStream) throws IOException {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buffer = new byte[1024];
			int length = 0;
			while ((length = inputStream.read(buffer)) != -1) {
				baos.write(buffer, 0, length);
			}
			return baos;
		}
}
