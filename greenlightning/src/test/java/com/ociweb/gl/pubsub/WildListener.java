package com.ociweb.gl.pubsub;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class WildListener implements PubSubListener {
	private final AppendableProxy target;
	private final GreenRuntime runtime;

	WildListener(Appendable target, GreenRuntime runtime) {
		this.target = Appendables.proxy(target);
		this.runtime = runtime;
	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {		
		target.append(topic).append("\n");
		if (topic.toString().endsWith("shutdown")) {
			runtime.shutdownRuntime();
		}
		return true;
	}
}
