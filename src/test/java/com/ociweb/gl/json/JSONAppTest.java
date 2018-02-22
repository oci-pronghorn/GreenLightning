package com.ociweb.gl.json;

import org.junit.Test;

import com.ociweb.gl.api.GreenRuntime;

public class JSONAppTest {
    @Test
    public void testApp() {
    	//HTTP1xRouterStage.showHeader = true;
        GreenRuntime.run(new JSONServerApp());
        GreenRuntime.testUntilShutdownRequested(new JSONClient(), 10_000);
        System.out.println("Test shutdown");
    }
}
