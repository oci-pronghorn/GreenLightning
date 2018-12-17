package com.ociweb.gl.benchmark;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;

public class GreenLightning {

	public static void main(String[] args) {
		
		//ServerSocketReaderStage.showRequests = true;
		
		////ScriptedNonThreadScheduler.debugStageOrder = System.out;
		//TODO: we have reactors in the wrong order after the consume ordering stage.. must fix
		//TODO: the TrackHTTPResponseListener private class is the new high cpu stage for full test.
		
		
		//PipeConfig.showConfigsCreatedLargerThan = 1<<23;
		GraphManager.showThreadIdOnTelemetry = true;
		GraphManager.showScheduledRateOnTelemetry = true;
		
		//System.setProperty("pronghorn.processors", "28"); //simulate the techempower testing box
		
		//reduce pipes for less memory used by test to reach 16K test..
		System.setProperty("pronghorn.processors", "8"); //set lower since we do testing here... //6 , 8,  12,  16
		                                                  
		GreenRuntime.run(new FrameworkTest(),args);
	
	}
	
}
