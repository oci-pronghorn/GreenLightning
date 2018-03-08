package com.ociweb.gl.test;

import com.ociweb.gl.api.*;

import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;

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
        int maxInFlightBits = config.simultaneousRequestsPerTrack;
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

        this.contentType = payload.contentType;
        this.maxPayloadSize = payload.maxPayloadSize;
		this.post = payload.post;

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

		SystemStartup startClock = new SystemStartup() ;
		runtime.addStartupListener(startClock);

		TrackCompletion ender = new TrackCompletion(runtime);
		runtime.addPubSubListener(ENDERS_NAME, ender).addSubscription(ENDERS_TOPIC);

		PubSubListener progress = new Progress(runtime);
		runtime.addPubSubListener(PROGRESS_NAME, progress).addSubscription(PROGRESS_TOPIC);
	}

	private class TrackCompletion implements PubSubListener {
		private int enderCounter;
		private int failedMessagesSum;
		private long totalTimeSum;
		private final GreenCommandChannel cmd3;

		TrackCompletion(GreenRuntime runtime) {
			this.cmd3 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		}

		@Override
		public boolean message(CharSequence topic, ChannelReader payload) {
			if (topic.equals(ENDERS_TOPIC)) {
				if (payload.hasRemainingBytes()) {
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

					return cmd3.shutdown();
				}
			}
			return true;
		}
	}

	private class SystemStartup implements StartupListener {
		@Override
		public void startup() {
			startupTime = System.nanoTime();
		}
	}

	private class Progress implements PubSubListener, StartupListener {
		private final int[] finished = new int[parallelTracks];
		private final int[] failures = new int[parallelTracks];
		private final GreenCommandChannel cmd4;
		private int lastPct = 0;
		private long lastTime = 0;

		Progress(GreenRuntime runtime) {
			this.cmd4 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		}

		@Override
		public void startup() {
			out.progress(0, 0);
		}

		@Override
		public boolean message(CharSequence topic, ChannelReader payload) {
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
				//System.err.println();
				return cmd4.publishTopic(ENDERS_TOPIC);
			}
			return true;
		}
	}

	@Override
	public void declareParallelBehavior(GreenRuntime runtime) {
		final int track = trackId++;

		TrackStartup startup = new TrackStartup(runtime);
		runtime.addStartupListener(STARTUP_NAME, startup);

		TrackHTTPResponseListener responder = new TrackHTTPResponseListener(runtime, track);
		runtime.addResponseListener(RESPONDER_NAME, responder)
				.includeHTTPSession(session[track]);

		TrackHTTPRequestMaker caller = new TrackHTTPRequestMaker(runtime, track);
		runtime.addPubSubListener(CALLER_NAME, caller).addSubscription(CALL_TOPIC);
	}

	private class TrackStartup implements StartupListener {
		private final GreenCommandChannel cmd1;

		TrackStartup(GreenRuntime runtime) {
			this.cmd1 = runtime.newCommandChannel(DYNAMIC_MESSAGING);
		}

		@Override
		public void startup() {
			int i = maxInFlight;
			while (--i>=0) {
				while(!cmd1.publishTopic(CALL_TOPIC)) {
					//must publish this many to get the world moving
					Thread.yield();
				}
				//must use message to startup the system
			}
		}
	}

	private class TrackHTTPRequestMaker implements PubSubListener {
		private final GreenCommandChannel cmd2;
		private final int track;
		private final String header;
		private final Writable writer;

		TrackHTTPRequestMaker(GreenRuntime runtime, int track) {
			this.cmd2 = runtime.newCommandChannel();
			this.track = track;
			this.header = contentType != null ?
					String.format("%s%s\r\n", HTTPHeaderDefaults.CONTENT_TYPE.writingRoot(), contentType.contentType()) : null;
			this.writer = post != null ? post.get() : null;

			if (post != null) {
				cmd2.ensureHTTPClientRequesting(4, maxPayloadSize + 1024);
			}
			else {
				cmd2.ensureHTTPClientRequesting();
			}
		}

		@Override
		public boolean message(CharSequence topic, ChannelReader payload) {
			callTime[track][maxInFlightMask & inFlightHead[track]++] = System.nanoTime();

			if (null==writer) {
				return cmd2.httpGet(session[track], route);
			}
			else if (header != null) {
				return cmd2.httpPost(session[track], route, header, writer);
			}
			else {
				return cmd2.httpPost(session[track], route, writer);
			}
		}
	}

	private class TrackHTTPResponseListener implements HTTPResponseListener, TimeListener {
		private final GreenCommandChannel cmd3;
		private final int track;
		private int countDown;
		private int failedResponse;
		private long totalTime;

		TrackHTTPResponseListener(GreenRuntime runtime, int track) {
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
				out.connectionClosed(track);
			}

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
			}
			else {
				result = cmd3.publishTopic(ENDERS_TOPIC, writer -> {
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
