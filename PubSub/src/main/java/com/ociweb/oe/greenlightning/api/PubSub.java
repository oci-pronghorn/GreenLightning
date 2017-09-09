package com.ociweb.oe.greenlightning.api;

import com.ociweb.gl.api.*;


public class PubSub implements GreenApp
{
	private final Appendable target;
	private final int seed;
	
	public PubSub(Appendable target, int seed) {
		this.target = target;
		this.seed = seed;
	}
	
    @Override
    public void declareConfiguration(Builder c) {
        //no connections are needed
    }


    @Override
    public void declareBehavior(GreenRuntime runtime) {

    	runtime.addStartupListener(new KickoffBehavior(runtime, target));
    	runtime.addPubSubListener(new GenerateBehavior(runtime, "Count", target, seed)).addSubscription("Next");
    	runtime.addPubSubListener(new CountBehavior(runtime, "Next")).addSubscription("Count");
    	
    	
    }
          
}
