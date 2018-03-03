package com.ociweb.gl.test;

import com.ociweb.gl.api.*;

import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;
import com.ociweb.pronghorn.util.Appendables;

import java.util.function.Supplier;

public class ParallelClientLoadTester implements GreenAppParallel {
	private final ClientHostPortInstance[] session;

	private final ElapsedTimeRecorder[] elapsedTime;
	
	private int trackId = 0;
	private final int cyclesPerTrack;
    private final String route;
    private final Supplier<Writable> post;
    private final Integer telemetryPort;
    private final String telemetryHost;
    private final boolean insecureClient;

    private final int parallelTracks;
	private final long durationNanos;
	private final HTTPContentTypeDefaults contentType;
	private final int maxPayloadSize;
	private final int ignoreInitialPerTrack;
	private final long responseTimeoutNS;
	private final ParallelTestCountdownDisplay display;
	private final Long rate;
	private long startupTime;
	private final boolean ensureLowLatency;
	
	private final int maxInFlightBits;
	private final int maxInFlight;
	private final int maxInFlightMask;

	private final long[][] callTime;
	private final int[] inFlight;
	private final int[] inFlightHead;
	private final int[] inFlightTail;
		
	

	private static final String STARTUP_NAME   = "startup";
    private static final String CALLER_NAME    = "caller";
    private static final String RESPONDER_NAME = "responder";
    private static final String CALL_TOPIC     = "makeCall";
    private static final String ENDERS_NAME    = "ender";
    private static final String PROGRESS_NAME  = "progessor";
    private static final String ENDERS_TOPIC   = "end";
    private static final String PROGRESS_TOPIC = "progress";

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
		
		this.insecureClient = config.insecureClient;
		this.ensureLowLatency = config.ensureLowLatency;
		
		this.maxInFlightBits = config.inFlightBits;		
		this.maxInFlight = 1<<maxInFlightBits;
		this.maxInFlightMask = maxInFlight-1;
				
		this.responseTimeoutNS = config.responseTimeoutNS;
		this.rate = config.rate;
		
		this.cyclesPerTrack = config.cyclesPerTrack;
		
		this.session = new ClientHostPortInstance[parallelTracks];
		this.callTime = new long[parallelTracks][maxInFlight];
		this.inFlight = new int[parallelTracks];
		this.elapsedTime = new ElapsedTimeRecorder[parallelTracks];
				
		int i = parallelTracks;
		while (--i>=0) {
			session[i]=new ClientHostPortInstance(config.host,config.port);
			elapsedTime[i] = new ElapsedTimeRecorder();
		}
		
		this.route = config.route;
		this.post = payload.post;
		this.telemetryPort = config.telemetryPort;
		this.telemetryHost = config.telemetryHost;
		this.display = display;
		
		this.inFlightHead = new int[parallelTracks];
		this.inFlightTail = new int[parallelTracks];
		
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		
		if (insecureClient) {
			builder.useInsecureNetClient();
		} else {
			builder.useNetClient();
		}
		
		if (telemetryPort != null) {
			
			if (null == this.telemetryHost) {
				builder.enableTelemetry(telemetryPort);				
			} else {
				builder.enableTelemetry(telemetryHost, telemetryPort);
			}
			
		}
		builder.parallelTracks(session.length);
		
		if (rate != null) {
			builder.setDefaultRate(rate);
		}
		
		builder.definePrivateTopic(CALL_TOPIC, STARTUP_NAME, CALLER_NAME);
		builder.definePrivateTopic(CALL_TOPIC, RESPONDER_NAME, CALLER_NAME);
		
		builder.defineUnScopedTopic(ENDERS_TOPIC);
		builder.defineUnScopedTopic(PROGRESS_TOPIC);

