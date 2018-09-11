package com.ociweb.gl.json;

import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenFramework;
import com.ociweb.gl.api.GreenRuntime;

public class JSONClient implements GreenApp {
    ClientHostPortInstance session;

    @Override
    public void declareConfiguration(GreenFramework builder) {
        builder.useInsecureNetClient();
        // Create the session
        session = builder.useNetClient().newHTTPSession("127.0.0.1", 8068).finish();
    }
    @Override
    public void declareBehavior(GreenRuntime runtime) {
        // Inject business logic
        runtime.registerListener(new JSONClientBehavior(runtime, session)).acceptHostResponses(session);
    }
}
