package com.ociweb.gl.example.parallel;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;
import com.ociweb.gl.test.ParallelClientLoadTesterConfig;
import com.ociweb.gl.test.ParallelClientLoadTesterPayload;

public class NamedMessagePassingTest {
	///////////-XX:+UseLargePages 
	//          -verbose:gc -Xloggc:gc.log -XX:+PrintGCTimeStamps -XX:+PrintGCDetails
	
	// -XX:MaxGCPauseMillis=5 -XX:+UseG1GC
	// -XX:MaxDirectMemorySize=256m

	@Test
	public void runTest() {

		
		boolean telemetry = true;
		long rate = 2000;
		
		GreenRuntime.run(new NamedMessagePassingApp(telemetry,rate));
		
		ParallelClientLoadTesterPayload payload 
				= new ParallelClientLoadTesterPayload("{\"key1\":\"value\",\"key2\":123}");
		
		ParallelClientLoadTesterConfig config1 = 
				new ParallelClientLoadTesterConfig(2, 50000, 8080, "/test", false);
		config1.rate = rate;
		config1.simultaneousRequestsPerTrackBits  = 4;
		
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(config1, null),
				600_000);

		ParallelClientLoadTesterConfig config2 = 
				new ParallelClientLoadTesterConfig(2, 100_000, 8080, "/test", telemetry);
		
		config2.simultaneousRequestsPerTrackBits  = 4;
		config2.responseTimeoutNS = 0L;
	
		//For low latency
		config2.rate = rate; //TODO: may need to be bigger for slow windows boxes.
				
		//TODO: ensure we have enough volume to make the optimization...
		
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(config2, null),
				2_000_000);
	}
}
