package com.ociweb.gl.test;

import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.DelayService;
import com.ociweb.gl.api.GreenApp;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.GreenFramework;
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
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.pronghorn.network.ClientAbandonConnectionScanner;
import com.ociweb.pronghorn.network.ClientConnection;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.ClientSocketReaderStage;
import com.ociweb.pronghorn.network.TLSCerts;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.http.HeaderWritable;
import com.ociweb.pronghorn.network.http.HeaderWriter;
import com.ociweb.pronghorn.pipe.ChannelReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.ElapsedTimeRecorder;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.util.Appendables;

public class ParallelClientLoadTester implements GreenApp {
	
	//if abandonSlowConnections is on we check for progress every 20 seconds else every 2 minutes
	private static final int PROGRESS_CHECK_RATE = ClientSocketReaderStage.abandonSlowConnections? 20_000 : 120_000 ;

	final static Logger logger = LoggerFactory.getLogger(ParallelClientLoadTester.class);
	
	private final boolean insecureClient;
    final int parallelTracks;
    final long cyclesPerTrack;
    private final Integer telemetryPort;
    private final String telemetryHost;
    private final Long rate;
    private final int inFlightHTTPs;
    final int maxInFlight;
    private final int maxInFlightMask;
    private int warmupCount = 20_000;
    private final int[] logCount;
	public static long LOG_LATENCY_LIMIT =  ClientSocketReaderStage.abandonSlowConnections ? 40_000_000_000L : 120_000_000_000L; //40sec or 2 min	
    
	final ParallelClientLoadTesterOutput out;
	private final GraphManager graphUnderTest;
    private final int maxPayloadSize;
    private final byte[] contentType;
	private final ValidatorFactory validator;

	public final int sessionCount;
	
    private final ClientHostPortInstance[][] session;
    
    final ElapsedTimeRecorder[] elapsedTime;
	
	private final HeaderWritableFactory header;		
	private final WritableFactory writer;
    private final RouteFactory route;
    
    
    private int trackId = 0;
	long startupTime;
	private long durationNanos = 0;

	private final long[][] callTime;
	private final int[] inFlightHead;
	private final int[] inFlightTail;

	private static final String RESPONDER_NAME = "TestResponder";
	private static final String CALL_TOPIC     = "makeCall";	
	private static final String PROGRESS_NAME  = "TestProgessor";
	static final String ENDERS_TOPIC   = "end";
	private static final String PROGRESS_TOPIC = "progress";
	
	static final int PUB_MSGS      = 8000;
	static final int PUB_MSGS_SIZE = 48;
	long minFinished;

	private int[] sessionLookup;
	
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
		this.sessionCount = config.sessionsPerTrack;

		this.graphUnderTest = config.graphUnderTest;
		this.logCount = new int[parallelTracks];
        this.cyclesPerTrack = config.cyclesPerTrack;
        this.rate = config.cycleRate;
        this.warmupCount = config.warmup;
                
		//bit  size   mask   pos
		//0     1      0      0
		//1     2      1      0,1
		//2     4      3      0,1,2,3
        //add one for close message
		this.maxInFlight = (1<< config.simultaneousRequestsPerTrackBits);
		this.inFlightHTTPs = 1<<config.simultaneousRequestsPerTrackBits;
		this.maxInFlightMask = maxInFlight-1;
		
		this.callTime = new long[parallelTracks][maxInFlight];
		this.inFlightHead = new int[parallelTracks];
		this.inFlightTail = new int[parallelTracks];
		this.sessionLookup = new int[ClientHostPortInstance.getSessionCount() + (parallelTracks*(sessionCount+1))];

		this.session = new ClientHostPortInstance[parallelTracks][sessionCount];
		this.elapsedTime = new ElapsedTimeRecorder[parallelTracks];

