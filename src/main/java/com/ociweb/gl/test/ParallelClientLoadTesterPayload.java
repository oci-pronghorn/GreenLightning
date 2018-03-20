package com.ociweb.gl.test;

import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;

import java.util.function.Supplier;

public class ParallelClientLoadTesterPayload {
    public HTTPContentTypeDefaults contentType = null;
    public Supplier<Writable> post = null;
    public int maxPayloadSize = 512;

    public Supplier<HTTPResponseListener> validate = new Supplier<HTTPResponseListener>() {
        @Override
        public HTTPResponseListener get() {
            return new HTTPResponseListener() {
                @Override
                public boolean responseHTTP(HTTPResponseReader reader) {
                    int code = reader.statusCode();
                    return code >= 200 && code < 400;
                }
            };
        }
    };

    public ParallelClientLoadTesterPayload() {
    }

    public ParallelClientLoadTesterPayload(String payload) {
        final byte[] bytes = payload.getBytes();
        maxPayloadSize = bytes.length;
        post = ()->writer->writer.write(bytes);
    }

    public ParallelClientLoadTesterPayload(String[] payload) {
        maxPayloadSize = 0;
        for (int i = 0; i < payload.length; i++) {
            maxPayloadSize = Math.max(maxPayloadSize, payload[i].length());
        }
        post = ()-> new ParallelClientLoadTesterPayloadScript(payload);
    }
}

