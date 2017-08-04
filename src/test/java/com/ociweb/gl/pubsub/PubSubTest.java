package com.ociweb.gl.pubsub;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;

public class PubSubTest {

	
	@Test
	public void somethingTest() {
		
			StringBuilder a = new StringBuilder();
			StringBuilder b = new StringBuilder();
						
		    GreenRuntime runtime = GreenRuntime.test(new WildExample(a,b));	    	
	    	NonThreadScheduler scheduler = (NonThreadScheduler)runtime.getScheduler();    	

	    	scheduler.startup();
	    	
	    	int iterations = 100;
			while (--iterations >= 0) {
				    		
					scheduler.run();
					
			}
			
			scheduler.shutdown();
			
			//add asserts here
			//System.err.println("A:\n"+a);
			//System.out.println("B:\n"+b);
			
		
	}
	
	
}
