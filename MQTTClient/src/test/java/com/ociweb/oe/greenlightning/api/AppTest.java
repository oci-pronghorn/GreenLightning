package com.ociweb.oe.greenlightning.api;

import static org.junit.Assert.assertFalse;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	//cloud bees has no MQTT server to talk to.
	@Test
	public void testApp()
	{
		long timeoutMS = 5_000;
		boolean exitWithoutTimeout = GreenRuntime.testConcurrentUntilShutdownRequested(new MQTTClient(), timeoutMS);
		assertFalse(exitWithoutTimeout);
	}

}
