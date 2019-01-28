package com.ociweb.gl.example.parallel;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.LoadTester;
import com.ociweb.gl.test.ParallelClientLoadTester;
import com.ociweb.gl.test.ParallelClientLoadTesterConfig;
import com.ociweb.gl.test.ParallelClientLoadTesterPayload;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;
import com.ociweb.pronghorn.network.ServerSocketReaderStage;
import com.ociweb.pronghorn.network.ServerSocketWriterStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class NamedMessagePassingTest {
	
	//TODO: urgent fix why is this latency larger than the old 20micros.	
	@Test
	public void runTest() {
		
		int port = (int) (2000 + (System.nanoTime()%12000));
		
		ClientSocketReaderStage.abandonSlowConnections = false;
		GraphManager.showThreadIdOnTelemetry = true;
		GraphManager.showScheduledRateOnTelemetry = true;
		
		ServerSocketWriterStage.hardLimtNS = 2_000;
		
		boolean telemetry = false;
		//ust not be true when checked in.
		long cycleRate = 6_000; //larger rate should be used with greater volume..

		//note only 4 threads in use and this should probably be 3
		//ScriptedNonThreadScheduler.debugStageOrder = System.out;
		//if we want more volume we should use more threads this can be 5x greater..
		
		int serverTracks = 2;
		GreenRuntime.run(new NamedMessagePassingApp(telemetry,cycleRate,serverTracks,port));
		
		
		ParallelClientLoadTesterPayload payload = new ParallelClientLoadTesterPayload("{\"key1\":\"value\",\"key2\":123}");

		//spikes are less frequent when the wifi network is off
		int cyclesPerTrack = 1<<15;
		int parallelTracks = 2; 

		
		ParallelClientLoadTesterConfig config2 = new ParallelClientLoadTesterConfig(
				             parallelTracks, cyclesPerTrack, port, "/test", telemetry);
		
		assertTrue(0==config2.durationNanos);
		
		config2.simultaneousRequestsPerTrackBits  = 0;
		
	    	    
		GreenRuntime.testConcurrentUntilShutdownRequested(
											new ParallelClientLoadTester(config2, payload),
											5*60*60_000); //5 hours
		
		//average resources per page is about 100
		//for 100 calls we expect the slowest to be 100 micros
		//to load this page 100 times we expect to find 1 load of 2 ms
		//to load this pate 1000 times all times are under 40 which is human perception

		
	}
}
