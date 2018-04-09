package com.ociweb.gl.test;

import com.ociweb.gl.api.ArgumentProvider;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;

import java.util.function.Supplier;

public class ParallelClientLoadTesterPayload {
    public int maxPayloadSize = 1024;
    public HTTPContentTypeDefaults contentType = HTTPContentTypeDefaults.JSON;
    public Supplier<Writable> post = null;
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

    public ParallelClientLoadTesterPayload(ArgumentProvider args) {
        inject(args);
    }

    public void inject(ArgumentProvider args) {
        maxPayloadSize = args.getArgumentValue("--maxPayloadSize", "-mps", maxPayloadSize);
        contentType = args.getArgumentValue("--contrentType", "-ct", HTTPContentTypeDefaults.class, contentType);
        String scriptFile = args.getArgumentValue("--script", "-s", (String)null);
        if (scriptFile != null) {
            ParallelClientLoadTesterPayloadScript script = new ParallelClientLoadTesterPayloadScript(scriptFile);
            post = ()->script;
        }
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

