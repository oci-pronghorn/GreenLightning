package com.ociweb.gl.test;

import com.ociweb.gl.api.*;

import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

import java.util.Arrays;
import java.util.function.Supplier;

public class ParallelClientLoadTester implements GreenAppParallel {
    private final String route;
    private boolean insecureClient=true;
    private final int parallelTracks;
    private final int cyclesPerTrack;
    private long durationNanos = 0;
    private final long responseTimeoutNS;
    private final Integer telemetryPort;
    private final String telemetryHost;
    private final Long rate;
    private final boolean ensureLowLatency;
    private final int maxInFlight;
    private final int maxInFlightMask;

	private final Supplier<Writable> post;
    private final int maxPayloadSize;
    private final HTTPContentTypeDefaults contentType;

    private final ClientHostPortInstance[] session;
    private final ElapsedTimeRecorder[] elapsedTime;

    private int trackId = 0;
	private final ParallelClientLoadTesterOutput out;
	private long startupTime;

	private final long[][] callTime;
	private final int[] inFlightHead;
	private final int[] inFlightTail;

	private static final String RESPONDER_NAME = "responder";
	private static final String CALL_TOPIC     = "makeCall";	
	private static final String PROGRESS_NAME  = "progessor";
	private static final String ENDERS_TOPIC   = "end";
	private static final String PROGRESS_TOPIC = "progress";
	
	private static final int PUB_MSGS      = 8000;
	private static final int PUB_MSGS_SIZE = 48;
	

	public ParallelClientLoadTester(
			int cyclesPerTrack,
			int port,
			String route,
			String post,
			boolean enableTelemetry) {

		this(
            new ParallelClientLoadTesterConfig(cyclesPerTrack, port, route, enableTelemetry),
            post != null ? new ParallelClientLoadTesterPayload(post) : null,
            new DefaultParallelClientLoadTesterOutput());
	}

	public ParallelClientLoadTester(
			int parallelTracks,
			int cyclesPerTrack,
			int port,
			String route,
			String post,
			boolean enableTelemetry) {

		this(
            new ParallelClientLoadTesterConfig(parallelTracks, cyclesPerTrack, port, route, enableTelemetry),
            post != null ? new ParallelClientLoadTesterPayload(post) : null,
            new DefaultParallelClientLoadTesterOutput());
	}

	public ParallelClientLoadTester(
			ParallelClientLoadTesterConfig config,
			ParallelClientLoadTesterPayload payload) {
		this(config, payload, new DefaultParallelClientLoadTesterOutput());
	}

	public ParallelClientLoadTester(
			ParallelClientLoadTesterConfig config,
			ParallelClientLoadTesterPayload payload,
			ParallelClientLoadTesterOutput out) {

        this.route = config.route;
        this.telemetryPort = config.telemetryPort;
        this.telemetryHost = config.telemetryHost;
		this.parallelTracks = config.parallelTracks;
		this.durationNanos = config.durationNanos;
		this.insecureClient = config.insecureClient;
		this.ensureLowLatency = config.ensureLowLatency;
        this.responseTimeoutNS = config.responseTimeoutNS;
        this.cyclesPerTrack = config.cyclesPerTrack;
        this.rate = config.rate;

		//bit  size   mask   pos
		//0     1      0      0
		//1     2      1      0,1
		//2     4      3      0,1,2,3
        int maxInFlightBits = config.simultaneousRequestsPerTrackBits;
		this.maxInFlight = 1<< maxInFlightBits;
		this.maxInFlightMask = maxInFlight-1;
		this.callTime = new long[parallelTracks][maxInFlight];
		this.inFlightHead = new int[parallelTracks];
		this.inFlightTail = new int[parallelTracks];

		this.session = new ClientHostPortInstance[parallelTracks];
		this.elapsedTime = new ElapsedTimeRecorder[parallelTracks];

		int i = parallelTracks;
		while (--i>=0) {
			session[i]=new ClientHostPortInstance(config.host,config.port);
			elapsedTime[i] = new ElapsedTimeRecorder();
		}

        this.contentType = null==payload ? null : payload.contentType;
        this.maxPayloadSize = null==payload ? 256 : payload.maxPayloadSize;
		this.post = null==payload? null : payload.post;

		this.out = out;
	}