		if (responseTimeoutNS > 0) {
			builder.setTimerPulseRate(responseTimeoutNS / 1_000_000);
		}
	}

	@Override
	public void declareBehavior(final GreenRuntime runtime) {

		runtime.setEnsureLowLatency(ensureLowLatency);
		
		StartupListener startClock = new StartupListener() {
			@Override
			public void startup() {
				startupTime = System.nanoTime();				
			}			
		};
		runtime.addStartupListener(startClock);
		
		PubSubListener ender = new PubSubListener() {
			private int enderCounter;
			private int failedMessagesSum;
			private long totalTimeSum;
			GreenCommandChannel cmd3 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			
			@Override
			public boolean message(CharSequence topic, ChannelReader payload) {
				if (topic.equals(ENDERS_TOPIC)) {
					if (payload.hasRemainingBytes()) {
						int failedMessages = payload.readPackedInt();
						totalTimeSum += payload.readPackedLong();
						failedMessagesSum += failedMessages;
					}
					
					if (++enderCounter == (parallelTracks+1)) { //we add 1 for the progress of 100%
						ElapsedTimeRecorder etr = new ElapsedTimeRecorder();
						int t = elapsedTime.length;
						while (--t>=0) {
							etr.add(elapsedTime[t]);
						}
						
						try {
							Thread.sleep(100); //fixing system out IS broken problem.
						} catch (InterruptedException e) {
							Thread.currentThread().interrupt();
						}
						
						int total = parallelTracks * cyclesPerTrack;
						display.displayEnd(etr, total, 
								           totalTimeSum, 
								           failedMessagesSum);
						
						long duration = System.nanoTime()-startupTime;
						Appendables.appendNearestTimeUnit(System.out, duration).append(" test duration\n");
																	
						
						long serverCallsPerSecond = (1_000_000_000L*total)/duration;
						Appendables.appendValue(System.out, serverCallsPerSecond).append(" total calls per second agaist server\n");
												
						return cmd3.shutdown();
					}
				}
				return true;
			}
		};
		runtime.addPubSubListener(ENDERS_NAME, ender).addSubscription(ENDERS_TOPIC);
		
		PubSubListener progress = new PubSubListener() {

			private final int[] finished = new int[parallelTracks];
			private final int[] failures   = new int[parallelTracks];
			GreenCommandChannel cmd4 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			private int lastPct = 0;
			private long lastTime = 0;
			
			@Override
			public boolean message(CharSequence topic, ChannelReader payload) {
				int track = payload.readPackedInt();
				int countDown = payload.readPackedInt();
				int failed = payload.readPackedInt();
				finished[track] = cyclesPerTrack-countDown;
				failures[track] = failed;
				
				int sumFail = 0;
				int sumFinished = 0;
				int i = parallelTracks;
				while (--i>=0) {
					sumFail += failures[i];
					sumFinished += finished[i];
				}
				
				int totalRequests = cyclesPerTrack*parallelTracks;
				int pctDone = (100*sumFinished)/totalRequests;

				long now = 0;
								
				//updates every half a second
				if ( (pctDone != lastPct  && ((now=System.nanoTime()) - lastTime)>500_000_000L)					
						|| 100==pctDone  ) {
					Appendables.appendValue(
							Appendables.appendValue(
									System.out, pctDone).append("% complete  "),sumFail).append(" failed\n");
					lastTime = now;
					lastPct = pctDone;
				}
				
				
				if (100 == pctDone) {
					System.err.println();
					return cmd4.publishTopic(ENDERS_TOPIC);
				}				
				return true;
			}
		};
		runtime.addPubSubListener(PROGRESS_NAME, progress).addSubscription(PROGRESS_TOPIC);
		
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {
				
		
		final int track = trackId++;
		StartupListener startup = new StartupListener() {
			GreenCommandChannel cmd1 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
			@Override
			public void startup() {
				int i = maxInFlight;
				while (--i>=0) {		
					cmd1.publishTopic(CALL_TOPIC); //must use message to startup the system
				}
			}			
		};
		runtime.addStartupListener(STARTUP_NAME, startup );
		
		HTTPResponseListener responder = new TheHTTPResponseListener(runtime, track);
		runtime.addResponseListener(RESPONDER_NAME, responder)
		  .includeHTTPSession(session[track]);

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
			if (inFlight[track]==maxInFlight) {
				return false;
			}
			
            callTime[track][maxInFlightMask & inFlightHead[track]++] = System.nanoTime();
            
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
		private long totalTime;
		

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
			/////
			
			long duration = System.nanoTime() - callTime[track][maxInFlightMask & inFlightTail[track]++];
			
			ElapsedTimeRecorder.record(elapsedTime[track], duration);
			totalTime+=duration;
			
			return nextCall();
		}

		private boolean nextCall() {
			
			if ((0xFFF & countDown) == 0) {
				cmd3.publishTopic(PROGRESS_TOPIC, writer-> {
					writer.writePackedInt(track);
					writer.writePackedInt(countDown);
					writer.writePackedInt(failedResponse);
				});
			}
			
			boolean result;
			if (countDown > 0) {
				if (durationNanos > 0) {
					cmd3.delay(durationNanos);
				}
				result = cmd3.publishTopic(CALL_TOPIC);
			} else {				
				result = cmd3.publishTopic(ENDERS_TOPIC, writer -> 
				   {
					   writer.writePackedInt(failedResponse);
					   writer.writePackedLong(totalTime);
				   });				
			}
			
			//upon failure should not count down
			if (result) {
				countDown--;
			}			
			
			return result;
		}

		@Override
		public void timeEvent(long time, int iteration) {
			
			long callTimeValue = callTime[track][maxInFlightMask & inFlightTail[track]];
			if (callTimeValue != 0) {
				long duration = System.nanoTime() - callTimeValue;
				if (duration > responseTimeoutNS) {
					failedResponse++;
					while (!nextCall()) {//must run now.	
						Thread.yield();
					}
				}
			}
		}
	}
}
