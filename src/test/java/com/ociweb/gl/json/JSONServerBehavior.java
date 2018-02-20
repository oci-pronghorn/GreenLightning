package com.ociweb.gl.json;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.util.parse.JSONReader;

public class JSONServerBehavior implements RestListener {
    private final GreenRuntime runtime;
    private final GreenCommandChannel channel;
    private final JSONResponse response = new JSONResponse();
    private final JSONRequest jsonRequest = new JSONRequest();
    private final JSONReader jsonReader = JSONRequest.jsonExtractor.reader();

    static int defineRoute(Builder builder) {
        return builder.defineRoute(JSONRequest.jsonExtractor)
        		.path("/test/path")
        		.path("/test/path?flag=#{flag}")
                .defaultInteger("flag", -6)
                .routeId();
    }

    JSONServerBehavior(GreenRuntime runtime) {
        channel = runtime.newCommandChannel(NET_RESPONDER);
        this.runtime = runtime;
    }

    @Override
    public boolean restRequest(HTTPRequestReader request) {
        int f = request.getInt("flag".getBytes());

        request.openPayloadData(reader -> {
            jsonRequest.readFromJSON(jsonReader, reader);
        });

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
