package com.ociweb.gl.json;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.ClientHostPortInstance;

public class JSONClient implements GreenApp {
    ClientHostPortInstance session;

    @Override
    public void declareConfiguration(Builder builder) {
        builder.useInsecureNetClient();
        // Create the session
        session = builder.createHTTP1xClient("127.0.0.1", 8068).finish();
    }
    @Override
    public void declareBehavior(GreenRuntime runtime) {
        // Inject business logic
        runtime.registerListener(new JSONClientBehavior(runtime, session)).includeHTTPSession(session);
    }
}
