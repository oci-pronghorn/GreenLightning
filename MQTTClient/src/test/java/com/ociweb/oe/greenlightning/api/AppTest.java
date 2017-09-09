package com.ociweb.oe.greenlightning.api;

import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	//cloud bees has no MQTT server to talk to.
	 @Ignore
	    public void testApp()
	    {
		    GreenRuntime runtime = GreenRuntime.test(new MQTTClient());	    	
	    	NonThreadScheduler scheduler = (NonThreadScheduler)runtime.getScheduler();    	

	    	scheduler.startup();
	    	
	    	int iterations = 1;
			while (--iterations >= 0) {
				    		
					scheduler.run();
					
					//test application here
					
			}
			
			scheduler.shutdown();
			
	    }
}
