package com.ociweb.gl.test;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.DelayService;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPClientConfig;
import com.ociweb.gl.api.HTTPRequestService;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.PubSubFixedTopicService;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.gl.api.PubSubService;
import com.ociweb.gl.api.StartupListener;
import com.ociweb.gl.api.TimeListener;
import com.ociweb.gl.api.Writable;
import com.ociweb.pronghorn.network.TLSCerts;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.http.HeaderWritable;
import com.ociweb.pronghorn.network.http.HeaderWriter;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.ChannelWriter;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;
import com.ociweb.pronghorn.util.Appendables;

public class ParallelClientLoadTester implements GreenApp {
	
	private final static Logger logger = LoggerFactory.getLogger(ParallelClientLoadTester.class);
	
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
    private final int[] logCount;
	public static long LOG_LATENCY_LIMIT =  40_000_000_000L; //40sec	
    
	private final ParallelClientLoadTesterOutput out;

    private final int maxPayloadSize;
    private final byte[] contentType;
	private final ValidatorFactory validator;

	private final int sessionCount = 1;
    private final ClientHostPortInstance[][] session;
    
    private final ElapsedTimeRecorder[] elapsedTime;
	
	private final HeaderWritableFactory header;		
	private final WritableFactory writer;
    private final RouteFactory route;
    
    
    private int trackId = 0;
	private long startupTime;
	private long durationNanos = 0;

	private final long[][] callTime;
	private final int[] inFlightHead;
	private final int[] inFlightTail;

	private static final String RESPONDER_NAME = "TestResponder";
	private static final String CALL_TOPIC     = "makeCall";	
	private static final String PROGRESS_NAME  = "TestProgessor";
	private static final String ENDERS_TOPIC   = "end";
	private static final String PROGRESS_TOPIC = "progress";
	
	private static final int PUB_MSGS      = 8000;
	private static final int PUB_MSGS_SIZE = 48;
	private long minFinished;

	private String host;

	private int port;

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

        this.telemetryPort = config.telemetryPort;
        this.telemetryHost = config.telemetryHost;
		this.parallelTracks = config.parallelTracks;
		this.durationNanos = config.durationNanos;
		this.insecureClient = config.insecureClient;
		this.host = config.host;
		this.port = config.port;

		this.logCount = new int[parallelTracks];
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

		this.session = new ClientHostPortInstance[parallelTracks][sessionCount];
		this.elapsedTime = new ElapsedTimeRecorder[parallelTracks];

        this.contentType = null==payload ? null : payload.contentType.getBytes();
        this.maxPayloadSize = null==payload ? 256 : payload.maxPayloadSize;
		
		
		this.validator = null==payload? null : payload.validator;

		this.out = out;
		
		////////////////////////
		////route factory and payload factory
        this.route = config.route;
        this.writer = null==payload? null : payload.post;
        if (this.writer != null) {
        	if (null == this.contentType) {
        		throw new UnsupportedOperationException("Content type is required for payload");
        	}
        }
		
		///////////////////////////////////
		///setup header writable
		///////////////////////////////////
		
