package com.ociweb.gl.json;

import com.ociweb.gl.api.GreenRuntime;
import org.junit.Test;

public class JSONAppTest {
    @Test
    public void testApp() {
        GreenRuntime.run(new JSONServerApp());
        GreenRuntime.testUntilShutdownRequested(new JSONClient(), 2000);
        System.out.println("Test shutdown");
    }
}
