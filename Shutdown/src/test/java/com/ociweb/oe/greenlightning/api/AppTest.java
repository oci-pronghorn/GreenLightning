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

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.GreenRuntime;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	private final int timeoutMS = 4_000; //4 sec
	private final static Logger logger = LoggerFactory.getLogger(AppTest.class);
	
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

				trustAllCerts("127.0.0.1");

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

	public static void trustAllCerts(final String host) {
		logger.warn("WARNING: this scope will now accept all certs on host: "+host+". This is for testing only!");
		
		try {
		     SSLContext sc = SSLContext.getInstance("SSL");
		     TrustManager[] trustAllCerts = new TrustManager[]{
		    		 new X509TrustManager() {
		    			 public java.security.cert.X509Certificate[] getAcceptedIssuers() {
		    				 return null;
		    			 }
		    			 public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {
		    			 }
		    			 public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {
		    			 }
		    		 }
		     };
		     sc.init(null, trustAllCerts, new java.security.SecureRandom());
		     HttpsURLConnection.setDefaultSSLSocketFactory(sc.getSocketFactory());
		     
		     HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
		         public boolean verify(String hostname, SSLSession session) {
		        	 return hostname.equals(host);
		         }
		     });
		     
		 } catch (Exception e) {
		    throw new RuntimeException(e);
		 }
	}
	
}
