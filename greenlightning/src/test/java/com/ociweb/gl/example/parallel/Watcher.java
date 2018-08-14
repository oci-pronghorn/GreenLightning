package com.ociweb.gl.example.parallel;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class Watcher implements PubSubListener {

	public Watcher(GreenRuntime runtime) {
		
	}

	@Override
	public boolean message(CharSequence topic, ChannelReader payload) {
		
		System.out.println("got message topic: "+topic);
	
		return true;
	}

}
