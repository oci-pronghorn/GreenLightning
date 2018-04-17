package com.ociweb.gl.network;

import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.test.ParallelClientLoadTester;
import com.ociweb.gl.test.ParallelClientLoadTesterConfig;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;
import com.ociweb.pronghorn.network.ClientSocketWriterStage;
import com.ociweb.pronghorn.network.ServerSocketReaderStage;
import com.ociweb.pronghorn.network.ServerSocketWriterStage;
import com.ociweb.pronghorn.network.http.HTTPClientRequestStage;

public class OpenCloseConnections {

	@Test
	public void testConnectionRemainsOpen() {
		
		StringBuilder target = new StringBuilder();
		
		GreenRuntime.run(new OpenCloseTestServer(8088, false, target));
				
		
		StringBuilder results = new StringBuilder();
		ParallelClientLoadTesterConfig config = new ParallelClientLoadTesterConfig(
				1, 100, 8088, "neverclose", false, results);
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(config, null),
				20_000);
		
		String captured = results.toString();
		
		assertTrue(captured, captured.contains("Total messages: 100"));
		assertTrue(captured, captured.contains("Send failures: 0 out of 100"));
		assertTrue(captured, captured.contains("Timeouts: 0"));
				
	}

	@Test
	public void testConnectionCloses() {
		
		//ServerSocketReaderStage.showRequests = true;
		//ClientSocketReaderStage.showResponse = true;
		//ClientSocketWriterStage.showWrites = true;
		//ServerSocketWriterStage.showWrites = true;
		
		StringBuilder target = new StringBuilder();
		
		GreenRuntime.run(new OpenCloseTestServer(8089, false, target));
						
		StringBuilder results = new StringBuilder();
		ParallelClientLoadTesterConfig config = new ParallelClientLoadTesterConfig(
				1, 100, 8089, "alwaysclose", true, results);
		GreenRuntime.testConcurrentUntilShutdownRequested(
				new ParallelClientLoadTester(config, null),
			10000*	20_000);
		
		String captured = results.toString();
		
		assertTrue(captured, captured.contains("Total messages: 100"));
		assertTrue(captured, captured.contains("Send failures: 0 out of 100"));
		assertTrue(captured, captured.contains("Timeouts: 0"));
				
	}
	
	//add test for a server where the clients keep connecting and the old one must be dropped.
	//we only have 5 connections in the server now..
	
	
	
	
	
	
}
