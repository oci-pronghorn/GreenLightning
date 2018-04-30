package com.ociweb.gl.test;

import java.util.Random;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.GreenAppParallel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPRequestService;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.HeaderWritable;
import com.ociweb.gl.api.HeaderWriter;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;
import com.ociweb.pronghorn.util.Appendables;

public class ParallelClientLoadTester implements GreenAppParallel {
	
	private final static Logger logger = LoggerFactory.getLogger(ParallelClientLoadTester.class);
	
    private final String route;
	private final boolean insecureClient;
    private final int parallelTracks;
    private final long cyclesPerTrack;
    private final Integer telemetryPort;
    private final String telemetryHost;
    private final Long rate;
    private final int inFlightHTTPs;
    private final int maxInFlight;
    private final int maxInFlightMask;
    private int warmupCount = 20_000;

	public static long LOG_LATENCY_LIMIT =  2_000_000_000L; //3_000_000_000L;//3sec	
    
	private final ParallelClientLoadTesterOutput out;
	private final Supplier<Writable> post;
    private final int maxPayloadSize;
    private final HTTPContentTypeDefaults contentType;
	private final Supplier<HTTPResponseListener> validate;

    private final ClientHostPortInstance[] session;
    private final ElapsedTimeRecorder[] elapsedTime;

