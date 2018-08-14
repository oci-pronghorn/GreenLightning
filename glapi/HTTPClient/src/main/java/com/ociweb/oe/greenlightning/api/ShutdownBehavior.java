package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class ShutdownBehavior implements PubSubListener {

	private final GreenRuntime runtime;
	public ShutdownBehavior(GreenRuntime runtime) {
		this.runtime = runtime;
	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		runtime.shutdownRuntime();
		
		return true;
	}

}
