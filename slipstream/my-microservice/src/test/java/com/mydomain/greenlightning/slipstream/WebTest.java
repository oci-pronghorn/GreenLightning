package com.mydomain.greenlightning.slipstream;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.Writable;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.json.JSONRequired;
import com.ociweb.json.encode.JSONRenderer;
import com.ociweb.pronghorn.network.ClientAbandonConnectionScanner;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStage;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.AppendableBuilder;
import com.ociweb.pronghorn.util.Appendables;

public class WebTest {
	
	static GreenRuntime runtime;
	
	static int port = 7244;
	static String host = "127.0.0.1";
	static int timeoutMS = 1200_000; //20 minutes	
	static boolean telemetry = false;
	static boolean useTLS = true;//true;//NOTE: must be dropping valuable data somewhere??

	
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
				
				int inFlightBits = 0;
				int tracks = 8;
				int callsPerTrack = 10000/tracks; 
		
				StringBuilder uploadConsoleCapture = new StringBuilder();
		    	LoadTester.runClient(
						(i,w) -> renderer.render(w, new Product((int)i)) ,
						(i,r) -> r.statusCode()==200 , 
						"/update", 
						useTLS, false, 
						tracks, callsPerTrack, 
						host, port, timeoutMS, inFlightBits,
						MsgRuntime.getGraphManager(runtime),						
						Appendables.join(uploadConsoleCapture,System.out));	
				
				assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(tracks*callsPerTrack))>=0);
				//System.out.println("iteration "+iter+" of "+totalIterations);
				//System.out.println(uploadConsoleCapture);
				
				
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

	@Test
	public void uploadProductsLoadTest() {

		Product[] prodCache = new Product[300];
		
		StringBuilder uploadConsoleCapture = new StringBuilder();
		GraphManager serverGM = null==runtime? null :MsgRuntime.getGraphManager(runtime);
		final AppendableBuilder target = new AppendableBuilder(1000);
		 
		
		int maxBits = 4; 
		int inFlightBits = maxBits+1;
		while (--inFlightBits>=0) {
		
			int totalIterations = 8;  //largest connections.
			int iter = totalIterations*2;
			while ((iter/=2)>=1) {
				{
		
					System.out.println("////////////////////////");
					System.out.println("Iteration "+iter+" Bits: "+inFlightBits);
					System.out.println("////////////////////////");
					
					int tracks = iter;
					int callsPerTrack = prodCache.length/tracks; 
			
					uploadConsoleCapture.setLength(0);
			    	LoadTester.runClient(
							(i,w) -> renderer.render(w, prodCache[(int)i]==null ?  prodCache[(int)i]=new Product((int)i) : prodCache[(int)i] ) ,
							(i,r) -> r.statusCode()==200 , 
							"/update", 
							useTLS, false, 
							tracks, callsPerTrack, 
							host, port, timeoutMS, 
							inFlightBits, Appendables.join(uploadConsoleCapture,System.out));	
					
					assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(tracks*callsPerTrack))>=0);
		
					//////////////////////////////////////
					//now test that we get the values back
					//////////////////////////////////////
					
					tracks = 1; //this test depends on having sequential tests
					callsPerTrack = 1;
					
					uploadConsoleCapture.setLength(0);
					
					LoadTester.runClient(
						 null, 
						(i,r) -> {
								target.clear();
								renderer.render(target, prodCache[(int)i]==null ?  prodCache[(int)i]=new Product((int)i) : prodCache[(int)i]);						
								return  (200 == r.statusCode()) &&
										target.isEqual(r.structured().readPayload()); //equals is done without creating any String object
								
							  }, 
						(i) -> "/query?id="+i,
						useTLS, false, 
						tracks, callsPerTrack, 
						host, port, timeoutMS,
						uploadConsoleCapture);
			
					 assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(tracks*callsPerTrack))>=0);		
					
					 if (null!=this.runtime) {
						 assertTrue("At the end of each iteration no pipes should remain locked.", this.runtime.validateNoPipeLocksHeld());
						 //NOTE: confirm that all the known pipes from the server are empty
						 Pipe<?>[] allPipes = GraphManager.allPipes(serverGM);
						 int a = allPipes.length;
						 while (--a >= 0) {
							 Pipe p = allPipes[a];
							 if (null != p) {	 
								 PronghornStage stage = GraphManager.getRingProducer(serverGM, p.id);
								 if (!GraphManager.hasNota(serverGM, stage.stageId, GraphManager.MONITOR)) {
									 assertTrue(p.toString(),Pipe.isEmpty(p));
								 };
							 }						 
						 }
					 }
					 				
				}

				 
			}
		}
	}
	
}
