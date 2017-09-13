package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.TLSUtil;
import com.ociweb.pronghorn.util.Appendables;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	private final int timeoutMS = 20_000;
	
	 @Test
	    public void testApp() {
		 
   		    String host = "127.0.0.1";
		    
   		    simulateUser(host);
		 
   		    StringBuilder result = new StringBuilder();
		 
		    long timeoutMS = 10_000;
			GreenRuntime.testUntilShutdownRequested(new HTTPServer(host, result), timeoutMS);

			//////////////////
			
			System.err.println(result);
			
			CharSequence[] rows = Appendables.split(result, '\n');
			
			
			
			
			
	    }
	 

		private void simulateUser(String host) {
			new Thread(()->{

					TLSUtil.trustAllCerts(host);
					
					int countDown = 200;
					while (!hitURL("https://"+host+":8088/testPageB", null, null)) {
						if (--countDown<=0) {
							fail("Server was not running");
							break;
						}
						try {
							//wait because the server is not yet running
							Thread.sleep(40);
						} catch (InterruptedException e1) {
						}
					}
					
					hitURL("https://"+host+":8088/testPageA?arg=42", "oreo", null);	
							
					hitURL("https://"+host+":8088/testPageC", "peanutbutter", "payload");

					hitURL("https://"+host+":8088/shutdown?key=shutdown", null, null);	
										
			   }).start();
		}

		private boolean hitURL(String urlString, String cookie, String payload) {
			try {
				URL url = new URL(urlString);				
				HttpURLConnection http = (HttpURLConnection)url.openConnection();
				
				if (null!=cookie) {
					http.setRequestProperty("Cookie", cookie);
				}
				
				if (null!=payload) {
					
					http.setRequestMethod("POST");
					http.setDoOutput(true);
					OutputStream out = http.getOutputStream();
					//System.out.println("writing the payload: "+payload);
					out.write(payload.getBytes());
					out.close();
					
				}
				
				
				
				http.setReadTimeout(timeoutMS);
				assertEquals(200, http.getResponseCode());			
				
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


	 
	 
}
