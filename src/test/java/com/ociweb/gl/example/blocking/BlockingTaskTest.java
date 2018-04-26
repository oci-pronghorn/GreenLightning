package com.ociweb.gl.example.blocking;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;
import com.ociweb.gl.test.ParallelClientLoadTesterConfig;
import com.ociweb.gl.test.ParallelClientLoadTesterPayload;

public class BlockingTaskTest {

	
	
	@Test
	public void runTest() {

		boolean telemetry = false;  //must not be true when checked in.
		int cyclesPerTrack = 1000;
		int parallelTracks = 1;//2;//4;
		int timeoutMS = 600_000;
				
		GreenRuntime.run(new BlockingExampleApp(telemetry));
		
		ParallelClientLoadTesterPayload payload 
				= new ParallelClientLoadTesterPayload(
						"{\"key1\":\"value\",\"key2\":123}");
		
		
		ParallelClientLoadTesterConfig config2 = 
				new ParallelClientLoadTesterConfig(parallelTracks, 
						cyclesPerTrack, 8083, "/test", telemetry);
		
		config2.simultaneousRequestsPerTrackBits  = 0;
		
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(config2, payload),
				timeoutMS);

		//TODO: assert some things about these results.
	}
	
	
}
