package com.ociweb.oe.greenlightning.api;

import org.junit.Ignore;

import com.ociweb.gl.api.GreenRuntime;
import org.junit.Test;

/**
 * Unit test for simple App.
 */
public class AppTest { 

	//cloud bees has no MQTT server to talk to.
	@Test
	@Ignore
	public void testApp()
	{
		long timeoutMS = 10_000;
		GreenRuntime.testUntilShutdownRequested(new MQTTClient(), timeoutMS);
	}
}
