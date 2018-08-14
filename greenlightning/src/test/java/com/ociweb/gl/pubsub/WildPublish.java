package com.ociweb.gl.pubsub;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.WaitFor;

public class WildPublish implements StartupListener {

	private final PubSubService cmd;

	WildPublish(GreenRuntime runtime) {
		cmd = runtime.newCommandChannel().newPubSubService();
	}

	@Override
	public void startup() {
		cmd.publishTopic("nomatch",WaitFor.None);
		cmd.publishTopic("root/green/color",WaitFor.None);
		cmd.publishTopic("root/green/frequency",WaitFor.None);
		cmd.publishTopic("root/red/frequency",WaitFor.None);
		cmd.publishTopic("root/shutdown",WaitFor.None);
	}
}