	@Override
	public void declareConfiguration(Builder builder) {
		if (insecureClient) {
			builder.useInsecureNetClient();
		}
		else {
			builder.useNetClient();
		}

		if (telemetryPort != null) {
			if (null == this.telemetryHost) {
				builder.enableTelemetry(telemetryPort);
			}
			else {
				builder.enableTelemetry(telemetryHost, telemetryPort);
			}

		}
		builder.parallelTracks(session.length);

		if (rate != null) {
			builder.setDefaultRate(rate);
		}

		builder.definePrivateTopic(Math.max(8, maxInFlight) ,100, CALL_TOPIC, RESPONDER_NAME, RESPONDER_NAME);

		builder.defineUnScopedTopic(ENDERS_TOPIC);
		builder.defineUnScopedTopic(PROGRESS_TOPIC);

		if (responseTimeoutNS > 0) {
			builder.setTimerPulseRate(responseTimeoutNS / 1_000_000);
		}
		
	}

	@Override
	public void declareBehavior(final GreenRuntime runtime) {
		runtime.setEnsureLowLatency(ensureLowLatency);

		Progress progress = new Progress(runtime);
		runtime.registerListener(PROGRESS_NAME, progress)
				.addSubscription(ENDERS_TOPIC, progress::enderMessage)
		        .addSubscription(PROGRESS_TOPIC, progress::progressMessage);
		
		
	}

	private class Progress implements PubSubMethodListener, StartupListener {
		private final int[] finished = new int[parallelTracks];
		private final int[] failures = new int[parallelTracks];
		private final GreenCommandChannel cmd4;
		private int lastPct = 0;
		private long lastTime = 0;
		private int enderCounter;
		private int failedMessagesSum;
		private long totalTimeSum;


		Progress(GreenRuntime runtime) {
			this.cmd4 = runtime.newCommandChannel();
			this.cmd4.ensureDynamicMessaging(PUB_MSGS, PUB_MSGS_SIZE);
		}

		@Override
		public void startup() {
			startupTime = System.nanoTime();
			out.progress(0, 0);
		}
		

		public boolean enderMessage(CharSequence topic, ChannelReader payload) {
			if (topic.equals(ENDERS_TOPIC)) {
				if (payload.hasRemainingBytes()) {
					int track = payload.readPackedInt();
					//System.err.println("end of track:"+track);
					
					int failedMessages = payload.readPackedInt();
					totalTimeSum += payload.readPackedLong();
					failedMessagesSum += failedMessages;
									
				}

				if (++enderCounter == (parallelTracks + 1)) { //we add 1 for the progress of 100%
					ElapsedTimeRecorder etr = new ElapsedTimeRecorder();
					int t = elapsedTime.length;
					while (--t >= 0) {
						etr.add(elapsedTime[t]);
					}

                    int total = parallelTracks * cyclesPerTrack;
                    long duration = System.nanoTime() - startupTime;
                    long serverCallsPerSecond = (1_000_000_000L * total) / duration;

					out.end(
					        etr, total,
							totalTimeSum,
							failedMessagesSum,
                            duration,
                            serverCallsPerSecond
                            );

					return cmd4.shutdown();
				}
			}
			return true;
		}
		
		public boolean progressMessage(CharSequence topic, ChannelReader payload) {
			int track = payload.readPackedInt();
			int countDown = payload.readPackedInt();
			int failed = payload.readPackedInt();
			finished[track] = cyclesPerTrack - countDown;
			failures[track] = failed;

			int sumFail = 0;
			int sumFinished = 0;
			int i = parallelTracks;
			while (--i >= 0) {
				sumFail += failures[i];
				sumFinished += finished[i];
			}

			int totalRequests = cyclesPerTrack * parallelTracks;
			int pctDone = (100 * sumFinished) / totalRequests;

			long now = 0;

			//updates every half a second
			if ((pctDone != lastPct && ((now = System.nanoTime()) - lastTime) > 500_000_000L)
					|| 100 == pctDone) {
			    out.progress(pctDone, sumFail);
			    
			    
				lastTime = now;
				lastPct = pctDone;
			}

			if (100 == pctDone) {
				//System.err.println(Arrays.toString(finished)+" "+cyclesPerTrack+" "+parallelTracks);
				//System.err.println();
				return cmd4.publishTopic(ENDERS_TOPIC);
			}
			return true;
		}
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {
		final int track = trackId++;

		TrackHTTPResponseListener responder = new TrackHTTPResponseListener(runtime, track);
		runtime.registerListener(RESPONDER_NAME, responder)
		.addSubscription(CALL_TOPIC, responder::callMessage)
				.includeHTTPSession(session[track]);

	}


