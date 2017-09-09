package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.oe.greenlightning.api.StateMachine.StopLight;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class TimingBehavior implements TimeListener {

	private static final long fullCycle = 20; //from one red light to the next in iterations
    
	private final GreenCommandChannel channel;
	private final AppendableProxy console;
	private final GreenRuntime runtime;

	public TimingBehavior(GreenRuntime runtime, AppendableProxy console) {
		this.channel = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.console = console;
		this.runtime = runtime;
	}

	@Override
	public void timeEvent(long time, int iteration) {

		if(iteration%fullCycle == 0) {
			changeState(time, StopLight.Go);
		}
		else if(iteration%fullCycle == 8) {
			changeState(time, StopLight.Caution);
		}
		else if(iteration%fullCycle == 11) {
			changeState(time, StopLight.Stop);
		}
		
		if (iteration == (fullCycle*3)) {
			runtime.shutdownRuntime(7);
		}

	}

	private void changeState(long time, StopLight target) {
		if (channel.changeStateTo(target)) {
			console.append(target.getColor()).append(" ");
			Appendables.appendEpochTime(console, time).append('\n');
		} else {
			console.append("unable to send state change, to busy");
		}
	}

}
