package com.ociweb.gl.example.parallel;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;

public class NamedMessagePassingTest {

	@Test
	public void emptyTest() {
		assertTrue(true);
	}
	
	@Ignore
	public void runTest() {
		
		GreenRuntime.run(new NamedMessagePassingApp());
		
		String payload = "{\"key1\":\"value\",\"key2\":123}";
		
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(2, 10_000, 8080, "/test", payload, false),
				200_000);

	}
	
}
