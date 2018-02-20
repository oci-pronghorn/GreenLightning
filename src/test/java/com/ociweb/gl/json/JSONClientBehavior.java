package com.ociweb.gl.json;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class JSONClientBehavior implements HTTPResponseListener, StartupListener, PubSubMethodListener {
    private final GreenRuntime runtime;
    private final GreenCommandChannel command;
    private final ClientHostPortInstance session;

    JSONClientBehavior(GreenRuntime runtime, ClientHostPortInstance session) {
        this.runtime = runtime;
        this.command = runtime.newCommandChannel();
        this.command.ensureHTTPClientRequesting();
        this.command.ensureDynamicMessaging();
        this.session = session;
    }

    @Override
    public void startup() {
        command.httpPost(session, "/atp/location", writer -> writer.write("{}".getBytes()));
    }

    @Override
    public boolean responseHTTP(HTTPResponseReader httpResponseReader) {
        System.out.println("Client received response.");
        httpResponseReader.openPayloadData(new Payloadable() {
            @Override
            public void read(ChannelReader reader) {
                System.out.println(reader.readUTFFully());
            }
        });
        runtime.shutdownRuntime();
        return true;
    }
}
