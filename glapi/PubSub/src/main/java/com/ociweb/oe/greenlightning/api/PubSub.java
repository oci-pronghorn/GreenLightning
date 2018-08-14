package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.util.AppendableProxy;
import com.ociweb.pronghorn.util.Appendables;


public class PubSub implements GreenApp
{
	private final AppendableProxy target;
	private final int seed;
	
	public PubSub(Appendable target, int seed) {
		this.target = Appendables.proxy(target);
		this.seed = seed;
	}
	
    @Override
    public void declareConfiguration(Builder c) {
        //no connections are needed
    	c.enableTelemetry();
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {

    	runtime.addStartupListener(new KickoffBehavior(runtime, target));
    	
    	runtime.addPubSubListener(new GenerateBehavior(runtime, "Count", target, seed))
    	                     .addSubscription("Next");
    	
    	CountBehavior counter = new CountBehavior(runtime, "Next");
		runtime.registerListener(counter)
		           			.addSubscription("Count", counter::triggerNextAndCount)
		           			.addSubscription("AnExample", counter::anotherMessage);
    	
    	
    	
    }
          
}
