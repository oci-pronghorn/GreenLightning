package com.ociweb.gl.example;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;

public class ServerTest {
	
    @Ignore //cloud bees is not letting us open a socket here.
    public void serverTestTLS() {
    	boolean isTLS = true;    	
    	testSimpleCallAndCookie(isTLS,9443);
    }

    @Ignore //cloud bees is not letting us open a socket here.
    public void serverTestNormal() {
    	boolean isTLS = false;    	
    	testSimpleCallAndCookie(isTLS,8098);
    }
    
	private void testSimpleCallAndCookie(boolean isTLS, int port) {
		
		SSLUtilities.trustAllHostnames();
    	SSLUtilities.trustAllHttpsCertificates();
    	
    	SimpleApp app = new SimpleApp(port, false, isTLS);
    	GreenRuntime runtime = GreenRuntime.test((GreenApp)app);
    	final NonThreadScheduler scheduler = (NonThreadScheduler)runtime.getScheduler();
    	
    	final AtomicBoolean isLive = new AtomicBoolean(true);
    	   	
    	
    	Thread thread = new Thread(new Runnable(){    		
    		public void run() {
    						scheduler.startup();
				    		while (isLive.get()) {    		
				    			scheduler.run();
				    			Thread.yield();
				    		}
    		}
    	});

    	thread.start();
    	    
    	try { //must wait just a little to make sure server is running before test.
			Thread.sleep(100);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
			fail();
		}
    	
    	try {

    		URL testCall = new URL("http"+(isTLS?"s":"")+"://127.0.0.1:"+port+"/groovyadd/2.3/2.8");
	
			URLConnection connection = testCall.openConnection();
			connection.setRequestProperty("Cookie", "Oreo");
			
			
			connection.connect();
			InputStream stream = connection.getInputStream();
			StringBuilder builder = new StringBuilder();
			int value;
			while ((value = stream.read()) != -1) {
				builder.append((char)value);
			}
			stream.close();
			
			String response = builder.toString();
			assertTrue(response, response.startsWith("{\"x\":2.3,\"y\":2.8,\"groovySum\":5.1"));
			//System.out.println(builder);
			
    	} catch (Exception e) {
    		e.printStackTrace();
			fail();
		}
    	
    	
    	
//    	try {
//
//    		URL testCall = new URL("http"+(isTLS?"s":"")+"://127.0.0.1:"+port+"/simpleadd/2/8");
//		
//			URLConnection connection = testCall.openConnection();
//			
//			connection.connect();
//			InputStream stream = connection.getInputStream();
//			StringBuilder builder = new StringBuilder();
//			int value;
//			while ((value = stream.read()) != -1) {
//				builder.append((char)value);
//			}
//			stream.close();
//			
//			String response = builder.toString();
//			assertTrue(response, response.startsWith("{\"x\":2,\"y\":8,\"groovySum\":10"));
//			//System.out.println(builder);
//			
//    	} catch (Exception e) {
//    		e.printStackTrace();
//			fail();
//		}
    	
    	isLive.set(false);
    	assertEquals("Oreo", app.getLastCookie());

    	
	}
    
}
