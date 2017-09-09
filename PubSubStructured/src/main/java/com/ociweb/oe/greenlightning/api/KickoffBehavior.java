package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.StartupListener;

public class KickoffBehavior implements StartupListener {
	private final GreenCommandChannel cmd;
	private final CharSequence publishTopic;

	KickoffBehavior(GreenRuntime runtime, CharSequence publishTopic) {
		this.cmd = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.publishTopic = publishTopic;
	}

	@Override
	public void startup() {
		// Send the initial value on startup
		cmd.presumePublishStructuredTopic(publishTopic, writer -> {
			writer.writeUTF8(PubSubStructured.SENDER_FIELD, "from kickoff behavior");
			writer.writeLong(PubSubStructured.VALUE_FIELD, 1);
		});
	}
}