	private class TrackHTTPResponseListener implements HTTPResponseListener, TimeListener, StartupListener, PubSubMethodListener {
		private final GreenCommandChannel cmd3;
		private final int track;
		private int countDown;
		private int failedResponse;
		private long totalTime;
		
		private final GreenCommandChannel cmd2;
		private final String header;
		private final Writable writer;

		TrackHTTPResponseListener(GreenRuntime runtime, int track) {
			this.track = track;
			countDown = cyclesPerTrack;
			cmd3 = runtime.newCommandChannel();
			cmd3.ensureDynamicMessaging(Math.max(maxInFlight,PUB_MSGS), PUB_MSGS_SIZE);
			if (durationNanos > 0) {
				cmd3.ensureDelaySupport();
			}
		
			this.cmd2 = runtime.newCommandChannel();
		
			this.header = contentType != null ?
					String.format("%s%s\r\n", HTTPHeaderDefaults.CONTENT_TYPE.writingRoot(), contentType.contentType()) : null;
			this.writer = post != null ? post.get() : null;

			if (post != null) {
				cmd2.ensureHTTPClientRequesting(4, maxPayloadSize + 1024);
			} else {
				cmd2.ensureHTTPClientRequesting();
			}
			
		}

		public boolean callMessage(CharSequence topic, ChannelReader payload) {
			return makeCall();
		}

		private boolean makeCall() {
			callTime[track][maxInFlightMask & inFlightHead[track]++] = System.nanoTime();

			if (null==writer) {
				return cmd2.httpGet(session[track], route);
			} else if (header != null) {
				return cmd2.httpPost(session[track], route, header, writer);
			} else {
				return cmd2.httpPost(session[track], route, writer);
			}
		}

		
		@Override
		public void startup() {
			long now = System.currentTimeMillis();
			int i = maxInFlight;
			while (--i>=0) {
								
				while(!cmd3.publishTopic(CALL_TOPIC, WaitFor.None)) {
					//must publish this many to get the world moving
					Thread.yield();
					if ((System.currentTimeMillis()-now) > 10_000) {
						System.err.println("Unable to send "+maxInFlight+" messages to start up.");
						System.exit(-1);
					}
				}
				//must use message to startup the system
			}
		}

		@Override
		public boolean responseHTTP(HTTPResponseReader reader) {
			boolean connectionClosed = reader.isConnectionClosed();
			if (connectionClosed) {
				out.connectionClosed(track);
			}

			long duration = System.nanoTime() - callTime[track][maxInFlightMask & inFlightTail[track]++];

			ElapsedTimeRecorder.record(elapsedTime[track], duration);
			totalTime+=duration;

			return nextCall();
		}

		private boolean nextCall() {
			if ((0xFFFF & countDown) == 0) {
				cmd3.publishTopic(PROGRESS_TOPIC, writer-> {
					writer.writePackedInt(track);
					writer.writePackedInt(countDown);
					writer.writePackedInt(failedResponse);
				}, WaitFor.None);
			}

			boolean result;
			if (countDown > 0) {
				if (durationNanos > 0) {
					cmd3.delay(durationNanos);
				}
				
				result = cmd3.publishTopic(CALL_TOPIC, WaitFor.None);
		
			}
			else {
				if (1 == (maxInFlight+countDown)) {
					//only end after all the inFlightMessages have returned.
					result = cmd3.publishTopic(ENDERS_TOPIC, writer -> {
						writer.writePackedInt(track);
						writer.writePackedInt(failedResponse);
						writer.writePackedLong(totalTime);
					}, WaitFor.None);
					//System.err.println("publish end of "+track);
				} else {
					result = true;
				}
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
