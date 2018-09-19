package com.ociweb.gl.test;

import com.ociweb.gl.api.ArgumentProvider;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.pipe.ChannelWriter;

public class ParallelClientLoadTesterPayload {
    
	public int maxPayloadSize = 500; //default
    
    public HTTPContentTypeDefaults contentType = HTTPContentTypeDefaults.JSON;
    public WritableFactory post = null;
    public ValidatorFactory validator = new ValidatorFactory() {
		@Override
		public boolean validate(long callInstance, HTTPResponseReader reader) {
			 int code = reader.statusCode();
             return code >= 200 && code < 400;
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

            post = new WritableFactory() {
    			@Override
    			public void payloadWriter(long callInstance, ChannelWriter w) {
    				script.write(w);;
    			}
            };
            
        }
    }

    public ParallelClientLoadTesterPayload(String payload) {
        final byte[] bytes = payload.getBytes();
        maxPayloadSize = bytes.length;
        
        final Writable payloadWritable = new Writable() {
			@Override
			public void write(ChannelWriter writer) {
				writer.write(bytes);
			}
		};
        post = new WritableFactory() {
			@Override
			public void payloadWriter(long callInstance, ChannelWriter w) {
				payloadWritable.write(w);;
			}
        };
        		
        		
    }

    public ParallelClientLoadTesterPayload(String[] payload) {
        maxPayloadSize = 0;
        for (int i = 0; i < payload.length; i++) {
            maxPayloadSize = Math.max(maxPayloadSize, payload[i].length());
        }

        final Writable payloadWritable = new ParallelClientLoadTesterPayloadScript(payload);
        post = new WritableFactory() {
			@Override
			public void payloadWriter(long callInstance, ChannelWriter w) {
				payloadWritable.write(w);
			}
        };
        
    }
}

