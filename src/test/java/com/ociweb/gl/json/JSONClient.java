package com.ociweb.gl.json;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.ClientHostPortInstance;

public class JSONClient implements GreenApp {
    @Override
    public void declareConfiguration(Builder builder) {
        builder.useInsecureNetClient();
    }
    @Override
    public void declareBehavior(GreenRuntime runtime) {
        // Create the session
        ClientHostPortInstance session = new ClientHostPortInstance("127.0.0.1",8088,0);
        // Inject business logic
        runtime.registerListener(new JSONClientBehavior(runtime, session)).includeHTTPSession(session);
    }
}
