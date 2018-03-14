package com.ociweb.gl.example.parallel;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;
import com.ociweb.gl.test.ParallelClientLoadTesterConfig;
import com.ociweb.gl.test.ParallelClientLoadTesterPayload;

public class NamedMessagePassingTest {

	@Test
	public void runTest() {

		//order supervisor blocks because it must have 1 incoming pipe for each
		//of the N unit pipes coming in or data can be hidden !!!
		
		//need more threads for volume??
		
		boolean telemetry = true;
		
		GreenRuntime.run(new NamedMessagePassingApp(telemetry));
		
		ParallelClientLoadTesterPayload payload 
				= new ParallelClientLoadTesterPayload("{\"key1\":\"value\",\"key2\":123}");
		
		ParallelClientLoadTesterConfig config1 = 
				new ParallelClientLoadTesterConfig(2, 1200, 8080, "/test", false);
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(config1, null),
				200_000);

		ParallelClientLoadTesterConfig config2 = 
				new ParallelClientLoadTesterConfig(8, 60_000, 8080, "/test", telemetry);
		
		config2.simultaneousRequestsPerTrackBits  = 14;
		config2.responseTimeoutNS = 10_000_000_000L;
		
		config2.rate = 4_000L;
		config2.ensureLowLatency = false;
		
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(config2, null),
				200_000);
	}
}
