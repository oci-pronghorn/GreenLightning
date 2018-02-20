package com.ociweb.gl.json;

import org.junit.Ignore;

import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStage;

import org.junit.Test;

public class JSONAppTest {
    @Test
    public void testApp() {
    	//HTTP1xRouterStage.showHeader = true;
        GreenRuntime.run(new JSONServerApp());
        GreenRuntime.testUntilShutdownRequested(new JSONClient(), 420_000);
        System.out.println("Test shutdown");
    }
}
