package com.ociweb.gl.pubsub;

import com.ociweb.gl.api.GreenRuntime;
import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PubSubTest {
	@Ignore
	public void wildcardTest() {
		StringBuilder collectedRoot = new StringBuilder();
		StringBuilder collectedGreen = new StringBuilder();

		boolean completed = GreenRuntime.testUntilShutdownRequested(new WildExample(collectedRoot,collectedGreen),100);

		System.err.println("Root:\n" + collectedRoot);
		System.out.println("Green:\n" + collectedGreen);

		assertEquals("root/green/color\nroot/green/frequency\nroot/red/frequency\nroot/green/frequency\nroot/shutdown\n", collectedRoot.toString());
		assertEquals("root/green/color\nroot/green/frequency", collectedGreen.toString());
		assertTrue(completed);
	}
}
