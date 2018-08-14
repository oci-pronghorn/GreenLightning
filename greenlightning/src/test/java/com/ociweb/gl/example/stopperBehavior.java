package com.ociweb.gl.example;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.TimeListener;

public class stopperBehavior implements TimeListener {
	
	private final GreenRuntime runtime;
	
	public stopperBehavior(GreenRuntime runtime) {
		this.runtime = runtime;
	}

	@Override
	public void timeEvent(long time, int iteration) {
		if (iteration==60) {
			runtime.shutdownRuntime();
		}
	}

}
