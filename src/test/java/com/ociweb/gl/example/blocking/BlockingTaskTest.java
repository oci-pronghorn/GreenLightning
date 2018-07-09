package com.ociweb.gl.example.blocking;

import org.junit.*;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;
import com.ociweb.gl.test.ParallelClientLoadTesterConfig;
import com.ociweb.gl.test.ParallelClientLoadTesterPayload;
import com.ociweb.pronghorn.network.ClientSocketWriterStage;

public class BlockingTaskTest {
	
	
	@Ignore
	public void runIgnore() {
		
		//ClientSocketWriterStage.showWrites = true;
		//ServerSocketReaderStage.showRequests = true;
		//HTTP1xRouterStage.showHeader = true;
		
		boolean telemetry = true;  //must not be true when checked in.
		int cyclesPerTrack = 100;
		int parallelTracks = 4;
		int timeoutMS = 600_000;
				
		ClientSocketWriterStage.showWrites = false;
		
		GreenRuntime.run(new BlockingExampleApp(telemetry));
		
		ParallelClientLoadTesterPayload payload 
				= new ParallelClientLoadTesterPayload(
						"{\"key1\":\"value\",\"key2\":123}");
		
		
		ParallelClientLoadTesterConfig config2 = 
				new ParallelClientLoadTesterConfig(parallelTracks, 
						cyclesPerTrack, 8083, "/test", telemetry);
		
		config2.simultaneousRequestsPerTrackBits  = 0;
		config2.telemetryHost="127.0.0.1";
		
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(config2, payload),
				timeoutMS);

		//wait for telemetry to update
//		try {
//			Thread.sleep(2_000);
//		} catch (InterruptedException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
		
		//TODO: assert some things about these results.
	}
	
	
}
