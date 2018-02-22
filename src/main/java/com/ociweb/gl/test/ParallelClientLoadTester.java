package com.ociweb.gl.test;

import com.ociweb.gl.api.*;

import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

import java.util.function.Supplier;

public class ParallelClientLoadTester implements GreenAppParallel {
	private final ClientHostPortInstance[] session;
	private final long[] callTime;

	private final ElapsedTimeRecorder[] elapsedTime;
	
	private int trackId = 0;
	private final int totalCycles;
    private final String route;
    private final Supplier<Writable> post;
    private final Integer enableTelemetry;
    private final boolean insecureClient;
    private final boolean sendTrackId;
    private final int parallelTracks;
	private final HTTPContentTypeDefaults contentType;
	private final int maxPayload;

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
			String post,
			boolean enableTelemetry) {
		this(4, cyclesPerTrack, "127.0.0.1", port, route, null, ()->writer->writer.append(post), post.length(), enableTelemetry ? TelemetryConfig.defaultTelemetryPort + 13 : null, false);
	}

	public ParallelClientLoadTester(
			int parallelTracks,
			int cyclesPerTrack,
			int port,
			String route,
			String post,
			boolean enableTelemetry,
			boolean sendTrackId) {
		this(parallelTracks, cyclesPerTrack, "127.0.0.1", port, route, null, ()->writer->writer.append(post), post.length(), enableTelemetry ? TelemetryConfig.defaultTelemetryPort + 13 : null, sendTrackId);
	}
	
	public ParallelClientLoadTester(
			int parallelTracks,
			int cyclesPerTrack,
			String host,
			int port, 
			String route,
			HTTPContentTypeDefaults contentType,
			Supplier<Writable> post,
			int maxPayload,
			Integer enableTelemetry,
			boolean sendTrackId) {
		
		this.parallelTracks = parallelTracks;
		this.contentType = contentType;
		this.maxPayload = maxPayload;
		this.insecureClient = true;
		this.sendTrackId = sendTrackId;
		
		this.totalCycles = cyclesPerTrack;
		
		this.session = new ClientHostPortInstance[parallelTracks];
		this.callTime = new long[parallelTracks];
		this.elapsedTime = new ElapsedTimeRecorder[parallelTracks];
				
		int i = parallelTracks;
		while (--i>=0) {
			session[i]=new ClientHostPortInstance(host,port);
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
		
		if (enableTelemetry != null) {
			builder.enableTelemetry(enableTelemetry);
		}
		builder.parallelTracks(session.length);
		
		builder.definePrivateTopic(CALL_TOPIC, STARTUP_NAME, CALLER_NAME);
		builder.definePrivateTopic(CALL_TOPIC, RESPONDER_NAME, CALLER_NAME);
		
		builder.defineUnScopedTopic(ENDERS_TOPIC);
		
	}

	@Override
	public void declareBehavior(final GreenRuntime runtime) {

		PubSubListener ender = new PubSubListener() {
			private int enderCounter;
			GreenCommandChannel cmd3 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			
			@Override
			public boolean message(CharSequence topic, ChannelReader payload) {
				
				if (++enderCounter >= parallelTracks) {
					System.out.println();
					ElapsedTimeRecorder etr = new ElapsedTimeRecorder();
					int t = elapsedTime.length;
					while (--t>=0) {
						etr.add(elapsedTime[t]);
					}					
					etr.report(System.out).append("\n");
										
					System.out.println();
					return cmd3.shutdown();
				}
				System.out.println("Ender " + enderCounter);

				return true;
			}
			
			
			
		};
		runtime.addPubSubListener(ENDERS_NAME, ender).addSubscription(ENDERS_TOPIC);
		
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {
				
		final int track = trackId++;
		final String trackRoute = sendTrackId ? route+"?track="+track : route;

		StartupListener startup = new StartupListener() {
			GreenCommandChannel cmd1 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			@Override
			public void startup() {
				cmd1.publishTopic(CALL_TOPIC); //must use message to startup the system
			}			
		};
		runtime.addStartupListener(STARTUP_NAME, startup ); 

		String out = "Track " + track + ".";
		
		HTTPResponseListener responder = new HTTPResponseListener() {

			int countDown = totalCycles;

			GreenCommandChannel cmd3 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			
			@Override
			public boolean responseHTTP(HTTPResponseReader reader) {
				long duration = System.nanoTime() - callTime[track];

				ElapsedTimeRecorder.record(elapsedTime[track], duration);

				if (countDown == totalCycles) {
					System.out.println(out + countDown);
				}
				else if (countDown >= 10_000) {
					if ((countDown % 10_000) == 0) {
						System.out.println(out + countDown);
					}
				}
				else if (countDown >= 1_000) {
					if ((countDown % 1_000) == 0) {
						System.out.println(out + countDown);
					}
				}
				else if (countDown >= 100) {
					if ((countDown % 100) == 0) {
						System.out.println(out + countDown);
					}
				}
				else if (countDown >= 10) {
					if ((countDown % 10) == 0) {
						System.out.println(out + countDown);
					}
				}
				else {
					System.out.println(out + countDown);
				}
				if (--countDown >= 0) {
					return cmd3.publishTopic(CALL_TOPIC);
				} else {
					return cmd3.publishTopic(ENDERS_TOPIC);
				}
			}
		};
		runtime.addResponseListener(RESPONDER_NAME, responder).includeHTTPSession(session[track]);

        final GreenCommandChannel cmd2 = runtime.newCommandChannel();
        if (post != null) {
            cmd2.ensureHTTPClientRequesting(4, maxPayload + 1024);
        }
        else {
            cmd2.ensureHTTPClientRequesting();
        }

		final Writable writer = post != null ? post.get() : null;
		final String header = contentType != null ?
				String.format("%s%s\r\n", HTTPHeaderDefaults.CONTENT_TYPE.writingRoot(), contentType.contentType()) : null;

		PubSubListener caller = new PubSubListener() {
			@Override
			public boolean message(CharSequence topic, ChannelReader payload) {
				callTime[track] = System.nanoTime();

				if (null==writer) {
					//logger.info("sent get to {} {}",session,trackRoute);
					return cmd2.httpGet(session[track], trackRoute);
				} else if (header != null) {
					//logger.info("sent post to {} {}",session,trackRoute);
					return cmd2.httpPost(session[track], trackRoute, header, writer);
				}
				else {
					return cmd2.httpPost(session[track], trackRoute, writer);
				}
			}
		};
		runtime.addPubSubListener(CALLER_NAME, caller).addSubscription(CALL_TOPIC);
	}
}
