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
		
		boolean telemetry = false;
		long cycleRate = 1200;  
		
		
		GreenRuntime.run(new NamedMessagePassingApp(telemetry,cycleRate));
		
		ParallelClientLoadTesterPayload payload 
				= new ParallelClientLoadTesterPayload("{\"key1\":\"value\",\"key2\":123}");
		
		
		//spikes are less frequent when the wifi network is off...
		
//		FileOutputStream hold = null;
//		try {//this piles up work and makes it worse? try short runs?
//			File f = new File("/dev/cpu_dma_latency");
//			hold = new FileOutputStream(f);
//			hold.write(new byte[] {0,0,0,1});
//		} catch(Exception e) {
//			e.printStackTrace(System.err);
//		}
		
		//10*  8min
		//50* 40min		
		int cyclesPerTrack =  10_000;// 1+99_9999;
		
		ParallelClientLoadTesterConfig config2 = 
				new ParallelClientLoadTesterConfig(1, cyclesPerTrack, 8080, "/test", telemetry);
		
		//TODO: the pipes between private topics may not be large enough for this...
		config2.simultaneousRequestsPerTrackBits  = 0;  //4 for max volume
		config2.responseTimeoutNS = 0L;
	
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
//		Total:1000000
//		0035 µs 25 percentile
//		0046 µs 50 percentile
//		0058 µs 80 percentile
//		0063 µs 90 percentile
//		0065 µs 95 percentile
//		0100 µs 98 percentile
//		0116 µs 99 percentile
//		0175 µs 99.9 percentile
//		0002 ms 99.99 percentile
//		0013 ms 99.999 percentile
//		0021 ms 99.9999 percentile
//		0021 ms max update
		
//		if (null!=hold) {
//			System.err.println("done xxxxxxxxxxxxxxxx ");
//			
//			try {
//				hold.close();
//			} catch (IOException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//		}
		
	}
}
