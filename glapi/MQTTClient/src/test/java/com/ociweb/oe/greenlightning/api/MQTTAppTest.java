package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

/**
 * Unit test for simple App.
 */
public class MQTTAppTest { 

	//cloud bees has no MQTT server to talk to.
	@Test
	public void testApp() {
		long timeoutMS = 1_000;
		boolean exitWithoutTimeout = GreenRuntime.testConcurrentUntilShutdownRequested(new MQTTClient("egress","ingress","testClient42"), timeoutMS);
		assertFalse(exitWithoutTimeout);
	}

	
//	@Test
//	public void roundTripTest()
//	{
//		//see telemetry
//		GreenRuntime.run(new MQTTClient("a","b","testClient42a"));
//		
//		long timeoutMS = 500_000;
//		GreenRuntime.testConcurrentUntilShutdownRequested(new MQTTClient("b","a","testClient42b"), timeoutMS);
//
//	}
	
}
