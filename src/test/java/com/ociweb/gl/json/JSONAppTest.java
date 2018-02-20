package com.ociweb.gl.json;

import org.junit.Ignore;

import com.ociweb.gl.api.GreenRuntime;

public class JSONAppTest {
    @Ignore
    public void testApp() {
        GreenRuntime.run(new JSONServerApp());
        GreenRuntime.testUntilShutdownRequested(new JSONClient(), 2000);
        System.out.println("Test shutdown");
    }
}