    private int trackId = 0;
	private long startupTime;
	private long durationNanos = 0;

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
            new DefaultParallelClientLoadTesterOutput(System.out));
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
            new DefaultParallelClientLoadTesterOutput(System.out));
	}

	public ParallelClientLoadTester(
			ParallelClientLoadTesterConfig config,
			ParallelClientLoadTesterPayload payload) {
		this(config, payload, new DefaultParallelClientLoadTesterOutput(config.target));
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

        this.cyclesPerTrack = config.cyclesPerTrack;
        this.rate = config.cycleRate;
        this.warmupCount = config.warmup;
        
		//bit  size   mask   pos
		//0     1      0      0
		//1     2      1      0,1
		//2     4      3      0,1,2,3
        //add one for close message
		this.maxInFlight = 1<< config.simultaneousRequestsPerTrackBits+1;
		this.inFlightHTTPs = 1<<config.simultaneousRequestsPerTrackBits;
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
		private final PubSubService cmd4;

		private final long[] finished = new long[parallelTracks];
		//private final long[] sendAttempts = new long[parallelTracks];
		//private final long[] sendFailures = new long[parallelTracks];
		private final long[] timeouts = new long[parallelTracks];
		//private final long[] responsesReceived = new long[parallelTracks];
		private final long[] responsesInvalid = new long[parallelTracks];

		private long totalTimeSum;
		private long sendAttemptsSum;
		private long sendFailuresSum;
		private long timeoutsSum;
		private long responsesReceivedSum;
		private long responsesInvalidSum;

		private int lastPercent = 0;
		private long lastTime = 0;
		private int enderCounter;

		Progress(GreenRuntime runtime) {
			this.cmd4 = runtime.newCommandChannel().newPubSubService(Math.max(PUB_MSGS, maxInFlight), PUB_MSGS_SIZE);
		}

		@Override
		public void startup() {
			startupTime = System.nanoTime();
			out.progress(0, 0, 0);
		}

		boolean enderMessage(CharSequence topic, ChannelReader payload) {
			if (topic.equals(ENDERS_TOPIC)) {
				if (payload.hasRemainingBytes()) {
					int track = payload.readPackedInt();
					long totalTime = payload.readPackedLong();
					long sendAttempts = payload.readPackedLong();
					long sendFailures = payload.readPackedLong();
					long timeouts = payload.readPackedLong();
					long responsesReceived = payload.readPackedLong();
					long responsesInvalid = payload.readPackedLong();

					totalTimeSum += totalTime;
					sendAttemptsSum += sendAttempts;
					sendFailuresSum += sendFailures;
					timeoutsSum += timeouts;
					responsesReceivedSum += responsesReceived;
					responsesInvalidSum += responsesInvalid;
					
					//logger.info("Finished track {} remaining {}",track,(parallelTracks-enderCounter));
				}
				
				
				if (++enderCounter == (parallelTracks + 1)) { //we add 1 for the progress of 100%
					ElapsedTimeRecorder etr = new ElapsedTimeRecorder();
					int t = elapsedTime.length;
					while (--t >= 0) {
						etr.add(elapsedTime[t]);
					}
					
					//NOTE: these counts do include warmup
                    long totalMessages = parallelTracks * cyclesPerTrack;
                    long testDuration = System.nanoTime() - startupTime;
                    long serverCallsPerSecond = (1_000_000_000L * totalMessages) / testDuration;

					out.end(
							etr, testDuration, totalMessages, totalTimeSum, serverCallsPerSecond,
							sendAttemptsSum, sendFailuresSum, timeoutsSum, responsesReceivedSum, responsesInvalidSum
					);
					//logger.info("shutting down load test");
					return cmd4.shutdown();
				}
			}
			return true;
		}
		
		boolean progressMessage(CharSequence topic, ChannelReader payload) {
			int track = payload.readPackedInt();
			long countDown = payload.readPackedLong();
			//long sendAttempts = payload.readPackedInt();
			//long sendFailures = payload.readPackedInt();
			long timeouts = payload.readPackedInt();
			//long responsesReceived = payload.readPackedInt();
			long responsesInvalid = payload.readPackedInt();

			this.finished[track] = cyclesPerTrack - countDown;
			//this.sendAttempts[track] = sendAttempts;
			//this.sendFailures[track] = sendFailures;
			this.timeouts[track] = timeouts;
			//this.responsesReceived[track] = responsesReceived;
			this.responsesInvalid[track] = responsesInvalid;

			long sumFinished = 0;
			//long sumSendAttempts = 0;
			//long sumSendFailures = 0;
			long sumTimeouts = 0;
			//long sumResponsesReceived = 0;
			long sumResponsesInvalid = 0;
			int i = parallelTracks;
			while (--i >= 0) {
				sumFinished += this.finished[i];
				//sumSendAttempts += this.sendAttempts[track];
				//sumSendFailures += this.sendFailures[track];
				sumTimeouts += this.timeouts[track];
				//sumResponsesReceived += this.responsesReceived[i];
				sumResponsesInvalid += this.responsesInvalid[i];
			}

			long totalRequests = cyclesPerTrack * parallelTracks;
			int percentDone = (int)((100L * sumFinished) / totalRequests);
			assert(percentDone>=0);

			long now = 0;

			//updates every 2 seconds
			if ((percentDone != lastPercent && ((now = System.nanoTime()) - lastTime) > 2_000_000_000L)
					|| 100L == percentDone) {
			    out.progress(percentDone, sumTimeouts, sumResponsesInvalid);

				lastTime = now;
				lastPercent = percentDone;
			}

			if (100 == percentDone) {
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


	private class TrackHTTPResponseListener implements HTTPResponseListener, StartupListener, PubSubMethodListener {
		private final PubSubService cmd3;
		private final int track;
		private final HTTPResponseListener validator;
		private final HTTPRequestService cmd2;
		private final HeaderWritable header;
		private final Writable writer;

		private long countDown;
		private long totalTime;
		private long callCounter;
		private long sendFailures;
		private long timeouts;
		private long responsesInvalid;
		private long responsesReceived;
		private boolean lastResponseOk=true;
		private final int cookieSize = 10;//00;

		private final String largeCookie = buildLargeCookie(cookieSize);
		
		TrackHTTPResponseListener(GreenRuntime runtime, int track) {
			this.track = track;
			countDown = cyclesPerTrack;
			cmd3 = runtime.newCommandChannel().newPubSubService(Math.max(2+((durationNanos>0?2:1)*maxInFlight),PUB_MSGS), PUB_MSGS_SIZE);

			this.header = contentType != null ?
					new HeaderWritable() {
						@Override
						public void write(HeaderWriter writer) {
							writer.write(HTTPHeaderDefaults.COOKIE, 
									largeCookie);
							
							writer.write(HTTPHeaderDefaults.CONTENT_TYPE,
									         contentType.contentType());
						}
					}				
					
					: null;

			this.writer = post != null ? post.get() : null;
			this.validator = validate != null ? validate.get() : null;

			this.cmd2 = runtime.newCommandChannel().newHTTPClientService(
					2+maxInFlight, post!=null ? maxPayloadSize + 1024 : 0);
			
		}

		private String buildLargeCookie(int size) {
			StringBuilder builder  = new StringBuilder();
			Random r = new Random();
			byte[] target = new byte[size];
			r.nextBytes(target);
			
			Appendables.appendBase64Encoded(builder, target, 0, target.length, Integer.MAX_VALUE);
			
			return builder.toString();
			
		}

		boolean callMessage(CharSequence topic, ChannelReader payload) {
			return makeCall();
		}

		private boolean makeCall() {
			long now = System.nanoTime();
			callCounter++;			
			boolean wasSent = doHTTPCall();			
			if (wasSent) {
				callTime[track][maxInFlightMask & inFlightHead[track]++] = now;
			} else {
				sendFailures++;
			}
			return wasSent;
		}

		private final HeaderWritable writeClose = new HeaderWritable() {
			@Override
			public void write(HeaderWriter writer) {
				writer.write(HTTPHeaderDefaults.CONNECTION, "close");
			}		
		};
		
		private boolean doHTTPCall() {
			if (cmd2.hasRoomFor(2)) {
				boolean wasSent;
				if (null==writer) {
					wasSent = httpGet();
				} else if (header != null) {
					wasSent = httpPostWithHeader();
				} else {
					wasSent = httpPost();
				}
				return wasSent;
			} else {
				return false;
			}
			
		}

		private boolean httpPost() {
			
			boolean wasSent;
			if (callCounter<cyclesPerTrack) {
				wasSent = cmd2.httpPost(session[track], route, writer);
			} else {
				wasSent = cmd2.httpPost(session[track], route, writeClose, writer);
				cmd2.httpClose(session[track]);
			}
			return wasSent;
		}

		private boolean httpGet() {
			boolean wasSent;
			
			//Hack test
			//if we close every call this should work fine but it does not
			//this can be either side causing the issue and must be resolved.
			
			
			if (callCounter<cyclesPerTrack) {
				wasSent = cmd2.httpGet(session[track], route);					
			} else {
				wasSent = cmd2.httpGet(session[track], route, writeClose);	
				//add boolean for easier time of this.. :TODO: API change.
				cmd2.httpClose(session[track]);//we wrote the close header must follow with close.
			}
			return wasSent;
		}

		private boolean httpPostWithHeader() {
			boolean wasSent;
		
			
			if (callCounter<cyclesPerTrack) {
		
				
				wasSent = cmd2.httpPost(session[track], route, header, writer);
			} else {

				HeaderWritable allHeaders = new HeaderWritable() {
					@Override
					public void write(HeaderWriter writer) {
						header.write(writer);
						writer.write(HTTPHeaderDefaults.CONNECTION, "close");
					}		
				};
				wasSent = cmd2.httpPost(session[track], route, allHeaders, writer);
				cmd2.httpClose(session[track]);
			}
			return wasSent;
		}
		
		@Override
		public void startup() {
			long now = System.currentTimeMillis();
			int i = inFlightHTTPs;
			while (--i>=0) {
								
				while(!cmd3.publishTopic(CALL_TOPIC)) {
					//must publish this many to get the world moving
					Thread.yield();
					if ((System.currentTimeMillis()-now) > 10_000) {
						out.failedToStart(inFlightHTTPs);
						cmd3.shutdown();
					}
				}
				//must use message to startup the system
			}
			countDown--; //we launched 1 cycle for this track on startup.
		}
	
		@Override
		public boolean responseHTTP(HTTPResponseReader reader) {
			
			if (reader.isConnectionClosed()) {
				
				//server just axed connection and we got no notice..
				out.connectionClosed(track);
				
				if (inFlightHead[track]>inFlightTail[track]) {
					
					int totalMissing = inFlightHead[track]-inFlightTail[track];
					timeouts+=totalMissing;
					
					logger.info("Connection closed, Expecting {} responses which will never arrive, resending http call(s)",totalMissing);
					callCounter-=totalMissing;
					//we must re-request the call
					//keep the clock rolling since this is a penalty against the server
					int i = totalMissing;
					while (--i>=0) {
						++callCounter;
						boolean ok = doHTTPCall();
						if (!ok) {
							throw new RuntimeException("internal error, channels must be large enough to hold backed up reqeusts.");
						}
					}
				}				
				
				return true;
			}

			//if false we already closed this one and need to skip this part
			if (lastResponseOk) {
				if (!reader.isEndOfResponse()) {
					return true;//just toss all the early chunks we only want the very end.
				}
				

				long sentTime = callTime[track][maxInFlightMask & inFlightTail[track]++];
				long arrivalTime = System.nanoTime();
				long duration = arrivalTime - sentTime;
								
				//only done once to ensure the code remains the same
				if (responsesReceived == (warmupCount-1)) {
					//clear all values we are not doing the real deal
					System.gc();//still in warmup so GC now quick
					totalTime = 0;//reset
					ElapsedTimeRecorder.clear(elapsedTime[track]);
					out.finishedWarmup();
					
				}
							
				//only log after warmup when the world is stable.
				if (duration>LOG_LATENCY_LIMIT) {
					long now = System.currentTimeMillis();
					long start = now - (duration/1_000_000);

					out.longCallDetected(track, duration, now, start);
				}
				
				ElapsedTimeRecorder.record(elapsedTime[track], duration);
				totalTime+=duration;

				responsesReceived++;
				
				//only checks if valid when the entire message is present				
				if (validator != null
					&& reader.isBeginningOfResponse() 
					&& reader.isEndOfResponse()	
					&& !validator.responseHTTP(reader)) {
					responsesInvalid++;
				} 
			}
			return lastResponseOk = nextCall();
		}

		private boolean nextCall() {
			if ((0x1FFFF & countDown) == 0) {
				cmd3.publishTopic(PROGRESS_TOPIC, writer-> {
					writer.writePackedInt(track);
					writer.writePackedLong(countDown);
					//writer.writePackedLong(sendAttempts);
					//writer.writePackedLong(sendFailures);
					writer.writePackedLong(timeouts);
					//writer.writePackedLong(responsesReceived);
					writer.writePackedLong(responsesInvalid);
				});
			}

			boolean isOk = true;
			if (countDown >= inFlightHTTPs) { //others are still in flight
				if (durationNanos > 0) {
					isOk = cmd3.delay(durationNanos);
				}
				if (isOk) {
					isOk = cmd3.publishTopic(CALL_TOPIC);
				}
			}
			else {
				if (0==countDown) {
					
					logger.info("finished track {}",track);
					
					//only end after all the inFlightMessages have returned.
					isOk = cmd3.publishTopic(ENDERS_TOPIC, writer -> {
						writer.writePackedInt(track);
						writer.writePackedLong(totalTime);
						writer.writePackedLong(callCounter);
						writer.writePackedLong(sendFailures);
						writer.writePackedLong(timeouts);
						writer.writePackedLong(responsesReceived);
						writer.writePackedLong(responsesInvalid);
					});
				}
			}

			//upon failure should not count down
			if (isOk) {
				countDown--;
			}

			return isOk;
		}

	}
}
