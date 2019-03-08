package com.ociweb;

import static org.junit.Assert.assertTrue;

import java.net.URL;

import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.LoadTester;

public class GreenLightningTest {

	@Test
	public void testStaticServer() {
	
		//for maven how to get this path?
		String path="demoSite/index.html";
		
		URL url = getClass().getResource("/NetRequest.xml");
		System.out.println(url);
		
		boolean isTLS=false;
		String bindHost="127.0.0.1";//"0.0.0.0";
		int bindPort = (int) (2000 + (System.nanoTime()%12000));
		boolean isClientAuthRequired=false;;
        String identityStoreResourceName=null;
        String keyPassword=null;
        String keyStorePassword=null;
        boolean trustAll=true;
        int telemetryPort=-1;
        
        GreenApp app = new GreenLightning.StaticFileServer(isTLS, bindHost, bindPort, path,
								                            isClientAuthRequired,
								                            identityStoreResourceName,
								                            keyPassword,
								                            keyStorePassword,
								                            trustAll,
								                            telemetryPort);
		
        GreenRuntime.run(app);
     
        hitServer(bindPort, null, 200, "/favicon.ico");
        hitServer(bindPort, "Green Lightning demo page", 200, "");
        hitServer(bindPort, "Green Lightning demo page", 200, "/");
        hitServer(bindPort, "Green Lightning demo page", 200, "/index.html");
        hitServer(bindPort, null, 404, "/notAFile.nothing");
	}

	private void hitServer(int bindPort, String expected, int status, String route) {
		StringBuilder b = new StringBuilder();
        
	 	LoadTester.runClient(null, 
	 			(i,r)-> {
	 				if (r.statusCode()==status) {
	 					if (null==expected) {
	 						return true;
	 					} else {
			 				String response = r.structured().readPayload().readUTFFully();
			 				//System.out.println(response);
			 				return response.contains(expected);
	 					}
	 				} else {
	 					System.out.println("bad status code:"+r.statusCode());
	 					return false;
	 				}
	 			},	 			
	 			route, 
	 			false, false, 
	 			1, 1, 
	 			"127.0.0.1", bindPort, 
	 			600_000_000, b);
	
	 	
	 	assertTrue(b.toString(),b.toString().contains("Responses invalid: 0"));
	}
	
}
