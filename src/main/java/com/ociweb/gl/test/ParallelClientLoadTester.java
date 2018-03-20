package com.ociweb.gl.test;

import java.util.function.Supplier;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.HeaderWritable;
import com.ociweb.gl.api.HeaderWriter;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;
import com.ociweb.pronghorn.util.Appendables;

public class ParallelClientLoadTester implements GreenAppParallel {
    private final String route;
	private boolean insecureClient=true;
    private final int parallelTracks;
    private final long cyclesPerTrack;
    private long durationNanos = 0;
    private final long responseTimeoutNS;
    private final Integer telemetryPort;
    private final String telemetryHost;
    private final Long rate;
    private final int maxInFlight;
    private final int maxInFlightMask;

	private final Supplier<Writable> post;
    private final int maxPayloadSize;
    private final HTTPContentTypeDefaults contentType;
	private final Supplier<HTTPResponseListener> validate;

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
		this.validate = null==payload? null : payload.validate;

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
		
		builder.setGlobalSLALatencyNS(20_000_000);

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

		builder.definePrivateTopic(2+Math.max(8, maxInFlight) ,0, CALL_TOPIC, RESPONDER_NAME, RESPONDER_NAME);

		builder.defineUnScopedTopic(ENDERS_TOPIC);
		builder.defineUnScopedTopic(PROGRESS_TOPIC);

