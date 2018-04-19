package com.ociweb.gl.example.parallel;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;
import com.ociweb.gl.test.ParallelClientLoadTesterConfig;
import com.ociweb.gl.test.ParallelClientLoadTesterPayload;
import com.ociweb.pronghorn.network.ServerSocketReaderStage;
import com.ociweb.pronghorn.network.http.HTTP1xResponseParserStage;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStage;

public class NamedMessagePassingTest {
	///////////-XX:+UseLargePages 
	//          -verbose:gc -Xloggc:gc.log -XX:+PrintGCTimeStamps -XX:+PrintGCDetails
	
	// -XX:MaxGCPauseMillis=5 -XX:+UseG1GC
	// -XX:MaxDirectMemorySize=256m

	@Test
	public void runTest() {

		//    cpupower frequency-info
		//    sudo cpupower frequency-set -g performance
		//    sudo x86_energy_perf_policy performance
		//   
        //monitor with
		//    sudo turbostat --debug -P
        //    sudo perf stat -a
		
		//open this location and write zero then hold it open value?
		//try this  https://access.redhat.com/articles/65410
		
		//ScriptedNonThreadScheduler.debugStageOrder = System.out;
		
		//ClientSocketWriterStage.logLatencyData = true; //for the group of connections used.
		//ClientConnection.logLatencyData = true; //every individual connection
		//ServerSocketReaderStage.showRequests = true;
		//HTTP1xResponseParserStage.showData = true;
		//HTTP1xRouterStage.showHeader=true;
		
		boolean telemetry = true;
		long cycleRate = 10000;
		
		
		GreenRuntime.run(new NamedMessagePassingApp(telemetry,cycleRate));
		
		ParallelClientLoadTesterPayload payload 
				= new ParallelClientLoadTesterPayload("{\"key1\":\"value\",\"key2\":123}");
		
		
		//spikes are less frequent when the wifi network is off...
		
		//10*   8min
		//50*  50min		
		int cyclesPerTrack =  2000;///(1+99_9999) / 10;
		
		ParallelClientLoadTesterConfig config2 = 
				new ParallelClientLoadTesterConfig(1, cyclesPerTrack, 8080, "/test", telemetry);
		
		//TODO: the pipes between private topics may not be large enough for this...
		config2.simultaneousRequestsPerTrackBits  = 1;//7;  //7 126k for max volume
		
		//TODO: chunked with simlutanious requests is broken, client side issue
		//      HTTP1xResponseParserStage needs research for multiple post responses.
		
		//Cases using pipe listener:
		// OrderSuper - was scanning could hold head values
		// Reactor    - was scanning could hold head values
		// Scheduler  - was scanning list was too long to check.
		
		
		//For low latency
		config2.cycleRate = cycleRate; //TODO: may need to be bigger for slow windows boxes.
			
		//TODO: ensure we have enough volume to make the optimization...
		
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(config2, payload),
				20_000_000);
		
		//average resources per page is about 100
		//for 100 calls we expect the slowest to be 100 micros
		//to load this page 100 times we expect to find 1 load of 2 ms
		//to load this pate 1000 times all times are under 40 which is human perception

		
	}
}
