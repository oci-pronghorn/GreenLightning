package com.ociweb.gl.benchmark;
import static org.junit.Assert.assertTrue;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.Appendables;


public class WebTest {
	
	final static boolean useTLS = false;
	final static int timeoutMS = 240_000;
	final static int totalCalls = 100_000;
	
	static GreenRuntime runtime;
	
	static int port = 8080;
	static String host = "127.0.0.1";
	
	
	static int telemetryPort = 8097;
	static boolean telemetry = false;
	
	@BeforeClass
	public static void startServer() {
		GraphManager.showThreadIdOnTelemetry = true;
		ClientSocketReaderStage.abandonSlowConnections = false;//allow tester to wait for responses.
				
		runtime = GreenRuntime.run(new FrameworkTest());
		
	}
		
	@AfterClass
	public static void stopServer() {
		runtime.shutdownRuntime();	
		runtime = null;
	}
		
	@Test
	public void plaintext1024Test() {
				
				int inFlightBits = 9; //512 * 2 tracks is 1024
				int tracks = 2;
				int callsPerTrack = totalCalls/tracks; 
				boolean testTelemetry = false;
		
				StringBuilder uploadConsoleCapture = new StringBuilder();
				LoadTester.runClient(
						null,
						(i,r) -> r.statusCode()==200 , 
						"/plaintext", 
						useTLS, testTelemetry, 
						tracks, callsPerTrack, 
						host, port, timeoutMS, inFlightBits,
						MsgRuntime.getGraphManager(runtime),						
						Appendables.join(uploadConsoleCapture,System.out));	
				
				assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(callsPerTrack*tracks))>=0);
				
	}

	@Test
	public void plaintext4096Test() {
		
				int inFlightBits = 10; //1024 * 4 tracks is 4096
				int tracks = 4;
				int callsPerTrack = totalCalls/tracks; 
				boolean testTelemetry = false;
		
				StringBuilder uploadConsoleCapture = new StringBuilder();
				LoadTester.runClient(
						null,
						(i,r) -> r.statusCode()==200 , 
						"/plaintext", 
						useTLS, testTelemetry, 
						tracks, callsPerTrack, 
						host, port, timeoutMS, inFlightBits,
						MsgRuntime.getGraphManager(runtime),						
						Appendables.join(uploadConsoleCapture,System.out));	
				
				assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(callsPerTrack*tracks))>=0);
				
	}

	
	@Ignore //reduce memory on build server
	public void plaintext16KTest() {
				int inFlightBits = 9; //64 * 256 tracks is 16K
				int tracks = 32;
				int callsPerTrack = totalCalls/tracks; 
				boolean testTelemetry = false;
		
				StringBuilder uploadConsoleCapture = new StringBuilder();
				LoadTester.runClient(
						null,
						(i,r) -> r.statusCode()==200, 
						"/plaintext", 
						useTLS, testTelemetry, 
						tracks, callsPerTrack, 
						host, port, timeoutMS, inFlightBits,
						MsgRuntime.getGraphManager(runtime),						
						Appendables.join(uploadConsoleCapture,System.out));	
				
				assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(callsPerTrack*tracks))>=0);
				
	}
	
	@Test
	public void json1024Test() {
				
				int inFlightBits = 9; //512 * 2 tracks is 1024
				int tracks = 2;
				int callsPerTrack = totalCalls/tracks; 
				boolean testTelemetry = false;
		
				StringBuilder uploadConsoleCapture = new StringBuilder();
				LoadTester.runClient(
						null,
						(i,r) -> r.statusCode()==200 , 
						"/json", 
						useTLS, testTelemetry, 
						tracks, callsPerTrack, 
						host, port, timeoutMS, inFlightBits,
						MsgRuntime.getGraphManager(runtime),						
						Appendables.join(uploadConsoleCapture,System.out));	
				
				assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(callsPerTrack*tracks))>=0);
				
	}
	
	@Test
	public void json4096Test() {
				
				int inFlightBits = 10; //1024 * 4 tracks is 4096
				int tracks = 4;
				int callsPerTrack = totalCalls/tracks; 
				boolean testTelemetry = false;
		
				StringBuilder uploadConsoleCapture = new StringBuilder();
				LoadTester.runClient(
						null,
						(i,r) -> r.statusCode()==200 , 
						"/json", 
						useTLS, testTelemetry, 
						tracks, callsPerTrack, 
						host, port, timeoutMS, inFlightBits,
						MsgRuntime.getGraphManager(runtime),						
						Appendables.join(uploadConsoleCapture,System.out));	
				
				assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(callsPerTrack*tracks))>=0);
				
	}
	
	
	@Ignore //reduce memory on build server
	public void json16kTest() {
				
				int inFlightBits = 9;// 16K
				int tracks = 32;
				int callsPerTrack = totalCalls/tracks; 
				boolean testTelemetry = false;
		
				StringBuilder uploadConsoleCapture = new StringBuilder();
				LoadTester.runClient(
						null,
						(i,r) -> r.statusCode()==200 , 
						"/json", 
						useTLS, testTelemetry, 
						tracks, callsPerTrack, 
						host, port, timeoutMS, inFlightBits,
						MsgRuntime.getGraphManager(runtime),						
						Appendables.join(uploadConsoleCapture,System.out));	
				
				assertTrue(uploadConsoleCapture.toString(), uploadConsoleCapture.indexOf("Responses invalid: 0 out of "+(callsPerTrack*tracks))>=0);
				
	}
}
