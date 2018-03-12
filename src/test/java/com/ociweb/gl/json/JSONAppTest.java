package com.ociweb.gl.json;

import static org.junit.Assert.*;

import org.junit.Ignore;
import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

public class JSONAppTest {
	
	@Test
    public void testApp() {
    	//HTTP1xRouterStage.showHeader = true;
        GreenRuntime serverRuntime = GreenRuntime.run(new JSONServerApp());
        GreenRuntime.testUntilShutdownRequested(new JSONClient(), 1_000);
        serverRuntime.checkForException();
        System.out.println("Test shutdown");
    }
}
