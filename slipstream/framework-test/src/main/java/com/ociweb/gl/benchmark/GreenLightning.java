package com.ociweb.gl.benchmark;

import com.ociweb.gl.api.GreenRuntime;

public class GreenLightning {

	public static void main(String[] args) {
		
		//ServerSocketReaderStage.showRequests = true;
		
		//PipeConfig.showConfigsCreatedLargerThan = 1<<23;
	//	GraphManager.showThreadIdOnTelemetry = true;
		//set to 15G? or have fewerpipes?
	//	System.setProperty("pronghorn.processors", "28"); //simulate the techempower testing box
	//	System.setProperty("pronghorn.processors", "8"); //set lower since we do testing here... //6 , 8,  12,  16
		                                                  
		GreenRuntime.run(new FrameworkTest(),args);
	
	}
	
}
