package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.api.GreenRuntime;

public class secondTimeBehavior implements TimeListener {
	
	private static final long timeInterval = 60_000; //Time in milliseconds
	private static long startTime;
	private static boolean haveStartTime = false;
	
	public secondTimeBehavior(GreenRuntime runtime) {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void timeEvent(long time, int iteration) {
		// TODO Auto-generated method stub
		if((time-startTime)%timeInterval == 0){
    		System.out.println("clock");
    	}
    	if(!haveStartTime){
    		startTime = time;
    		haveStartTime = true;
    	}
	}

}
