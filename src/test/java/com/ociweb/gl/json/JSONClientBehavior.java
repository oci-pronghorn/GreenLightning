package com.ociweb.gl.json;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.pipe.ChannelReader;

public class JSONClientBehavior implements HTTPResponseListener, StartupListener {
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
        JSONRequest request1 = new JSONRequest();
        request1.name = "Bob";
        request1.age = 22;
        JSONRequest request2 = new JSONRequest();
        request2.name = "Sam";
        request2.age = 44;
        command.httpPost(session, "/test/path?flag=42", writer -> JSONRequest.renderer.render(writer, request1));
        command.httpPost(session, "/test/path", writer -> JSONRequest.renderer.render(writer, request2));
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
