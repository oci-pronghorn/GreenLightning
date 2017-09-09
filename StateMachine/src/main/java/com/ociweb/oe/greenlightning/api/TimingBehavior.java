package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.oe.greenlightning.api.StateMachine.StopLight;

public class TimingBehavior implements TimeListener {
	private static long startTime;
	private static boolean haveStartTime = false;
	private static final long fullTime = 15_000; //time from one red light to the next in milliseconds
    final GreenCommandChannel channel;

	public TimingBehavior(GreenRuntime runtime) {
		channel = runtime.newCommandChannel(DYNAMIC_MESSAGING);

	}


	@Override
	public void timeEvent(long time, int iteration) {
		
		if((time-startTime)%fullTime == 5_000) {
			System.out.print("Go! ");
			channel.changeStateTo(StopLight.Go);
		}
		else if((time-startTime)%fullTime == 10_000) {
			System.out.print("Caution. ");
			channel.changeStateTo(StopLight.Caution);
		}
		else if((time-startTime)%fullTime == 0) {
			System.out.print("Stop! ");
			channel.changeStateTo(StopLight.Stop);
		}
		
		if(!haveStartTime) {
			startTime = time;
			haveStartTime = true;
		}
	}

}
