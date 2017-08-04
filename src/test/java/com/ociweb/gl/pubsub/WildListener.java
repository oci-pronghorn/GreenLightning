package com.ociweb.gl.pubsub;

import com.ociweb.gl.api.PubSubListener;
import com.ociweb.pronghorn.pipe.BlobReader;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;

public class WildListener implements PubSubListener {
	
	private final AppendableProxy target;
	
	public WildListener(Appendable target) {
		this.target = Appendables.proxy(target);
	}

	@Override
	public boolean message(CharSequence topic, BlobReader payload) {		
		target.append("Received Topic: ").append(topic).append("\n");
		return true;
	}

}
