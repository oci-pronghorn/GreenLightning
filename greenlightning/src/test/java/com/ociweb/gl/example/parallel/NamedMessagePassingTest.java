package com.ociweb.gl.example.parallel;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;
import com.ociweb.gl.test.ParallelClientLoadTesterConfig;
import com.ociweb.gl.test.ParallelClientLoadTesterPayload;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.ScriptedNonThreadScheduler;

public class NamedMessagePassingTest {
	
	///////////-XX:+UseLargePages 
	//          -verbose:gc -Xloggc:gc.log -XX:+PrintGCTimeStamps -XX:+PrintGCDetails
	
	// -XX:MaxGCPauseMillis=5 -XX:+UseG1GC
	// -XX:MaxDirectMemorySize=256m

//	@Test
//	public void lowCPUUsage() {
//		
//		//run server
//		
//		boolean telemetry = true;  //must not be true when checked in.
//		long cycleRate = 10_000;
//		
//		//cpu usage
//		//default network size..
//		
//		GreenRuntime.run(new NamedMessagePassingApp(telemetry,cycleRate));
//		
//		try {
//			Thread.sleep(200_000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
//		
//		//call cpu monior ...
//		
//	}
	
	@Test
	public void runTest() {

		//ServerSocketWriterStage.showWrites = true;
		
		//ClientSocketWriterStage.logLatencyData = true; //for the group of connections used.
		//ClientConnection.logLatencyData = true; //every individual connection
		//ServerSocketReaderStage.showRequests = true;
		//HTTP1xResponseParserStage.showData = true;
		//HTTP1xRouterStage.showHeader=true;
		
//		-XX:+UnlockCommercialFeatures 
//		-XX:+FlightRecorder
//		-XX:CMSInitiatingOccupancyFraction=98  //fixed 99
//		-XX:+UseCMSInitiatingOccupancyOnly
//		-XX:+UseThreadPriorities 
//		-XX:+UseNUMA
//		-XX:+AlwaysPreTouch
//		-XX:+UseConcMarkSweepGC 
//		-XX:+CMSParallelRemarkEnabled 
//		-XX:+ParallelRefProcEnabled
//		-XX:+UnlockDiagnosticVMOptions
//		-XX:ParGCCardsPerStrideChunk=32768  //fixed the 99.9 ??
				
		
		//GraphManager.showThreadIdOnTelemetry = true;
		//GraphManager.showScheduledRateOnTelemetry = true;
		
		boolean telemetry = true;  //must not be true when checked in.
		long cycleRate = 6_000; //larger rate should be used with greater volume..

		//note only 4 threads in use and this should probably be 3
		//ScriptedNonThreadScheduler.debugStageOrder = System.out;
		//if we want more volume we should use more threads this can be 5x greater..
		
		int serverTracks = 4;
		GreenRuntime.run(new NamedMessagePassingApp(telemetry,cycleRate,serverTracks));
		
		ParallelClientLoadTesterPayload payload = new ParallelClientLoadTesterPayload("{\"key1\":\"value\",\"key2\":123}");

		//spikes are less frequent when the wifi network is off
		int cyclesPerTrack = 4_000; //*(1+99_9999);
		int parallelTracks = 1;

		
		ParallelClientLoadTesterConfig config2 = new ParallelClientLoadTesterConfig(parallelTracks, cyclesPerTrack, 8081, "/test", telemetry);
		assertTrue(0==config2.durationNanos);
		
		config2.simultaneousRequestsPerTrackBits  = 0;//10; // 126k for max volume

		GreenRuntime.testConcurrentUntilShutdownRequested(
															new ParallelClientLoadTester(config2, payload),
															5*60*60_000); //5 hours
		
		//average resources per page is about 100
		//for 100 calls we expect the slowest to be 100 micros
		//to load this page 100 times we expect to find 1 load of 2 ms
		//to load this pate 1000 times all times are under 40 which is human perception

		
	}
}