		if (contentType == null) {
			header = null;
		} else {
		
			final int cookieSize = 10;//00;
			final byte[] largeCookie = buildLargeCookie(cookieSize).getBytes();
			
			final HeaderWritable tempHeader = 
					new HeaderWritable() {
				@Override
				public void write(HeaderWriter writer) {
					
					writer.writeUTF8(HTTPHeaderDefaults.COOKIE, 
							largeCookie);
					
					writer.writeUTF8(HTTPHeaderDefaults.CONTENT_TYPE,
							contentType);
				}
			};
			
			
			header = new HeaderWritableFactory() {			
				@Override
				public HeaderWritable headerWritable(long callInstance) {
					return tempHeader;
				}
			};
		}
		
		
	}


	private static String buildLargeCookie(int size) {
		StringBuilder builder  = new StringBuilder();
		Random r = new Random();
		byte[] target = new byte[size];
		r.nextBytes(target);
		
		Appendables.appendBase64Encoded(builder, target, 0, target.length, Integer.MAX_VALUE);
		
		return builder.toString();
		
	}
	
	@Override
	public void declareConfiguration(Builder builder) {
		
		HTTPClientConfig clientConfig;
		if (insecureClient) {
			clientConfig = builder.useInsecureNetClient();
		} else {
			clientConfig = builder.useNetClient(TLSCerts.define());
		}
		//clientConfig.setUnwrapCount(4);
		
		int i = parallelTracks;
		while (--i>=0) {
			session[i][0] = clientConfig.newHTTPSession(host, port).finish();
			elapsedTime[i] = new ElapsedTimeRecorder();
		}

		if (telemetryPort != null) {

			if (null == this.telemetryHost) {
				builder.enableTelemetry(telemetryPort);
			}
			else {
				builder.enableTelemetry(telemetryHost, telemetryPort);
			}

		}
		builder.parallelTracks(session.length, this::declareParallelBehavior);
		
		builder.setTimerPulseRate(20_000); //check for progress every 20 seconds
		
		if (rate != null) {
			builder.setDefaultRate(rate);
		}

		builder.defineUnScopedTopic(ENDERS_TOPIC);
		builder.defineUnScopedTopic(PROGRESS_TOPIC);	
		
		//in order so slow the rate we require this topic to public, this way the cop is involved
		if (durationNanos>0) {
			builder.definePublicTopics(CALL_TOPIC);
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
		private final PubSubFixedTopicService cmd4;

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
		private boolean done = false;

		Progress(GreenRuntime runtime) {
			this.cmd4 = runtime.newCommandChannel().newPubSubService(ENDERS_TOPIC,Math.max(PUB_MSGS, maxInFlight), PUB_MSGS_SIZE);
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
					logger.info("\nshutting down load test, all tracks have finished");
					cmd4.requestShutdown();
					return true;
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
			int trackId = parallelTracks;
			long minFin = Long.MAX_VALUE;
			while (--trackId >= 0) {
				
				long f = this.finished[trackId];
				if (f < minFin) {
					minFin = f;
				}
				
				sumFinished += f;
				//sumSendAttempts += this.sendAttempts[track];
				//sumSendFailures += this.sendFailures[track];
				sumTimeouts += this.timeouts[track];
				//sumResponsesReceived += this.responsesReceived[i];
				sumResponsesInvalid += this.responsesInvalid[trackId];
			}
			minFinished = minFin;

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
			
			if (100 == percentDone && (!done)) {
				logger.trace("received {}% of requests on all tracks",percentDone);
				boolean result = cmd4.publishTopic();	
				if (result) {
					done = true;					
				}
				return result;
			}
			return true;
		}
	}

	public void declareParallelBehavior(GreenRuntime runtime) {
		final int track = trackId++;

		TrackHTTPResponseListener responder = new TrackHTTPResponseListener(runtime, track);
		runtime.registerListener(RESPONDER_NAME, responder)
					.addSubscription(CALL_TOPIC, responder::callMessage) //TODO: this object has a connection from pub sub that does nothing..
					.acceptHostResponses(session[track][0]);
	}


	private class TrackHTTPResponseListener implements HTTPResponseListener, TimeListener, StartupListener, PubSubMethodListener {
		private final PubSubFixedTopicService callService;
		private DelayService delayService;
		private final PubSubService pubService;
		
		private final int track;
		private final HTTPRequestService httpClientService;   

		private long countDown;
		private long totalTime;
		private long callInstanceCounter;
		private long sendFailures;
		private long timeouts;
		private long responsesInvalid;
		private long responsesReceived;
		private boolean lastResponseOk=true;
		
		private Writable writeWrapper;
		
		TrackHTTPResponseListener(GreenRuntime runtime, int track) {
			this.track = track;
			countDown = cyclesPerTrack;
			
			GreenCommandChannel newCommandChannel = runtime.newCommandChannel();
			callService = newCommandChannel.newPubSubService(CALL_TOPIC,
																Math.max(2+((durationNanos>0?2:1)*maxInFlight), PUB_MSGS), 
																PUB_MSGS_SIZE);
			pubService = newCommandChannel.newPubSubService(Math.max(2+((durationNanos>0?2:1)*maxInFlight),PUB_MSGS), PUB_MSGS_SIZE);
			
			if (durationNanos > 0) {//this service requires cops but is only needed when delay is enabled. to save resources only create as needed.
				delayService = newCommandChannel.newDelayService();
			}
			int queueLength = 2*Math.min(2+maxInFlight,1<<10);
						
			httpClientService = runtime.newCommandChannel()
					.newHTTPClientService(
							queueLength //keeps from having giant pipes, its 2x in-case we must re-send 	
					, writer!=null ? maxPayloadSize + (1<<12) : 0);

			writeWrapper = new Writable() {
				@Override
				public void write(ChannelWriter w) {
					writer.payloadWriter(callInstanceCounter, w);
				}				
			};
			
		}


		private int x = 0;
		
		boolean callMessage(CharSequence topic, ChannelReader payload) {

			//slow down the tracks far ahead of the others
			long gap = (cyclesPerTrack-countDown) - minFinished;
			if (gap > 2000) {
				if (0!=(15&x++)) {
					//if non zero we delay making this call
					return false;
				}
			}
			
			return makeCall();
		}

		private boolean makeCall() {
			if (session[track][0]!=null) {
				long now = System.nanoTime();
				boolean wasSent = doHTTPCall();			
				if (wasSent) {
					callInstanceCounter++;//must happen after call			
					callTime[track][maxInFlightMask & inFlightHead[track]++] = now;
				} else {
					sendFailures++;
				}
				return wasSent;
			} else {
				//old data, return true to mark as done
				return true;
			}
		}
		
		private boolean doHTTPCall() {		
			if (null==writer) {
				return (header != null) ? httpGetWithHeader() : httpGet();   
			} else { 
				return (header != null) ? httpPostWithHeader() : httpPost();
			}
		}
		
		//TODO: add support for N sessions per track, this will allow us to simulate many clients while using fewer threads.
		
		
		private boolean httpPost() {
			return httpClientService.httpPost(session[track][0], route.route(callInstanceCounter), writeWrapper);
		}

		private boolean httpGet() {
			return httpClientService.httpGet(session[track][0], route.route(callInstanceCounter));	
		}

		private boolean httpGetWithHeader() {
			return httpClientService.httpGet(session[track][0], route.route(callInstanceCounter), header.headerWritable(callInstanceCounter));		
		}
		
		private boolean httpPostWithHeader() {
			ClientHostPortInstance s = session[track][0];
			if (null!=s) {
				return httpClientService.httpPost(s, route.route(callInstanceCounter), header.headerWritable(callInstanceCounter), writeWrapper);

			} else {
				//NOTE: must investigate this, we are getting TOO many re-sends? after a close??
				logger.info("\nold reqeusts where requested after completion.");
				return true;
			}
		}
		
		@Override
		public void startup() {
			long now = System.currentTimeMillis();
			int i = inFlightHTTPs;
			while (--i>=0) {
								
				while(!callService.publishTopic()) {
					//must publish this many to get the world moving
					Thread.yield();
					if ((System.currentTimeMillis()-now) > 10_000) {
						out.failedToStart(inFlightHTTPs);
						callService.requestShutdown();
					}
				}
				//must use message to startup the system
			}
			countDown--; //we launched 1 cycle for this track on startup.
		}
	
		@Override
		public boolean responseHTTP(HTTPResponseReader reader) {
		
			if (reader.isConnectionClosed()) {
				//server or internal subsystem just axed connection and we got no notice..
							
				if (inFlightHead[track]>inFlightTail[track]) {
					
					//NOTE: we have already countDown these however they never arrived.
					int totalMissing = inFlightHead[track]-inFlightTail[track];
					
					if (!httpClientService.hasRoomFor(totalMissing)) {
						return false;//try again later
					}
					
					timeouts+=totalMissing;
					
					
					//logger.info("\nConnection {} closed, expecting {} responses which will never arrive, resending http call(s)",reader.connectionId(),totalMissing);
										
					//we must re-request the call
					//keep the clock rolling since this is a penalty against the server
					callInstanceCounter-=totalMissing;
					int i = totalMissing;
					while (--i>=0) {
						boolean ok = doHTTPCall();
						assert(ok) : "internal error, channels must be large enough to hold backed up reqeusts.";
						callInstanceCounter++;//must happen after call
					}
				}	
				out.connectionClosed(track);
				
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
				if ((duration > LOG_LATENCY_LIMIT) && (sentTime > 0)) {
					long now = System.currentTimeMillis();
					long start = now - (duration/1_000_000);
					if (Integer.numberOfLeadingZeros(logCount[track]) != Integer.numberOfLeadingZeros(++logCount[track])) {
						out.longCallDetected(track, duration, now, start);
					}
				}
				
				ElapsedTimeRecorder.record(elapsedTime[track], duration);
				totalTime+=duration;
				
				//only checks if valid when the entire message is present				
				if (validator != null
					&& reader.isBeginningOfResponse() 
					&& reader.isEndOfResponse()	
					&& !validator.validate(responsesReceived, reader)) {
					responsesInvalid++;
				} 				
				responsesReceived++;
			}
			return lastResponseOk = nextCall();
		}

		private boolean nextCall() {
			if ((0x1FFFF & countDown) == 0) {
				pubService.publishTopic(PROGRESS_TOPIC, writer-> {
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
					isOk = delayService.delay(durationNanos);
				}
				if (isOk) {
					isOk = callService.publishTopic();
				}
			}
		
			if (0==countDown) {
				boolean clean = pubService.publishTopic(PROGRESS_TOPIC, writer-> {
					writer.writePackedInt(track);
					writer.writePackedLong(countDown);
					//writer.writePackedLong(sendAttempts);
					//writer.writePackedLong(sendFailures);
					writer.writePackedLong(timeouts);
					//writer.writePackedLong(responsesReceived);
					writer.writePackedLong(responsesInvalid);
				});
				//only end after all the inFlightMessages have returned.
				clean &= pubService.publishTopic(ENDERS_TOPIC, writer -> {
					writer.writePackedInt(track);
					writer.writePackedLong(totalTime);
					writer.writePackedLong(callInstanceCounter);
					writer.writePackedLong(sendFailures);
					writer.writePackedLong(timeouts);
					writer.writePackedLong(responsesReceived);
					writer.writePackedLong(responsesInvalid);
				});
				assert(clean) :"unable to end test clean";
				
				//System.out.println("publish enders clean:"+clean+"  "+track+" rec  "+responsesReceived+" "+callCounter);
				
				ClientHostPortInstance s = session[track][0];
				if (s!=null) {
					//NOTE: only safe place to close the connection.
					
					if (insecureClient) {	//TODO: this close is too early for TLS? shared engine instance?				
						httpClientService.httpClose(s); 
					}
					session[track][0]=null;
				}
				--countDown;//ensure it goes negative so we do not finish more than once, this can happen when connections have been timed out and new requests sent.
				
			} else if (isOk) { //upon failure should not count down
				--countDown;
			} else {
			    logger.warn("Unable to send request, will try again.");
			}

			return isOk;
		}

		long lastProgressCheck = -1;
		
		@Override
		public void timeEvent(long time, int iteration) {
			
			if (lastProgressCheck != callInstanceCounter) {
				lastProgressCheck = callInstanceCounter;
			} else {			
				if (callInstanceCounter!=cyclesPerTrack) {
					StringBuilder builder = new StringBuilder(); 
					Appendables.appendValue(Appendables.appendValue(Appendables.appendValue(
							Appendables.appendEpochTime(builder.append("\n"), time)
							           .append(" status for track: "),track), " progress:", callInstanceCounter), "/", cyclesPerTrack,"  No progress has been made! Has the server stopped responding?\n");
					System.out.print(builder);
				}
			}
		}

	}
}
