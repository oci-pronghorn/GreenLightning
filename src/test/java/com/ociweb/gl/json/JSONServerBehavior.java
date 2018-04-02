package com.ociweb.gl.json;

import static org.junit.Assert.assertEquals;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.util.parse.JSONReader;

public class JSONServerBehavior implements RestListener {
    private final GreenRuntime runtime;
    private final GreenCommandChannel channel;
    private final JSONResponse response = new JSONResponse();
    private final JSONRequest jsonRequest = new JSONRequest();
    private final JSONReader jsonReader = JSONRequest.jsonExtractor.reader();
    private final long flagsFieldId;
    
    static int defineRoute(Builder builder) {
        return builder.defineRoute(JSONRequest.jsonExtractor)
        		.path("/test/path")
        		.path("/test/path?flag=#{flag}")
                .defaultInteger("flag", -6)
                .routeId();
    }

    JSONServerBehavior(GreenRuntime runtime, long flagsFieldId) {
    	this.flagsFieldId = flagsFieldId;
        this.channel = runtime.newCommandChannel(NET_RESPONDER);
        this.runtime = runtime;
    }

    @Override
    public boolean restRequest(HTTPRequestReader request) {
        int f = request.structured().readInt(flagsFieldId);

        request.openPayloadData(reader -> {
            jsonRequest.reset();
            jsonRequest.readFromJSON(jsonReader, reader);
        });

        System.out.println("Server: " + f + " " + jsonRequest);

        if (f == 42) assertEquals(42, jsonRequest.getValue());
        if (f == -6) assertEquals(43, jsonRequest.getValue());

        channel.publishHTTPResponse(
                request.getConnectionId(), request.getSequenceCode(),
                200, false, HTTPContentTypeDefaults.JSON,
                new Writable() {
                    @Override
                    public void write(ChannelWriter writer) {
                    	
                        //System.err.println("pre "+writer.length());
                        
                    	response.writeToJSON(writer);
                        
                    	//System.err.println("post "+writer.length());
                    	
                        
                    }
                });
        //runtime.shutdownRuntime();
        return true;
    }
}