        this.contentType = null==payload ? null : payload.contentType.getBytes();
        this.maxPayloadSize = null==payload ? 180 : payload.maxPayloadSize;
		
		
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
	public void declareConfiguration(GreenFramework builder) {
		
		//Keep in case this is not zero.
		((BuilderImpl)builder).sessionCountBase = ClientHostPortInstance.getSessionCount();
		
		HTTPClientConfig clientConfig;
		if (insecureClient) {
			clientConfig = builder.useInsecureNetClient();
		} else {
			clientConfig = builder.useNetClient(TLSCerts.define());
		}

		int base = ClientHostPortInstance.getSessionCount();
		int i = parallelTracks;
		while (--i>=0) {
			int s = session[i].length;
			while (--s >= 0) {
				ClientHostPortInstance conSess = clientConfig.newHTTPSession(host, port).finish();
				session[i][s] = conSess;
				sessionLookup[conSess.sessionId] = s;				
			}
			elapsedTime[i] = new ElapsedTimeRecorder();
		}

		//when we have massive connections this is a very small number
		//HIGHVOLUME test
		int maxQueuedResponsesFromServer = Math.max(8,maxInFlight/sessionCount);
		
		clientConfig.setResponseQueueLength(maxQueuedResponsesFromServer);
		
		clientConfig.setMaxSimultaniousRequests(parallelTracks*sessionCount);//each has sequential maxInFlight
			
		//clientConfig.setUnwrapCount(2); //hack test
		
		int maxExpectedMessageSizeFromServer = 128; //TODO: add support to configure this
		clientConfig.setMaxResponseSize(maxExpectedMessageSizeFromServer);
		
		System.out.println("Test is running with "+(ClientHostPortInstance.getSessionCount()-base)+" total connections");
		
		if (telemetryPort != null) {

			if (null == this.telemetryHost) {
				builder.enableTelemetry(telemetryPort);
			} else {
				builder.enableTelemetry(telemetryHost, telemetryPort);
			}

		}
		builder.parallelTracks(session.length, this::declareParallelBehavior);
		
		builder.setTimerPulseRate(PROGRESS_CHECK_RATE); 
		
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
		LoadTestProgress progress = new LoadTestProgress(this, runtime);
		runtime.registerListener(PROGRESS_NAME, progress)
				.SLALatencyNS(200_000_000)//due to use of System out and shutdown this is allowed more time
				.addSubscription(ENDERS_TOPIC, progress::enderMessage)
		        .addSubscription(PROGRESS_TOPIC, progress::progressMessage);
	}

	public void declareParallelBehavior(GreenRuntime runtime) {
		final int track = trackId++;

		TrackHTTPResponseListener responder = new TrackHTTPResponseListener(runtime, track);
		runtime.registerListener(RESPONDER_NAME, responder)
					.addSubscription(CALL_TOPIC, responder::callMessage)
					.acceptHostResponses(session[track]);
	}


	private class TrackHTTPResponseListener implements HTTPResponseListener, TimeListener, StartupListener, PubSubMethodListener {
		
		private final PubSubFixedTopicService callService;
		private DelayService delayService;
		private final PubSubService pubService;
		
		private final int track;
		
		private final HTTPRequestService[] httpClientService;   
		private final int clientServiceMask;
		
		private long totalTime;
		private long sendFailures;
		private long timeouts;
		private long responsesInvalid;
	
		private TrackHTTPResponseListenerData[] connectionSessionData = new TrackHTTPResponseListenerData[sessionCount];

		private BuilderImpl<?> builder; //needed for debug of halted jobs.
		