		if (responseTimeoutNS > 0) {
			Appendables.appendNearestTimeUnit(System.out.append("Checking for timeouts at this rate: "), responseTimeoutNS).append('\n');
			builder.setTimerPulseRate(responseTimeoutNS / 1_000_000);
		}
		
	}

	@Override
	public void declareBehavior(final GreenRuntime runtime) {

		Progress progress = new Progress(runtime);
		runtime.registerListener(PROGRESS_NAME, progress)
				.SLALatencyNS(200_000_000)//due to use of System out and shutdown this is allowed more time
				.addSubscription(ENDERS_TOPIC, progress::enderMessage)
		        .addSubscription(PROGRESS_TOPIC, progress::progressMessage);
		
		
	}

	private class Progress implements PubSubMethodListener, StartupListener {

		private final GreenCommandChannel cmd4;

		private final long[] finished = new long[parallelTracks];
		private final int[] sendAttempts = new int[parallelTracks];
		private final int[] sendFailures = new int[parallelTracks];
		private final int[] timeouts = new int[parallelTracks];
		private final int[] responsesReceived = new int[parallelTracks];
		private final int[] responsesInvalid = new int[parallelTracks];

		private int sendAttemptsSum;
		private int sendFailuresSum;
		private int timeoutsSum;
		private int responsesReceivedSum;
		private int responsesInvalidSum;

		private int lastPct = 0;
		private long lastTime = 0;
		private int enderCounter;
		private long totalTimeSum;

		Progress(GreenRuntime runtime) {
			this.cmd4 = runtime.newCommandChannel();
			this.cmd4.ensureDynamicMessaging(Math.max(PUB_MSGS, maxInFlight), PUB_MSGS_SIZE);
		}

		@Override
		public void startup() {
			startupTime = System.nanoTime();
			out.progress(0, 0, 0);
		}
		

		public boolean enderMessage(CharSequence topic, ChannelReader payload) {
			if (topic.equals(ENDERS_TOPIC)) {
				if (payload.hasRemainingBytes()) {
					int track = payload.readPackedInt();
					long totalTime = payload.readPackedLong();
					int sendAttempts = payload.readPackedInt();
					int sendFailures = payload.readPackedInt();
					int timeouts = payload.readPackedInt();
					int responsesReceived = payload.readPackedInt();
					int responsesInvalid = payload.readPackedInt();

					//System.err.println("end of track:"+track);

					totalTimeSum += totalTime;
					sendAttemptsSum += sendAttempts;
					sendFailuresSum += sendFailures;
					timeoutsSum += timeouts;
					responsesReceivedSum += responsesReceived;
					responsesInvalidSum += responsesInvalid;
				}

				if (++enderCounter == (parallelTracks + 1)) { //we add 1 for the progress of 100%
					ElapsedTimeRecorder etr = new ElapsedTimeRecorder();
					int t = elapsedTime.length;
					while (--t >= 0) {
						etr.add(elapsedTime[t]);
					}

                    long totalMessages = parallelTracks * cyclesPerTrack;
                    long testDuration = System.nanoTime() - startupTime;
                    long serverCallsPerSecond = (1_000_000_000L * totalMessages) / testDuration;

					out.end(
							etr, testDuration, totalMessages, totalTimeSum, serverCallsPerSecond,
							sendAttemptsSum, sendFailuresSum, timeoutsSum, responsesReceivedSum, responsesInvalidSum
					);

					return cmd4.shutdown();
				}
			}
			return true;
		}
		
		public boolean progressMessage(CharSequence topic, ChannelReader payload) {
			int track = payload.readPackedInt();
			long countDown = payload.readPackedLong();
			int sendAttempts = payload.readPackedInt();
			int sendFailures = payload.readPackedInt();
			int timeouts = payload.readPackedInt();
			int responsesReceived = payload.readPackedInt();
			int responsesInvalid = payload.readPackedInt();

			this.finished[track] = cyclesPerTrack - countDown;
			this.sendAttempts[track] = sendAttempts;
			this.sendFailures[track] = sendFailures;
			this.timeouts[track] = timeouts;
			this.responsesReceived[track] = responsesReceived;
			this.responsesInvalid[track] = responsesInvalid;

			int sumFinished = 0;
			int sumSendAttempts = 0;
			int sumSendFailures = 0;
			int sumTimeouts = 0;
			int sumResponsesReceived = 0;
			int sumResponsesInvalid = 0;
			int i = parallelTracks;
			while (--i >= 0) {
				sumFinished += this.finished[i];
				sumSendAttempts += this.sendAttempts[track];
				sendFailures += this.sendFailures[track];
				sumTimeouts += this.timeouts[track];
				sumResponsesReceived += this.responsesReceived[i];
				sumResponsesInvalid += this.responsesInvalid[i];
			}

			long totalRequests = cyclesPerTrack * parallelTracks;
			int pctDone = (int)((100L * sumFinished) / totalRequests);
			assert(pctDone>=0);
			
			long now = 0;

			//updates every half a second
			if ((pctDone != lastPct && ((now = System.nanoTime()) - lastTime) > 500_000_000L)
					|| 100L == pctDone) {
			    out.progress(pctDone, sumTimeouts, sumResponsesInvalid);
			    			    
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
		private final HTTPResponseListener validator;
		private final GreenCommandChannel cmd2;
		private final HeaderWritable header;
		private final Writable writer;

		private long countDown;
		private long totalTime;
		private int sendAttempts;
		private int sendFailures;
		private int timeouts;
		private int responsesInvalid;
		private int responsesReceived;
		private boolean lastResponseOk=true;

		TrackHTTPResponseListener(GreenRuntime runtime, int track) {
			this.track = track;
			countDown = cyclesPerTrack;
			cmd3 = runtime.newCommandChannel();
			cmd3.ensureDynamicMessaging(Math.max(2+((durationNanos>0?2:1)*maxInFlight),PUB_MSGS), PUB_MSGS_SIZE);
			if (durationNanos > 0) {
				cmd3.ensureDelaySupport();
			}
		

			this.header = contentType != null ?
					new HeaderWritable() {
						@Override
						public void write(HeaderWriter writer) {
							writer.write(HTTPHeaderDefaults.CONTENT_TYPE,
									     contentType.contentType());
						}
					}				
					
					: null;
					

			this.writer = post != null ? post.get() : null;
			this.validator = validate != null ? validate.get() : null;

			this.cmd2 = runtime.newCommandChannel();
			if (post != null) {
				cmd2.ensureHTTPClientRequesting(2+maxInFlight, maxPayloadSize + 1024);
			} else {
				cmd2.ensureHTTPClientRequesting(2+maxInFlight, 0);
			}
			
		}

		public boolean callMessage(CharSequence topic, ChannelReader payload) {
			return makeCall();
		}

		private boolean makeCall() {

			long now = System.nanoTime();
			boolean wasSent;
			if (null==writer) {
				wasSent = cmd2.httpGet(session[track], route);
			} else if (header != null) {
				wasSent = cmd2.httpPost(session[track], route, header, writer);
			} else {
				wasSent = cmd2.httpPost(session[track], route, writer);
			}

			sendAttempts++;
			if (wasSent) {
				callTime[track][maxInFlightMask & inFlightHead[track]++] = now;
			}
			else {
				sendFailures++;
			}
			return wasSent;
		}

		
		@Override
		public void startup() {
			long now = System.currentTimeMillis();
			int i = maxInFlight;
			while (--i>=0) {
								
				while(!cmd3.publishTopic(CALL_TOPIC)) {
					//must publish this many to get the world moving
					Thread.yield();
					if ((System.currentTimeMillis()-now) > 10_000) {
						System.err.println("Unable to send "+maxInFlight+" messages to start up.");			
						cmd3.shutdown();
					}
				}
				//must use message to startup the system
			}
		}
	
		@Override
		public boolean responseHTTP(HTTPResponseReader reader) {

				//if false we already closed this one and need to skip this part
				if (lastResponseOk) {
					boolean connectionClosed = reader.isConnectionClosed();
					if (connectionClosed) {
						out.connectionClosed(track);
					}
		
					long duration = System.nanoTime() - callTime[track][maxInFlightMask & inFlightTail[track]++];
						
					boolean findLongCalls = false;
					if (findLongCalls && duration>20_000_000) {
						
						Appendables.appendValue(
								Appendables.appendNearestTimeUnit(System.err, duration)
						        .append(" long call detected for ")
						        ,(inFlightTail[track]-1)).append("\n");
						
					}
					
					ElapsedTimeRecorder.record(elapsedTime[track], duration);
					totalTime+=duration;
					responsesReceived++;
					if (validator != null && !validator.responseHTTP(reader)) {
						responsesInvalid++;
					}
				}
				return lastResponseOk = nextCall();

		}

		private boolean nextCall() {
			if ((0xFFFF & countDown) == 0) {
				cmd3.publishTopic(PROGRESS_TOPIC, writer-> {
					writer.writePackedInt(track);
					writer.writePackedLong(countDown);
					writer.writePackedInt(sendAttempts);
					writer.writePackedInt(sendFailures);
					writer.writePackedInt(timeouts);
					writer.writePackedInt(responsesReceived);
					writer.writePackedInt(responsesInvalid);
				});
			}

			boolean isOk = true;
			if (countDown > 0) {
				if (durationNanos > 0) {
					isOk = cmd3.delay(durationNanos);
				}
				if (isOk) {
					isOk = cmd3.publishTopic(CALL_TOPIC);
				}
			}
			else {
				if (1 == (maxInFlight+countDown)) {
					//only end after all the inFlightMessages have returned.
					isOk = cmd3.publishTopic(ENDERS_TOPIC, writer -> {
						writer.writePackedInt(track);
						writer.writePackedLong(totalTime);
						writer.writePackedInt(sendAttempts);
						writer.writePackedInt(sendFailures);
						writer.writePackedInt(timeouts);
						writer.writePackedInt(responsesReceived);
						writer.writePackedInt(responsesInvalid);
					});
					//System.err.println("publish end of "+track);
				} 
			}

			//upon failure should not count down
			if (isOk) {
				countDown--;
			}

			return isOk;
		}

		@Override
		public void timeEvent(long time, int iteration) {
			long callTimeValue = callTime[track][maxInFlightMask & inFlightTail[track]];
			if (callTimeValue != 0) {
				long duration = System.nanoTime() - callTimeValue;
				if (duration > responseTimeoutNS) {
					
					Appendables.appendNearestTimeUnit(System.out.append("Failed response detected after timeout of: "), responseTimeoutNS).append('\n');
										
					timeouts++;
					while (!nextCall()) {//must run now.
						Thread.yield();
					}
				}
			}
		}
	}
}
