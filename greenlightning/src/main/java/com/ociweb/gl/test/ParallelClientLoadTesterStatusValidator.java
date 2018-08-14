package com.ociweb.gl.test;

import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;

import java.util.function.Supplier;

public class ParallelClientLoadTesterStatusValidator implements Supplier<HTTPResponseListener> {
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
}