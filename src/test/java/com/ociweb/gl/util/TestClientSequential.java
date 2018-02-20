package com.ociweb.gl.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.util.Appendables;

public class TestClientSequential implements GreenApp {
	
	
	private static final Logger logger = LoggerFactory.getLogger(TestClientSequential.class);
	private final ClientHostPortInstance session;
	private int countDown;
	private long callTime;
	
	private long totalTime = 0;
	private final int totalCycles;
    private final String route;
    private final String post;
    private final boolean enableTelemetry;
    
    private static final String STARTUP_NAME   = "startup";
    private static final String CALLER_NAME    = "caller";
    private static final String RESPONDER_NAME = "responder";
    private static final String CALL_TOPIC     = "makeCall";
        
	public TestClientSequential(int cycles, int port, String route, String post, boolean enableTelemetry) {
		this.countDown = cycles;
		this.totalCycles = cycles;
		this.session = new ClientHostPortInstance("127.0.0.1",port);
		this.route = route;
		this.post = post;
		this.enableTelemetry = enableTelemetry;
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		builder.useInsecureNetClient();
		builder.limitThreads(2);
		if (enableTelemetry) {
			builder.enableTelemetry();
		}
		
		//ScriptedNonThreadScheduler.debug = true;
		
		//use private topics
		//builder.definePrivateTopic(STARTUP_NAME, CALLER_NAME, CALL_TOPIC);
		//builder.definePrivateTopic(RESPONDER_NAME, CALLER_NAME, CALL_TOPIC);
		
	}

	@Override
	public void declareBehavior(final GreenRuntime runtime) {
		
		StartupListener startup = new StartupListener() {
			GreenCommandChannel cmd1 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			@Override
			public void startup() {
				cmd1.publishTopic(CALL_TOPIC);
			}			
		};
		runtime.addStartupListener(STARTUP_NAME, startup ); 
			
		
		HTTPResponseListener responder = new HTTPResponseListener() {
			GreenCommandChannel cmd3 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			
			@Override
			public boolean responseHTTP(HTTPResponseReader reader) {
				long duration = System.nanoTime() - callTime;
				
				totalTime += duration;
				
				if (--countDown>0) {
					return cmd3.publishTopic(CALL_TOPIC);
				} else {
					System.out.println();
					Appendables.appendNearestTimeUnit(System.out, totalTime/totalCycles, " latency on "+session);
					System.out.println();
					
					cmd3.shutdown();
					return true;
				}
			}
		};
		runtime.addResponseListener(RESPONDER_NAME, responder).includeHTTPSession(session);
		

		
		PubSubListener caller = new PubSubListener() {
			GreenCommandChannel cmd2 = runtime.newCommandChannel(NET_REQUESTER);

			final Writable writer = new Writable() {

				@Override
				public void write(ChannelWriter writer) {
					writer.append(post);
				}				
			};

			
			@Override
			public boolean message(CharSequence topic, ChannelReader payload) {
				callTime = System.nanoTime();
				
				if (null==post) {
					logger.info("sent get to {} {}",session,route);
					return cmd2.httpGet(session, route);
				} else {
					logger.info("sent post to {} {}",session,route);
					return cmd2.httpPost(session, route, writer);
					
				}
			}
			
		};
		runtime.addPubSubListener(CALLER_NAME, caller).addSubscription(CALL_TOPIC);
	
	}

}
