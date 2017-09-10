package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.pronghorn.util.AppendableProxy;

public class KickoffBehavior implements StartupListener{

	final GreenCommandChannel channel0;
	final AppendableProxy target;
	
	public KickoffBehavior(GreenRuntime runtime, Appendable target) {
		
		this.channel0 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		this.target = new AppendableProxy(target);
	
	}

	@Override
	public void startup() {
		
		target.append("Your lucky numbers are ...\n");

		channel0.publishTopic("Next");
	}

}
