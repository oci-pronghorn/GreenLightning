package com.ociweb.gl.pubsub;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.WaitFor;

public class WildPublish implements StartupListener {

	private final GreenCommandChannel cmd;

	public WildPublish(GreenRuntime runtime) {
		cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);
	}

	@Override
	public void startup() {
		cmd.publishTopic("nomatch",WaitFor.None);
		cmd.publishTopic("root/green/color",WaitFor.None);
		cmd.publishTopic("root/green/frequency",WaitFor.None);
		cmd.publishTopic("root/red/frequency",WaitFor.None);
	
	}


}