		TrackHTTPResponseListener(GreenRuntime runtime, int track) {
			this.track = track;
			
			final int totalSessionsPerTrack = connectionSessionData.length;;
			int s = totalSessionsPerTrack;
			while (--s>=0) {
				connectionSessionData[s] = new TrackHTTPResponseListenerData();
				connectionSessionData[s].waitingCountDown = cyclesPerTrack;
			}
			
			builder = runtime.builder;
			
			GreenCommandChannel newCommandChannel = runtime.newCommandChannel();
			callService = newCommandChannel.newPubSubService(CALL_TOPIC, maxInFlight*sessionCount, PUB_MSGS_SIZE);
			pubService = newCommandChannel.newPubSubService(parallelTracks*4, PUB_MSGS_SIZE);
			
			if (durationNanos > 0) {//this service requires cops but is only needed when delay is enabled. to save resources only create as needed.
				delayService = newCommandChannel.newDelayService();
			}
			
			int consumedBits = 0==maxPayloadSize ? 0 : (int)Math.ceil(Math.log(maxPayloadSize)/Math.log(2));
			
			//do not make too large, when doing giant load testing. This can cause memory issues.
			//we do need this long enough to all all the requests unless it will consume more than 1Gb
			//note 28 is a magic number based on the ratio between slab and blob for this schema
			int queueLength = Math.min(2 *maxInFlight * totalSessionsPerTrack, 1<<Math.max(28-consumedBits,3));

			int httpClientConnections = 1+(sessionCount/256);
			httpClientService = new HTTPRequestService[httpClientConnections];
			clientServiceMask = httpClientService.length-1;
			
			int i = httpClientConnections;
			while(--i>=0) {
				httpClientService[i] = runtime.newCommandChannel().newHTTPClientService(queueLength, writer!=null ? maxPayloadSize : 0);	
			}
			
		}

		
		boolean callMessage(CharSequence topic, ChannelReader payload) {
			return makeCall(payload.readInt()); //session Idx
		}

