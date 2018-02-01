package com.ociweb.gl.pubsub;

import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;

public class PubSubTest {

	
	@Ignore
	public void somethingTest() {
		
		
			StringBuilder a = new StringBuilder();
			StringBuilder b = new StringBuilder();
						
			
			GreenRuntime.testUntilShutdownRequested(new WildExample(a,b),100);

			
			//add asserts here
			//System.err.println("A:\n"+a);
			//System.out.println("B:\n"+b);
			
		
	}
	
	
}
