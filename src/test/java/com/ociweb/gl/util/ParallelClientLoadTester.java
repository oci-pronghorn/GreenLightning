package com.ociweb.gl.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;
import com.ociweb.pronghorn.util.Appendables;

public class ParallelClientLoadTester implements GreenAppParallel {
	
	
	private static final Logger logger = LoggerFactory.getLogger(ParallelClientLoadTester.class);
	
	private final ClientHostPortInstance[] session;
	private final long[] callTime;

	private final ElapsedTimeRecorder[] elapsedTime;
	
	private int trackId = 0;
	private final int totalCycles;
    private final String route;
    private final String post;
    private final boolean enableTelemetry;
    private final boolean insecureClient;
    private final boolean sendTrackId = true;
    private final int parallelTracks;
    
    private static final String STARTUP_NAME   = "startup";
    private static final String CALLER_NAME    = "caller";
    private static final String RESPONDER_NAME = "responder";
    private static final String CALL_TOPIC     = "makeCall";
    private static final String ENDERS_NAME    = "ender";
    private static final String ENDERS_TOPIC   = "end";
            
	public ParallelClientLoadTester(
			int cyclesPerTrack, 
			int port, 
			String route, 
			String post, boolean enableTelemetry) {
		this(4, cyclesPerTrack, port, route, post, enableTelemetry);
	}
	
	public ParallelClientLoadTester(
			int parallelTracks,
			int cyclesPerTrack, 
			int port, 
			String route, 
			String post, boolean enableTelemetry) {
		
		this.parallelTracks = parallelTracks;
		this.insecureClient = true;
		
		this.totalCycles = cyclesPerTrack;
		
		this.session = new ClientHostPortInstance[parallelTracks];
		this.callTime = new long[parallelTracks];
		this.elapsedTime = new ElapsedTimeRecorder[parallelTracks];
				
		int i = parallelTracks;
		while (--i>=0) {
			session[i]=new ClientHostPortInstance("127.0.0.1",port);
			elapsedTime[i] = new ElapsedTimeRecorder();
		}
		
		this.route = route;
		this.post = post;
		this.enableTelemetry = enableTelemetry;
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		
		if (insecureClient) {
			builder.useInsecureNetClient();
		} else {
			builder.useNetClient();
		}
		
		if (enableTelemetry) {
			builder.enableTelemetry();
		}
		builder.parallelTracks(session.length);
		
		builder.definePrivateTopic(CALL_TOPIC, STARTUP_NAME, CALLER_NAME);
		builder.definePrivateTopic(CALL_TOPIC, RESPONDER_NAME, CALLER_NAME);
		
	}

	@Override
	public void declareBehavior(final GreenRuntime runtime) {

	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {
				
		final int track = trackId++;
		final String trackRoute = sendTrackId ? route+"?track="+track : route;

		final Writable writer = new Writable() {

			@Override
			public void write(ChannelWriter writer) {
				writer.append(post);
			}				
		};
		
		StartupListener startup = new StartupListener() {
			GreenCommandChannel cmd1 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			@Override
			public void startup() {
				cmd1.publishTopic(CALL_TOPIC); //must use message to startup the system
			}			
		};
		runtime.addStartupListener(STARTUP_NAME, startup ); 
			
		
		HTTPResponseListener responder = new HTTPResponseListener() {

			int countDown = totalCycles;
				 
			GreenCommandChannel cmd3 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			
			@Override
			public boolean responseHTTP(HTTPResponseReader reader) {
				long duration = System.nanoTime() - callTime[track];
	
				ElapsedTimeRecorder.record(elapsedTime[track], duration);
				
				if (--countDown>0) {
					return cmd3.publishTopic(CALL_TOPIC);
				} else {
					//return cmd3.publishTopic(ENDERS_TOPIC);
					
					System.out.println();			
					elapsedTime[track].report(System.out).append("\n");
										
					System.out.println();
					
					return cmd3.shutdown();		
					
				}
			}
		};
		runtime.addResponseListener(RESPONDER_NAME, responder).includeHTTPSession(session[track]);
		
		
		PubSubListener caller = new PubSubListener() {
			GreenCommandChannel cmd2 = runtime.newCommandChannel(NET_REQUESTER);

			@Override
			public boolean message(CharSequence topic, ChannelReader payload) {
				callTime[track] = System.nanoTime();
				
				if (null==post) {
					//logger.info("sent get to {} {}",session,route);
					return cmd2.httpGet(session[track], trackRoute);
				} else {
					//logger.info("sent post to {} {}",session,route);
					return cmd2.httpPost(session[track], trackRoute, writer);
					
				}
			}
			
		};
		runtime.addPubSubListener(CALLER_NAME, caller).addSubscription(CALL_TOPIC);
		
//		PubSubListener ender = new PubSubListener() {
//			private int enderCounter;
//			GreenCommandChannel cmd3 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
//			
//			@Override
//			public boolean message(CharSequence topic, ChannelReader payload) {
//				
//				if (++enderCounter >= parallelTracks) {
//					System.out.println();
//					ElapsedTimeRecorder etr = new ElapsedTimeRecorder();
//					int t = elapsedTime.length;
//					while (--t>=0) {
//						etr.add(elapsedTime[t]);
//					}					
//					etr.report(System.out).append("\n");
//										
//					System.out.println();
//					
//					return cmd3.shutdown();					
//				}
//				return true;
//			}
//			
//			
//			
//		};
//		runtime.addPubSubListener(ENDERS_NAME, ender).addSubscription(ENDERS_TOPIC);
		
	}

}
