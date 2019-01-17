package com.ociweb.gl.benchmark;

import java.util.concurrent.TimeUnit;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

import io.vertx.core.VertxOptions;

public class GreenLightning {

	public static void main(String[] args) {
		
		
//		System.setProperty("vertx.options.maxEventLoopExecuteTime","10000000000"); 
//		
//		VertxOptions options = new VertxOptions();
//    	options.setBlockedThreadCheckIntervalUnit(TimeUnit.MINUTES);
//    	options.setBlockedThreadCheckInterval(20);
    	    	
		//ServerSocketReaderStage.showRequests = true;
		
		////ScriptedNonThreadScheduler.debugStageOrder = System.out;
		//TODO: we have reactors in the wrong order after the consume ordering stage.. must fix
		//TODO: the TrackHTTPResponseListener private class is the new high cpu stage for full test.
		
		//TODO: underload vert.x can shut down server, limited to 14 bits?
		//   
		//     Jan 10, 2019 1:47:45 PM io.vertx.core.impl.BlockedThreadChecker
		//     WARNING: Thread Thread[vert.x-eventloop-thread-0,5,main] has been blocked for 2247 ms, time limit is 2000 ms
		
		//TODO: must check factors for pipe splits...
		
		//PipeConfig.showConfigsCreatedLargerThan = 1<<23;
		GraphManager.showThreadIdOnTelemetry = true;		
		GraphManager.showScheduledRateOnTelemetry = true;
		GraphManager.showMessageCountRangeOnTelemetry = true;
		
		//System.setProperty("pronghorn.processors", "28"); //simulate the techempower testing box
		
		//test client is the same old 22 version so issue MUST be
		//in socket reader OR the http parser.
		//with 12 we ran normal 84 and found issues but these are NOT shared connections..
		//System.setProperty("pronghorn.processors", "12"); //with 6-28 we have issues...
		
		//TODO: update for 5 way MUST ensure no multiple of 5 logic is fixed...
		
		//reduce pipes for less memory used by test to reach 16K test..
		//TODO: block other 5 values..
		System.setProperty("pronghorn.processors", "3"); //set lower since we do testing here... //6 , 8,  12,  16
		                                                  
		GreenRuntime.run(new FrameworkTest(),args);
	
	}
	
}
