package com.ociweb.gl.pubsub;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;

public class WildExample implements GreenApp {

	Appendable collectedA;
	Appendable collectedB;
	
	public WildExample(Appendable a, Appendable b) {
		this.collectedA = a;
		this.collectedB = b;
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		
	}

	@Override
	public void declareBehavior(GreenRuntime runtime) {
		//TODO: we have a problem inserting in the reverse order, must fix.
		runtime.addPubSubListener(new WildListener(collectedB)).addSubscription("root/green/#");
		runtime.addPubSubListener(new WildListener(collectedA)).addSubscription("root/#");
		runtime.addStartupListener(new WildPublish(runtime));
	}

}
