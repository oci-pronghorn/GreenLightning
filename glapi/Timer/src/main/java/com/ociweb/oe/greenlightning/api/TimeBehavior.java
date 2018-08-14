package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.TimeListener;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.gl.api.GreenRuntime;

public class TimeBehavior implements TimeListener {
	private static final int timeInterval = 5; //iterations
    private static final int oneTimeTrigger = 20;
	
	private final AppendableProxy console;
	private final GreenRuntime runtime;
	
	public TimeBehavior(GreenRuntime runtime, AppendableProxy console) {
		this.console = console;
		this.runtime = runtime;
	}

	@Override
	public void timeEvent(long time, int iteration) {
		
		if(iteration%timeInterval == 0){
			Appendables.appendEpochTime(console, time).append('\n');
		}
		
		if (oneTimeTrigger == iteration) {
			console.append("Event Triggered\n");
			runtime.shutdownRuntime();
		}
	}

}
