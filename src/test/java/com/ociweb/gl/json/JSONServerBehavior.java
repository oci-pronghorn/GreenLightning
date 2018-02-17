package com.ociweb.gl.json;
import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class JSONServerBehavior implements RestListener {
    private final GreenRuntime runtime;
    private final GreenCommandChannel channel;
    private final JSONObject response = new JSONObject();

    static int defineRoute(Builder builder) {
        return builder.defineRoute().path("/atp/location").routeId();
    }

    JSONServerBehavior(GreenRuntime runtime) {
        channel = runtime.newCommandChannel(NET_RESPONDER);
        this.runtime = runtime;
    }

    @Override
    public boolean restRequest(HTTPRequestReader request) {
        System.out.println("Server received request.");
        channel.publishHTTPResponse(
                request.getConnectionId(), request.getSequenceCode(),
                200, false, HTTPContentTypeDefaults.JSON,
                new Writable() {
                    @Override
                    public void write(ChannelWriter writer) {
                        response.writeToJSON(writer);
                    }
                });
        //runtime.shutdownRuntime();
        return true;
    }
}
