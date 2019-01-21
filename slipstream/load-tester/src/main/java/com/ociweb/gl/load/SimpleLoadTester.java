package com.ociweb.gl.load;

import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;

public class SimpleLoadTester {

	public static void main(String[] args) {
	
		
		LoadTester.cycleRate = 400_000L;
		//ClientSocketReaderStage.showResponse = true;
		
		//ClientSocketReaderStage.showResponse = true;
		
		//GraphManager.showThreadIdOnTelemetry = true;
		ClientSocketReaderStage.abandonSlowConnections = false;//turned off so we wait forever.
		//ClientAbandonConnectionScanner.absoluteNSToKeep = 20_000_000_000L;
		//ClientAbandonConnectionScanner.absoluteNSToAbandon = 40_000_000_000L;
		
		int timeoutMS = 2_400_000;
		
		boolean useTLS = false;

		String route = MsgRuntime.getOptArg("route", "-r", args, "/plaintext");
		String host  = MsgRuntime.getOptArg("host", "-h", args, "127.0.0.1");		
		int port = Integer.parseInt(MsgRuntime.getOptArg("host", "-p", args, "8080"));
		long totalCalls = Long.parseLong(MsgRuntime.getOptArg("calls", "-c", args, "16000000"));
		int inFlightBits = Integer.parseInt(MsgRuntime.getOptArg("inFlightBits", "-b", args, "9"));
		int tracks = Integer.parseInt(MsgRuntime.getOptArg("tracks", "-t", args, "8"));		
		boolean testTelemetry = Boolean.parseBoolean(MsgRuntime.getOptArg("monitor", "-m", args, "false"));
		
		int iterations = Integer.parseInt(MsgRuntime.getOptArg("tracks", "-i", args, "1"));		
		
		if (tracks>=128) {
		    //reduce memory usage and slow down the system.
			ClientCoordinator.setTargetPipesPerResponseParser(tracks);
		}
		//////////////
		//run
		//////////////
		long callsPerTrack = totalCalls/tracks; 

		for(int it=0; it<iterations; it++) {
			System.out.println("-------- "+(it+1)+" out of "+iterations+" -------------");
			LoadTester.runClient(
					null,
					(i,r) -> r.statusCode()==200 , 
					route, 
					useTLS, testTelemetry, 
					tracks, (int)callsPerTrack, //TODO: pass long is for calls per track..
					host, port, timeoutMS, inFlightBits,
					null,						
					System.out);	
		}
		
	}
	
}
