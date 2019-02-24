package com.mydomain.greenlightning.slipstream;

import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.network.ClientAbandonConnectionScanner;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;
import com.ociweb.pronghorn.util.AppendableBuilder;
import com.ociweb.pronghorn.util.Appendables;

public class WebMicroServiceTest {
	
	static GreenRuntime runtime;
	
	static int port = (int) (3000 + (System.nanoTime()%12000));
	
	static String host = "127.0.0.1";
	static int timeoutMS = 40_000; //40 sec
	static boolean telemetry = false;
	static boolean useTLS = false;

	
	public static void main(String[] args) {
	
		//for cloud testing we bump this up since it may be running on very slow hardware
		ClientAbandonConnectionScanner.absoluteNSToKeep =      2_000_000_000L; //2sec calls are always OK.

		GreenRuntime.run(new MyMicroservice(useTLS, port, telemetry));
	}
	
	@BeforeClass
	public static void startServer() {

		//for cloud testing we bump this up since it may be running on very slow hardware
		ClientAbandonConnectionScanner.absoluteNSToKeep =      2_000_000_000L; //2sec calls are always OK.

		runtime = GreenRuntime.run(new MyMicroservice(useTLS, port, telemetry));
		
	}
		
	@AfterClass
	public static void stopServer() {
		runtime.shutdownRuntime();	
		runtime = null;
	}

	private JSONRenderer<Product> renderer = new JSONRenderer<Product>()
			.startObject()
			.integer(   "id", o->o.id)
			.integer(   "quantity", o->o.quantity)
			.string(    "name", (o,t)->t.append(o.name))
			.bool(      "disabled", o->o.disabled)
			.endObject();
	
	@Test
	public void uploadProductsTest() {
				
		
		ClientSocketReaderStage.abandonSlowConnections = false;
		
		        boolean testTelemetry = false;
				int inFlightBits = 6;  
				int tracks = 1;
				
				int callsPerTrack = 1000 /tracks; 
		
				StringBuilder uploadConsoleCapture = new StringBuilder();
				LoadTester.runClient(
						(i,w) -> renderer.render(w, new Product((int)i%10_000)) ,
						(i,r) -> r.statusCode()==200, 
						"/update", 
						useTLS, testTelemetry, 
						tracks, callsPerTrack * 10, 
						host, port, timeoutMS, inFlightBits,
						MsgRuntime.getGraphManager(runtime),						
						Appendables.join(uploadConsoleCapture,System.out));	
				
				assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(tracks*callsPerTrack))>=0);
		
				
				//////////////////////////////////////
				//now test that we get the values back
				//////////////////////////////////////
				
				tracks = 1; //this test depends on having sequential tests
				callsPerTrack = 10;
				
				
				StringBuilder captured = new StringBuilder();
				
				LoadTester.runClient(
					 null, 
					(i,r) -> {
							AppendableBuilder target = new AppendableBuilder(1000);								
							renderer.render(target, new Product((int)i));
							
							return  (200 == r.statusCode()) &&
									(target.toString().equals(r.structured().readPayload().readUTFFully()))	;
							
						  }, 
					(i) -> "/query?id="+i,
					useTLS, false, 
					tracks, callsPerTrack, 
					host, port, timeoutMS,
					captured);		
		
				 assertTrue(captured.toString(), captured.indexOf("Responses invalid: 0 out of "+(tracks*callsPerTrack))>=0);

	}
	
}
