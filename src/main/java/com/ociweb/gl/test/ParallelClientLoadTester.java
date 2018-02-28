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
	private final int cyclesPerTrack;
    private final String route;
    private final Supplier<Writable> post;
    private final Integer telemetryPort;
    private final boolean insecureClient;

    private final int parallelTracks;
	private final long durationNanos;
	private final HTTPContentTypeDefaults contentType;
	private final int maxPayloadSize;
	private final int ignoreInitialPerTrack;
	private final long responseTimeoutNS;
	private final ParallelTestCountdownDisplay display;

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

		this(
				new ParallelTestConfig(cyclesPerTrack, port, route, enableTelemetry),
				post != null ? new ParallelTestPayload(post) : null,
				new DefaultParallelTestCountdownDisplay());
	}

	public ParallelClientLoadTester(
			int parallelTracks,
			int cyclesPerTrack,
			int port,
			String route,
			String post,
			boolean enableTelemetry) {

		this(
				new ParallelTestConfig(parallelTracks, cyclesPerTrack, port, route, enableTelemetry),
				post != null ? new ParallelTestPayload(post) : null,
				new DefaultParallelTestCountdownDisplay());
	}

	public ParallelClientLoadTester(
			ParallelTestConfig config,
			ParallelTestPayload payload) {
		this(config, payload, new DefaultParallelTestCountdownDisplay());
	}
	
	public ParallelClientLoadTester(
			ParallelTestConfig config,
			ParallelTestPayload payload,
			ParallelTestCountdownDisplay display) {
		
		this.parallelTracks = config.parallelTracks;
		this.durationNanos = config.durationNanos;
		this.contentType = payload.contentType;
		this.maxPayloadSize = payload.maxPayloadSize;
		this.ignoreInitialPerTrack = config.ignoreInitialPerTrack;
		this.insecureClient = true;

		this.responseTimeoutNS = config.responseTimeoutNS;
		
		this.cyclesPerTrack = config.cyclesPerTrack;
		
		this.session = new ClientHostPortInstance[parallelTracks];
		this.callTime = new long[parallelTracks];
		this.elapsedTime = new ElapsedTimeRecorder[parallelTracks];
				
		int i = parallelTracks;
		while (--i>=0) {
			session[i]=new ClientHostPortInstance(config.host,config.port);
			elapsedTime[i] = new ElapsedTimeRecorder();
		}
		
		this.route = config.route;
		this.post = payload.post;
		this.telemetryPort = config.telemetryPort;
		this.display = display;
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		
		if (insecureClient) {
			builder.useInsecureNetClient();
		} else {
			builder.useNetClient();
		}
		
		if (telemetryPort != null) {
			builder.enableTelemetry(telemetryPort);
		}
		builder.parallelTracks(session.length);
		
		builder.definePrivateTopic(CALL_TOPIC, STARTUP_NAME, CALLER_NAME);
		builder.definePrivateTopic(CALL_TOPIC, RESPONDER_NAME, CALLER_NAME);
		
		builder.defineUnScopedTopic(ENDERS_TOPIC);

		if (responseTimeoutNS > 0) {
			builder.setTimerPulseRate(responseTimeoutNS / 1_000_000);
		}
	}

	@Override
	public void declareBehavior(final GreenRuntime runtime) {

		PubSubListener ender = new PubSubListener() {
			private int enderCounter;
			private int failedMessagesSum;
			GreenCommandChannel cmd3 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			
			@Override
			public boolean message(CharSequence topic, ChannelReader payload) {
				int failedMessages = payload.readPackedInt();
				failedMessagesSum += failedMessages;

				if (++enderCounter >= parallelTracks) {
					ElapsedTimeRecorder etr = new ElapsedTimeRecorder();
					int t = elapsedTime.length;
					while (--t>=0) {
						etr.add(elapsedTime[t]);
					}
					display.displayEnd(etr, parallelTracks * cyclesPerTrack, failedMessagesSum);
					return cmd3.shutdown();
				}
				display.displayTrackEnd(enderCounter);

				return true;
			}
		};
		runtime.addPubSubListener(ENDERS_NAME, ender).addSubscription(ENDERS_TOPIC);
		
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {
				
		final int track = trackId++;
		StartupListener startup = new StartupListener() {
			GreenCommandChannel cmd1 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			@Override
			public void startup() {
				cmd1.publishTopic(CALL_TOPIC); //must use message to startup the system
			}			
		};
		runtime.addStartupListener(STARTUP_NAME, startup );
		
		HTTPResponseListener responder = new TheHTTPResponseListener(runtime, track);
		runtime.addResponseListener(RESPONDER_NAME, responder).includeHTTPSession(session[track]);

        final GreenCommandChannel cmd2 = runtime.newCommandChannel();

        if (post != null) {
            cmd2.ensureHTTPClientRequesting(4, maxPayloadSize + 1024);
        }
        else {
            cmd2.ensureHTTPClientRequesting();
        }

		final Writable writer = post != null ? post.get() : null;
		final String header = contentType != null ?
				String.format("%s%s\r\n", HTTPHeaderDefaults.CONTENT_TYPE.writingRoot(), contentType.contentType()) : null;

		PubSubListener caller = (topic, payload) -> {
            callTime[track] = System.nanoTime();
            if (null==writer) {
                return cmd2.httpGet(session[track], route);
            } else if (header != null) {
                return cmd2.httpPost(session[track], route, header, writer);
            }
            else {
                return cmd2.httpPost(session[track], route, writer);
            }
        };
		runtime.addPubSubListener(CALLER_NAME, caller).addSubscription(CALL_TOPIC);
	}

	private class TheHTTPResponseListener implements HTTPResponseListener, TimeListener {
		private final GreenCommandChannel cmd3;
		private final int track;

		private int countDown;
		private int failedResponse;

		TheHTTPResponseListener(GreenRuntime runtime, int track) {
			this.track = track;
			countDown = cyclesPerTrack;
			cmd3 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			if (durationNanos > 0) {
				cmd3.ensureDelaySupport();
			}
		}

		@Override
		public boolean responseHTTP(HTTPResponseReader reader) {
			boolean connectionClosed = reader.isConnectionClosed();
			if (connectionClosed) {
				display.displayConnectionClosed(track);
			}
			//if (ignoreInitialPerTrack > 0 && (cyclesPerTrack - countDown) < ignoreInitialPerTrack) {
				//display.display(track, cyclesPerTrack, countDown, ParallelTestCountdownDisplay.Response.ResponseIgnored);
			//}
			//else {
				long duration = System.nanoTime() - callTime[track];
				ElapsedTimeRecorder.record(elapsedTime[track], duration);
				display.display(track, cyclesPerTrack, countDown, ParallelTestCountdownDisplay.Response.ResponseReveived);
			//}
			return nextCall();
		}

		private boolean nextCall() {
			if (--countDown >= 0) {
				if (durationNanos > 0) {
					cmd3.delay(durationNanos);
				}
				return cmd3.publishTopic(CALL_TOPIC);
			} else {
				return cmd3.publishTopic(ENDERS_TOPIC, writer -> writer.writePackedInt(failedResponse));
			}
		}

		@Override
		public void timeEvent(long time, int iteration) {
			long callTimeValue = callTime[track];
			if (callTimeValue != 0 && countDown > 0) {
				long duration = System.nanoTime() - callTimeValue;
				if (duration > responseTimeoutNS) {
					failedResponse++;
					display.display(track, cyclesPerTrack, countDown, ParallelTestCountdownDisplay.Response.ResponseTimeout);
					nextCall();
				}
			}
		}
	}
}
