package com.ociweb.gl.json;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.util.parse.JSONReader;

public class JSONClientBehavior implements HTTPResponseListener, StartupListener {
    private final GreenRuntime runtime;
    private final GreenCommandChannel command;
    private final HTTPSession session;
    private final JSONReader jsonReader = RestResponse.createReader();
    private final RestResponse restResponse = new RestResponse();

    JSONClientBehavior(GreenRuntime runtime, HTTPSession session) {
        this.runtime = runtime;
        this.command = runtime.newCommandChannel();
        this.command.ensureHTTPClientRequesting();
        this.session = session;
    }

    @Override
    public void startup() {
        command.httpPost(session, "/atp/location", new Writable() {
            @Override
            public void write(ChannelWriter writer) {
                writer.write("{}".getBytes());
            }
        });
    }

    @Override
    public boolean responseHTTP(HTTPResponseReader httpResponseReader) {
        System.out.println("Client received response.");
        // This crashes...
        httpResponseReader.openPayloadData(new Payloadable() {
            @Override
            public void read(ChannelReader reader) {
                restResponse.readFromJSON(jsonReader, reader);
            }
        });
        runtime.shutdownRuntime();
        return true;
    }
}
