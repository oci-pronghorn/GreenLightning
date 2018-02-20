package com.ociweb.gl.json;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class JSONServerBehavior implements RestListener {
    private final GreenRuntime runtime;
    private final GreenCommandChannel channel;
    private final JSONResponse response = new JSONResponse();

    static int defineRoute(Builder builder) {
        return builder.defineRoute()
                .path("/test/path")
                .path("/test/path?flag=#{flag}")
                .defaultInteger("flag", -6).routeId();
    }

    JSONServerBehavior(GreenRuntime runtime) {
        channel = runtime.newCommandChannel(NET_RESPONDER);
        this.runtime = runtime;
    }

    @Override
    public boolean restRequest(HTTPRequestReader request) {
        int f = request.getInt("flag".getBytes());

        // BUG: f is always -6
        // -- unknown key specified in getIntCall
        // -- client sends value

        System.out.println("Server received request. " + f);
        channel.publishHTTPResponse(
                request.getConnectionId(), request.getSequenceCode(),
                200, false, HTTPContentTypeDefaults.JSON,
                new Writable() {
                    @Override
                    public void write(ChannelWriter writer) {
                    	
                        System.err.println("pre "+writer.length());
                        
                    	response.writeToJSON(writer);
                        
                    	System.err.println("post "+writer.length());
                    	
                        
                    }
                });
        //runtime.shutdownRuntime();
        return true;
    }
}
