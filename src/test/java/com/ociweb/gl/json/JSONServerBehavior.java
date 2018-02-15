package com.ociweb.gl.json;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class JSONServerBehavior implements RestListener {
    private final GreenRuntime runtime;
    private final GreenCommandChannel channel;
    private final RestResponse response = new RestResponse();

    static int defineRoute(Builder builder) {
        return builder.defineRoute().path("/atp/location").routeId();
    }

    JSONServerBehavior(GreenRuntime runtime) {
        channel = runtime.newCommandChannel();
        channel.ensureHTTPServerResponse();
        this.runtime = runtime;
    }

    @Override
    public boolean restRequest(HTTPRequestReader request) {
        
        channel.publishHTTPResponse(
                request.getConnectionId(), request.getSequenceCode(),
                200, false, HTTPContentTypeDefaults.JSON,
                new Writable() {
                    @Override
                    public void write(ChannelWriter writer) {
                        response.writeToJSON(writer);
                    }
                });
        runtime.shutdownRuntime();
        return true;
    }
}