		private boolean makeCall(int sessionIdx) {
		
			if (null != session[track][sessionIdx]) {
				long now = System.nanoTime();
				boolean wasSent = doHTTPCall(sessionIdx);			
				if (wasSent) {
					connectionSessionData[sessionIdx].callRequestInstanceCounter++;//must happen after call			
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
		
		private boolean doHTTPCall(int sessionIdx) {		
			if (null==writer) {
				return (header != null) ? httpGetWithHeader(sessionIdx) : httpGet(sessionIdx);   
			} else { 
				return (header != null) ? httpPostWithHeader(sessionIdx) : httpPost(sessionIdx);
			}
		}
		
		private boolean httpPost(int sessionIdx) {
			return httpClientService[clientServiceMask&sessionIdx].httpPost(session[track][sessionIdx], 
					                          route.route(connectionSessionData[sessionIdx].callRequestInstanceCounter),
					                          (w)->writer.payloadWriter(connectionSessionData[sessionIdx].callRequestInstanceCounter, w));
		}

		private boolean httpGet(int sessionIdx) {
			return httpClientService[clientServiceMask&sessionIdx].httpGet(session[track][sessionIdx], route.route(connectionSessionData[sessionIdx].callRequestInstanceCounter));	
		}

		private boolean httpGetWithHeader(int sessionIdx) {
			return httpClientService[clientServiceMask&sessionIdx].httpGet(session[track][sessionIdx], route.route(connectionSessionData[sessionIdx].callRequestInstanceCounter), header.headerWritable(connectionSessionData[sessionIdx].callRequestInstanceCounter));		
		}
		
		private boolean httpPostWithHeader(int sessionIdx) {
			ClientHostPortInstance s = session[track][sessionIdx];
			if (null!=s) {
				return httpClientService[clientServiceMask&sessionIdx].httpPost(s, route.route(connectionSessionData[sessionIdx].callRequestInstanceCounter), 
						                           header.headerWritable(connectionSessionData[sessionIdx].callRequestInstanceCounter), 
						                           (w)->writer.payloadWriter(connectionSessionData[sessionIdx].callRequestInstanceCounter, w));

			} else {
				//NOTE: must investigate this, we are getting TOO many re-sends? after a close??
				logger.trace("\nold reqeusts where requested after completion.");
				return true;
			}
		}
		
		@Override
		public void startup() {
					
			int s = session[track].length;
			while (--s >= 0) {
			
				final int finalS = s;
				long now = System.currentTimeMillis();
				int i = inFlightHTTPs;
				while (--i>=0) {
					
					while(!callService.publishTopic(w->w.writeInt(finalS))) {
						//must publish this many to get the world moving
						Thread.yield();
						if ((System.currentTimeMillis()-now) > 10_000) {
							out.failedToStart(inFlightHTTPs);
							callService.requestShutdown();
						}
					}
					//must use message to startup the system
				}
				connectionSessionData[s].waitingCountDown--; //we launched 1 cycle for this track on startup.
			}			
			
		}
	
		@Override
		public boolean responseHTTP(HTTPResponseReader reader) {
		
			int sessionIdx = sessionLookup[reader.sessionId()]; //convert to our own internal (eg per track) session idx
			
			
			if (reader.isConnectionClosed()) {
				//System.out.println("connection closed: got response on track "+track+" session "+sessionIdx+" remaining "+connectionSessionData[sessionIdx].callRequestInstanceCounter);
				
				//server or internal subsystem just axed connection and we got no notice..
							
				if (inFlightHead[track]>inFlightTail[track]) {
					
					//NOTE: we have already countDown these however they never arrived.
					int totalMissing = inFlightHead[track]-inFlightTail[track];
					
					if (!httpClientService[clientServiceMask&sessionIdx].hasRoomFor(totalMissing)) {
						return false;//try again later
					}
					
					timeouts+=totalMissing;
					
					
					logger.info("\nConnection {} closed, expecting {} responses which will never arrive, resending http call(s)",reader.connectionId(),totalMissing);
										
					//we must re-request the call
					//keep the clock rolling since this is a penalty against the server
					connectionSessionData[sessionIdx].callRequestInstanceCounter-=totalMissing;
					int i = totalMissing;
					while (--i>=0) {
						while (!doHTTPCall(sessionIdx)) {Thread.yield();};
						connectionSessionData[sessionIdx].callRequestInstanceCounter++;//must happen after call
					}
				}	
				out.connectionClosed(track);
				
				return true;
			}

			//if false we already closed this one and need to skip this part
			if (connectionSessionData[sessionIdx].lastResponseOk) {
				if (!reader.isEndOfResponse()) {
					return true;//just toss all the early chunks we only want the very end.
				}
				

				long sentTime = callTime[track][maxInFlightMask & inFlightTail[track]++];
				long arrivalTime = System.nanoTime();
				long duration = arrivalTime - sentTime;
								
				//only done once to ensure the code remains the same
				if (connectionSessionData[sessionIdx].responsesReceived == (warmupCount-1)) {
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
					&& !validator.validate(connectionSessionData[sessionIdx].responsesReceived, reader)) {
					responsesInvalid++;
				} 				
				connectionSessionData[sessionIdx].responsesReceived++;
			}
			return connectionSessionData[sessionIdx].lastResponseOk = nextCall(sessionIdx);
		}

		private boolean nextCall(int sessionIdx) {
			if ((0xFFFF & connectionSessionData[sessionIdx].waitingCountDown) == 0) {
				pubService.publishTopic(PROGRESS_TOPIC, writer-> {
					writer.writePackedInt(track);
					
					int sumCountDown = 0;
					int s = connectionSessionData.length;
					while (--s >= 0) {
						sumCountDown+=connectionSessionData[s].waitingCountDown;
					}					
					writer.writePackedLong(sumCountDown);					
					
					//writer.writePackedLong(sendAttempts);
					//writer.writePackedLong(sendFailures);
					writer.writePackedLong(timeouts);
					//writer.writePackedLong(responsesReceived);
					writer.writePackedLong(responsesInvalid);
				});				
				
			}
			

			boolean isOk = true;
			if (connectionSessionData[sessionIdx].waitingCountDown >= inFlightHTTPs) { //others are still in flight
				if (durationNanos > 0) {
					isOk = delayService.delay(durationNanos);
				}
				if (isOk) {
					//since this same behavior is the one which will make the call directly 
					//call the method instead of using the private topic,  this eliminates the 
					//need to loop over the features twice for send and receive to ourself.
					isOk = makeCall(sessionIdx);
				}
			}
			
			//System.out.println("session "+sessionIdx+" count down "+connectionSessionData[sessionIdx].waitingCountDown);
		
			if (0==connectionSessionData[sessionIdx].waitingCountDown) {
				boolean clean = pubService.publishTopic(PROGRESS_TOPIC, writer-> {
					writer.writePackedInt(track);
					
					int sumCountDown = 0;
					int s = connectionSessionData.length;
					while (--s >= 0) {
						sumCountDown += connectionSessionData[s].waitingCountDown;						
					}					
					writer.writePackedLong(sumCountDown);
					
					
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
					writer.writePackedLong(connectionSessionData[sessionIdx].callRequestInstanceCounter);
					writer.writePackedLong(sendFailures);
					writer.writePackedLong(timeouts);
					writer.writePackedLong(connectionSessionData[sessionIdx].responsesReceived);
					writer.writePackedLong(responsesInvalid);
				});
				assert(clean) :"unable to end test clean";
				
				--connectionSessionData[sessionIdx].waitingCountDown;//ensure it goes negative so we do not finish more than once, this can happen when connections have been timed out and new requests sent.
				
				//logger.info("publish enders clean:{} for track: {} session: {},",clean,track,sessionIdx);				
				
				ClientHostPortInstance s = session[track][sessionIdx];
		
				if (s!=null) {
					//NOTE: only safe place to close the connection.
						
					if (insecureClient) {
						
						httpClientService[clientServiceMask&sessionIdx].httpClose(s); //FOR TLS we can not close because shared SSLEngine gets closed before we are done. 
					}
					session[track][sessionIdx]=null;
				}			
				
				
			} else if (isOk) { //upon failure should not count down
				--connectionSessionData[sessionIdx].waitingCountDown;
			} else {
				//not an error since we can load test millions of users
			   // logger.warn("Unable to send request, will try again.");
			}

			return isOk;
		}

		long lastProgressCheck = -1;
		
		@Override
		public void timeEvent(long time, int iteration) {

			int callSum = 0;
			int waitingSum = 0;
			int j = connectionSessionData.length;
			while (--j>=0) {
				callSum += connectionSessionData[j].callRequestInstanceCounter;
				waitingSum += connectionSessionData[j].waitingCountDown;
			}
						
			
			if (lastProgressCheck != callSum) {
				lastProgressCheck = callSum;
			} else {			
				if (callSum != (cyclesPerTrack*connectionSessionData.length)) {
					StringBuilder builder = new StringBuilder(); 
					Appendables.appendValue(Appendables.appendValue(Appendables.appendValue(
							Appendables.appendEpochTime(builder.append("\n"), time)
							           .append(" status for track: "),track), " progress:", callSum), "/", cyclesPerTrack,"  No progress has been made! Has the server stopped responding?\n");
					
					System.out.print(builder);
					
					///////////////////////////////
					//we have detected a possible problem so this is the best point to do a deep analysis.
					///////////////////////////////
					
					//First double check that the load testing code has no pipes of data to be processed.
					scanForPipesWithData(this.builder.gm, "Load testing pipe found with data: ");
					
					//Second double check the target server if it was provided.
					if (null!=graphUnderTest) {
						scanForPipesWithData(graphUnderTest, "Server pipe found with data: ");
					}
					
				} else if (waitingSum>=0) {
					System.out.println("No progress on track: "+track+" All requests sent but waiting for ~"+waitingSum+". Was closed connection not detected?");
								
					//First double check that the load testing code has no pipes of data to be processed.
					scanForPipesWithData(this.builder.gm, "Load testing pipe found with data: ");
					
					//Second double check the target server if it was provided.
					if (null!=graphUnderTest) {
						scanForPipesWithData(graphUnderTest, "Server pipe found with data: ");
					}
					
					
				}
				
				//no progress has been made... how many requests are in flight and what connection was used?
				ClientHostPortInstance s = session[track][0];
				if (null!=s) {
				
					int totalMissing = inFlightHead[track]-inFlightTail[track];
					long connectionId = s.getConnectionId();
					long prevConnectionId = s.getPrevConnectionId();
					System.out.println("total requests in flight "+totalMissing+", last used connection "+connectionId+" prev con "+prevConnectionId);
					
					boolean newConnection = false;
					if (-1 == connectionId) {
						//this is the beginning of a connection and the id was not yet stored back so we must look it up.
						connectionId = ClientCoordinator.lookup(s.hostId(), s.port(), s.sessionId);
						newConnection = true;
					}	
					
					if (connectionId>=0) {
						ClientCoordinator ccm = this.builder.getClientCoordinator();
						
						ClientConnection conObj = (ClientConnection)ccm.connectionObjForConnectionId(connectionId, true);
						System.out.println("Con: "+conObj.id+" registered:"+conObj.isRegistered()+" valid:"+conObj.isValid()+" Outstanding:"
								+ Appendables.appendNearestTimeUnit(new StringBuilder(), conObj.outstandingCallTime(System.nanoTime()))+" new:"+newConnection+" atIdx: "+connectionId   							
								+ " closedNoticeSent:"+conObj.isClientClosedNotificationSent()
								);
						
						if (prevConnectionId>=0) {
							ClientConnection conObjPrev = (ClientConnection)ccm.connectionObjForConnectionId(prevConnectionId, true);
							System.out.println("Con: "+conObjPrev.id+" registered:"+conObjPrev.isRegistered()+" valid:"+conObjPrev.isValid()+" Outstanding:"
									+ Appendables.appendNearestTimeUnit(new StringBuilder(), conObjPrev.outstandingCallTime(System.nanoTime()))+" atIdx: "+prevConnectionId    							
									+ " closedNoticeSent:"+conObjPrev.isClientClosedNotificationSent()
									);						
						}	
					}

					if (!ClientSocketReaderStage.abandonSlowConnections) {
						System.out.println("must leave the abandonSlowConnections feature on in ClientSocketReaderStage or hangs like these will happen");
						
					} else {
						System.out.println("SCAN NOW");
						ClientAbandonConnectionScanner.showScan = true;
						ClientAbandonConnectionScanner slow = this.builder.getClientCoordinator().scanForSlowConnections();
						ClientAbandonConnectionScanner.showScan = false;
						System.out.println("SCAN FINISHED");
						
						ClientConnection candidate = slow.leadingCandidate();
						if (null!=candidate) {
							System.out.println("FOUND CANDIDATE: "+candidate);
						}
						ClientConnection[] timedOut = slow.timedOutConnections();
         	        	int i = timedOut.length;
         	        	while (--i >= 0) {
         	        		if (null!=timedOut[i]) {
         	        			System.out.println("FOUND: "+timedOut[i]);
         	        		}
         	        	}					
					}
				}
			}
		}

		private void scanForPipesWithData(GraphManager g, String label) {
			Pipe<?>[] allPipes = GraphManager.allPipes(g);
			int a = allPipes.length;
			while (--a >= 0) {
				 Pipe p = allPipes[a];
				 if (null != p) {	 
					 PronghornStage stagePro = GraphManager.getRingProducer(g, p.id);
					 PronghornStage stageCon = GraphManager.getRingConsumer(g, p.id);							 
					 if (!GraphManager.hasNota(g, stagePro.stageId, GraphManager.MONITOR)) {
						 if (!Pipe.isEmpty(p)) {
							System.out.println(label+p+" "+stagePro+"->"+stageCon);
						 }
				     };
				 }						 
			}
		}

	}
}
