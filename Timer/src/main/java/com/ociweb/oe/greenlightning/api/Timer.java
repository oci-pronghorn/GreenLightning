package com.ociweb.oe.greenlightning.api;


//import static com.ociweb.iot.grove.GroveTwig.*;

import com.ociweb.gl.api.*;

public class Timer implements GreenApp
{
	

    @Override
    public void declareConfiguration(Builder c) {
    	c.setTimerPulseRate(1); //the rate at which time is checked in milliseconds
        
    }

    @Override
    public void declareBehavior(GreenRuntime runtime) {
    	
    	runtime.addTimePulseListener(new firstTimeBehavior(runtime));
    	//runtime.addTimeListener(new secondTimeBehavior(runtime));
    	
    }
}
