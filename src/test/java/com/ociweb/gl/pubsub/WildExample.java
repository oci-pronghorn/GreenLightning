package com.ociweb.gl.pubsub;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;

public class WildExample implements GreenApp {
	private final Appendable collectedRoot;
	private final Appendable collectedGreen;
	
	WildExample(Appendable collectedRoot, Appendable collectedGreen) {
		this.collectedRoot = collectedRoot;
		this.collectedGreen = collectedGreen;
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
		runtime.addPubSubListener(new WildListener(collectedGreen, runtime)).addSubscription("root/green/#");
		runtime.addPubSubListener(new WildListener(collectedRoot, runtime)).addSubscription("root/#");
		runtime.addStartupListener(new WildPublish(runtime));
	}
}
