package com.ociweb.gl.impl;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.ListenerTransducer;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.PubSubMethodListener;
import com.ociweb.gl.api.RestMethodListener;
import com.ociweb.gl.api.TimeTrigger;
import com.ociweb.gl.api.transducer.HTTPResponseListenerTransducer;
import com.ociweb.gl.api.transducer.PubSubListenerTransducer;
import com.ociweb.gl.api.transducer.RestListenerTransducer;
import com.ociweb.gl.api.transducer.StateChangeListenerTransducer;
import com.ociweb.gl.impl.mqtt.MQTTConfigImpl;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.gl.impl.stage.HTTPClientRequestTrafficStage;
import com.ociweb.gl.impl.stage.MessagePubSubStage;
import com.ociweb.gl.impl.stage.ReactiveListenerStage;
import com.ociweb.gl.impl.stage.ReactiveManagerPipeConsumer;
import com.ociweb.gl.impl.stage.ReactiveOperators;
import com.ociweb.gl.impl.stage.TrafficCopStage;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStage;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStageConfig;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.MessageSchema;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.scheduling.FixedThreadsScheduler;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;
import com.ociweb.pronghorn.util.Blocker;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

import static com.ociweb.gl.api.MQTTBridge.defaultPort;
import static com.ociweb.gl.api.MQTTBridge.tlsPort;

public class BuilderImpl implements Builder {

	private static final int DEFAULT_MAX_MQTT_IN_FLIGHT = 10;
	private static final int DEFAULT_MAX__MQTT_MESSAGE = 1<<12;
	//NB: The Green Lightning maximum header size 64K is defined here HTTP1xRouterStage.MAX_HEADER
	protected static final int MAXIMUM_INCOMMING_REST_SIZE = 2*HTTP1xRouterStage.MAX_HEADER;
	//  This has a large impact on memory usage but also on performance volume
	protected static final int MINIMUM_INCOMMING_REST_REQUESTS_IN_FLIGHT = 1<<8;
	protected static final int MINIMUM_TLS_BLOB_SIZE = 1<<15;
	
	protected boolean useNetClient;
	protected boolean useNetServer;

	protected long timeTriggerRate;
	protected long timeTriggerStart;
		
	private Blocker channelBlocker;

	public final GraphManager gm;
	public final String[] args;
	
	private int threadLimit = -1;
	private boolean threadLimitHard = false;

	private static final int DEFAULT_LENGTH = 16;
	private static final int DEFAULT_PAYLOAD_SIZE = 128;

	protected static final long MS_TO_NS = 1_000_000;

	private static final Logger logger = LoggerFactory.getLogger(BuilderImpl.class);

	public final PipeConfigManager pcm = new PipeConfigManager();

	public Enum<?> beginningState;
    private int parallelism = 1;//default is one
    
    private static final int maxBehaviorBits = 15;
    private final IntHashTable netPipeLookup;
        
	private static final int BehaviorMask = 1<<31;//high bit on
	

	/////////////////
	///Pipes for initial startup declared subscriptions. (Not part of graph)
    //TODO: should be zero unless startup is used.
	private final int maxStartupSubs = 256; //TODO: make a way to adjust this outside???
	private final int maxTopicLengh  = 128;
	private Pipe<MessagePubSub> tempPipeOfStartupSubscriptions;
	/////////////////
	/////////////////
    
    private long defaultSleepRateNS = 2000;// should normally be between 900 and 20_000; 
    
	private final int shutdownTimeoutInSeconds = 1;

	protected ReentrantLock devicePinConfigurationLock = new ReentrantLock();

	protected MQTTConfigImpl mqtt = null;
		
	private String defaultHostPath = "";
	private String bindHost = null;
	private int bindPort = -1;
	private boolean isLarge = false;
	private boolean isTLSServer = true; 
	private boolean isTLSClient = true; 

	private ClientCoordinator ccm;

	
	private boolean isTelemetryEnabled = false;
	private String telemetryHost = null;
	private int telemetryPort = defaultTelemetryPort;
	
	
	//TODO: set these vales when we turn on the client usage??
	private int connectionsInBit = 3; 
	private int maxPartialResponse = 10;
	
	protected int IDX_MSG = -1;
	protected int IDX_NET = -1;
	//TODO: why is responder missing?
	   
    ///////
	//These topics are enforced so that they only go from one producer to a single consumer
	//No runtime subscriptions can pick them up
	//They do not go though the public router
	//private topics never share a pipe so the topic is not sent on the pipe only the payload
	//private topics are very performant and much more secure than pub/sub.
	//private topics have their own private pipe and can not be "imitated" by public messages
	//WARNING: private topics do not obey traffic cops and allow for immediate communications.
	///////
	private String[] privateTopics = null;
	
    ////////////////////////////
    ///gather and store the server module pipes
    /////////////////////////////
    private ArrayList<Pipe<HTTPRequestSchema>>[][] collectedHTTPRequstPipes;
	private ArrayList<Pipe<ServerResponseSchema>>[] collectedServerResponsePipes;
	

	
	//////////////////////////////
	//support for REST modules and routing
	//////////////////////////////
	public final HTTPSpecification<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>
	             httpSpec = HTTPSpecification.defaultSpec();
	private HTTP1xRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>
	             routerConfig;	//////////////////////////////
	//////////////////////////////
	
	    
    public final ReactiveOperators operators;
	
	
    public void registerHTTPClientId(int routeId, int pipeIdx) {
		boolean addedItem = IntHashTable.setItem(netPipeLookup, routeId, pipeIdx);
        if (!addedItem) {
        	logger.warn("The route {} has already been assigned to a listener and can not be assigned to another.\n"
        			+ "Check that each HTTP Client consumer does not share an Id with any other.",routeId);
        }
    }
    
    public int lookupHTTPClientPipe(int routeId) {
    	return IntHashTable.getItem(netPipeLookup, routeId);
    }
    
	public int pubSubIndex() {
		return IDX_MSG;
	}
	
	public int netIndex() {
		return IDX_NET;
	}
	
	public void releasePubSubTraffic(int count, MsgCommandChannel<?> gcc) {
		MsgCommandChannel.publishGo(count, IDX_MSG, gcc);
	}
	
	public final boolean isLarge() {
		return isLarge;
	}
	
	public final boolean isServerTLS() {
		return isTLSServer;
	}
	
	public final boolean isClientTLS() {
		return isTLSClient;
	}
	
	
	public final String bindHost() {
		return bindHost;
	}
	
	public final int bindPort() {
		return bindPort;
	}
	
	public final String defaultHostPath() {
		return defaultHostPath;
	}
	
	
	public ClientCoordinator getClientCoordinator() {
		return ccm;
	}
	
	public final void enableServer(boolean isTLS, boolean isLarge, String bindHost, int bindPort) {
		enableServer(isTLS, isLarge, bindHost, bindPort, "");
	}
	
    public final void enableServer(boolean isTLS, boolean isLarge, String bindHost, int bindPort, String defaultPath) {
    	
    	this.useNetServer();

    	this.defaultHostPath = defaultPath;
    	this.isTLSServer = isTLS;
    	this.isLarge = isLarge;
    	this.bindHost = bindHost;
    	if (null==this.bindHost) {
    		this.bindHost = NetGraphBuilder.bindHost();
    	}
    	this.bindPort = bindPort;
    	if (bindPort<=0 || (bindPort>=(1<<16))) {
    		throw new UnsupportedOperationException("invalid port "+bindPort);
    	}
    }
 
	public final void enableServer(boolean isTLS, int bindPort) {
		enableServer(isTLS, bindPort, "");
	}
	
    public final void enableServer(boolean isTLS, int bindPort, String defaultPath) {
    	enableServer(isTLS,false,NetGraphBuilder.bindHost(),bindPort,defaultPath);
    }
    
	public final void enableServer(int bindPort) {
		enableServer(bindPort, "");
	}
	
	public final void enableServer(String host, int bindPort) {
		enableServer(host, bindPort, "");
	}
    
    public final void enableServer(int bindPort, String defaultPath) {
    	enableServer(true,false,NetGraphBuilder.bindHost(),bindPort);
    }
    
    public final void enableServer(String host, int bindPort, String defaultPath) {
    	enableServer(true,false,null==host?NetGraphBuilder.bindHost():host,bindPort);
    }
    
    public String getArgumentValue(String longName, String shortName, String defaultValue) {
    	return MsgRuntime.getOptArg(longName, shortName, args, defaultValue);
    }

	public boolean hasArgument(String longName, String shortName) {
		return MsgRuntime.hasArg(longName, shortName, args);
	}

    public int behaviorId(Behavior b) {
    	return BehaviorMask | System.identityHashCode(b);
    }
    
    public final HTTP1xRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults> routerConfig() {
    	if (null==routerConfig) {
    		routerConfig = new HTTP1xRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>(httpSpec); 
    	}    	
    	return routerConfig;
    }
 
    
	public final void appendPipeMapping(Pipe<HTTPRequestSchema> httpRequestPipe, int routeIdx, int parallelId) {
		
		lazyCreatePipeLookupMatrix();
		collectedHTTPRequstPipes[parallelId][routeIdx].add(httpRequestPipe);

	}

	private void lazyCreatePipeLookupMatrix() {
		if (null==collectedHTTPRequstPipes) {
			
			int parallelism = parallelism();
			int routesCount = routerConfig().routesCount();
			
			assert(parallelism>=1);
			assert(routesCount>-1);	
			
			//for catch all route since we have no specific routes.
			if (routesCount==0) {
				routesCount = 1;
			}
			
			collectedHTTPRequstPipes = (ArrayList<Pipe<HTTPRequestSchema>>[][]) new ArrayList[parallelism][routesCount];
			
			int p = parallelism;
			while (--p>=0) {
				int r = routesCount;
				while (--r>=0) {
					collectedHTTPRequstPipes[p][r] = new ArrayList();
				}
			}
			
	
		}
	}
	
	
	public final ArrayList<Pipe<HTTPRequestSchema>> buildFromRequestArray(int r, int p) {
		return null!=collectedHTTPRequstPipes ? collectedHTTPRequstPipes[r][p] : new ArrayList<Pipe<HTTPRequestSchema>>();
	}
	
	
	public final void recordPipeMapping(Pipe<ServerResponseSchema> netResponse, int parallelInstanceId) {
		
		if (null == collectedServerResponsePipes) {
			int parallelism = parallelism();
			collectedServerResponsePipes =  (ArrayList<Pipe<ServerResponseSchema>>[]) new ArrayList[parallelism];
			
			int p = parallelism;
			while (--p>=0) {
				collectedServerResponsePipes[p] = new ArrayList();
			}
			
		}
		
		collectedServerResponsePipes[parallelInstanceId].add(netResponse);
		
	}
	

	public final Pipe<ServerResponseSchema>[] buildToOrderArray(int r) {
		if (null==collectedServerResponsePipes || collectedServerResponsePipes.length==0) {
			return new Pipe[0];
		} else {
			ArrayList<Pipe<ServerResponseSchema>> list = collectedServerResponsePipes[r];
			return (Pipe<ServerResponseSchema>[]) list.toArray(new Pipe[list.size()]);
		}
	}
	
	
    public final Pipe<ServerResponseSchema> newNetResponsePipe(PipeConfig<ServerResponseSchema> config, int parallelInstanceId) {
    	Pipe<ServerResponseSchema> pipe = new Pipe<ServerResponseSchema>(config) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataOutputBlobWriter<ServerResponseSchema> createNewBlobWriter() {
				return new NetResponseWriter(this);
			}
    	};
 	   recordPipeMapping(pipe, parallelInstanceId);
 	   return pipe;
    }

	////////////////////////////////
	
	public BuilderImpl(GraphManager gm, String[] args) {	
		
		this.operators = ReactiveListenerStage.reactiveOperators();
		
		this.gm = gm;
		this.getTempPipeOfStartupSubscriptions().initBuffers();
		this.args = args;
		
		this.pcm.addConfig(new PipeConfig<HTTPRequestSchema>(HTTPRequestSchema.instance, 
									                   MINIMUM_INCOMMING_REST_REQUESTS_IN_FLIGHT, 
									                   MAXIMUM_INCOMMING_REST_SIZE));
				
		int requestQueue = 4;
		this.pcm.addConfig(new PipeConfig<NetPayloadSchema>(NetPayloadSchema.instance,
				                                    requestQueue,
				                                    MINIMUM_TLS_BLOB_SIZE)); 		
			
		int maxMessagesQueue = 8;
		int maxMessageSize = 256;
		this.pcm.addConfig(new PipeConfig<MessageSubscription>(MessageSubscription.instance,
				maxMessagesQueue,
				maxMessageSize)); 		


		this.pcm.addConfig(new PipeConfig<TrafficReleaseSchema>(TrafficReleaseSchema.instance, DEFAULT_LENGTH));
		this.pcm.addConfig(new PipeConfig<TrafficAckSchema>(TrafficAckSchema.instance, DEFAULT_LENGTH));

	    int defaultCommandChannelLength = 16;
	    int defaultCommandChannelHTTPMaxPayload = 1<<14; //must be at least 32K for TLS support	    
		this.pcm.addConfig(new PipeConfig<NetResponseSchema>(NetResponseSchema.instance, defaultCommandChannelLength, defaultCommandChannelHTTPMaxPayload));   

		//for MQTT ingress
		int maxMQTTMessagesQueue = 8;
		int maxMQTTMessageSize = 1024;
		this.pcm.addConfig(new PipeConfig(IngressMessages.instance, maxMQTTMessagesQueue, maxMQTTMessageSize));
		
		this.netPipeLookup = new IntHashTable(maxBehaviorBits);
	}

	public final <E extends Enum<E>> boolean isValidState(E state) {

		if (null!=beginningState) {
			return beginningState.getClass()==state.getClass();    		
		}
		return false;
	}

	public final <E extends Enum<E>> Builder startStateMachineWith(E state) {   	
		beginningState = state;
		return this;
	}
	
	public final Builder setTimerPulseRate(long rateInMS) {
		timeTriggerRate = rateInMS;
		timeTriggerStart = System.currentTimeMillis()+rateInMS;
		return this;
	}
	
	public final Builder setTimerPulseRate(TimeTrigger trigger) {	
		long period = trigger.getRate();
		timeTriggerRate = period;
		long now = System.currentTimeMillis();		
		long soFar = (now % period);		
		timeTriggerStart = (now - soFar) + period;				
		return this;
	}

	public final Builder useNetClient() {
		this.useNetClient = true;

		return this;
	}
	
	public final Builder useInsecureNetClient() {
		this.useNetClient = true;
		this.isTLSClient = false;

		return this;
	}

	public final Builder useNetServer() {
		this.useNetServer = true;
		return this;
	}

	public final boolean isUseNetServer() {
		return this.useNetServer;
	}

	public final boolean isUseNetClient() {
		return this.useNetClient;
	}
	
	public final long getTriggerRate() {
		return timeTriggerRate;
	}
	public final long getTriggerStart() {
		return timeTriggerStart;
	}

    public <R extends ReactiveListenerStage> R createReactiveListener(GraphManager gm,  Behavior listener, 
    		                		Pipe<?>[] inputPipes, Pipe<?>[] outputPipes, 
    		                		ArrayList<ReactiveManagerPipeConsumer> consumers, int parallelInstance) {
    	assert(null!=listener);
    	
    	return (R) new ReactiveListenerStage(gm, listener, inputPipes, outputPipes, consumers, this, parallelInstance);
    }

	public <G extends MsgCommandChannel> G newCommandChannel(
												int features,
			                                    int parallelInstanceId,
			                                    PipeConfigManager pcm
			                                ) {
		return (G) new GreenCommandChannel(gm, this, features, parallelInstanceId,
				                           pcm);
	}	
	
	public <G extends MsgCommandChannel> G newCommandChannel(
            int parallelInstanceId,
            PipeConfigManager pcm
        ) {
		return (G) new GreenCommandChannel(gm, this, 0, parallelInstanceId,
				                           pcm);
	}

	static final boolean debug = false;

	public void shutdown() {
		if (null!=ccm) {
			ccm.shutdown();
		}
		
		
		//can be overridden by specific hardware impl if shutdown is supported.
	}

	protected void initChannelBlocker(int maxGoPipeId) {
		channelBlocker = new Blocker(maxGoPipeId+1);
	}

	protected final boolean useNetClient(Pipe<ClientHTTPRequestSchema>[] netRequestPipes) {

		return (netRequestPipes.length!=0);
	}

	protected final void createMessagePubSubStage(IntHashTable subscriptionPipeLookup,
			Pipe<IngressMessages>[] ingressMessagePipes,
			Pipe<MessagePubSub>[] messagePubSub,
			Pipe<TrafficReleaseSchema>[] masterMsggoOut, 
			Pipe<TrafficAckSchema>[] masterMsgackIn, 
			Pipe<MessageSubscription>[] subscriptionPipes) {


		new MessagePubSubStage(this.gm, subscriptionPipeLookup, this, 
				                ingressMessagePipes, messagePubSub, 
				                masterMsggoOut, masterMsgackIn, subscriptionPipes);


	}

	public StageScheduler createScheduler(final MsgRuntime runtime) {
				
		final StageScheduler scheduler = 
				 runtime.builder.threadLimit>0 ?				
		         StageScheduler.defaultScheduler(gm, 
		        		 runtime.builder.threadLimit, 
		        		 runtime.builder.threadLimitHard) :
		         StageScheduler.defaultScheduler(gm);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				scheduler.shutdown();
				scheduler.awaitTermination(getShutdownSeconds(), TimeUnit.SECONDS);
			}
		});
		
		
		return scheduler;
	}

	private final int getShutdownSeconds() {
		return shutdownTimeoutInSeconds;
	}

	
	protected final ChildClassScannerVisitor deepListener = new ChildClassScannerVisitor<ListenerTransducer>() {
		@Override
		public boolean visit(ListenerTransducer child, Object topParent) {
			return false;
		}		
	};
	
	public final boolean isListeningToSubscription(Behavior listener) {
			
		//NOTE: we only call for scan if the listener is not already of this type
		return listener instanceof PubSubMethodListenerBase ||
			   listener instanceof StateChangeListenerBase<?> 
		       || !ChildClassScanner.visitUsedByClass(listener, deepListener, PubSubListenerTransducer.class)
		       || !ChildClassScanner.visitUsedByClass(listener, deepListener, StateChangeListenerTransducer.class);
	}

	public final boolean isListeningToHTTPResponse(Object listener) {
		return listener instanceof HTTPResponseListenerBase ||
			   //will return false if HTTPResponseListenerBase was encountered
			  !ChildClassScanner.visitUsedByClass(listener, deepListener, HTTPResponseListenerTransducer.class);
	}

	public final boolean isListeningHTTPRequest(Object listener) {
		return listener instanceof RestMethodListenerBase ||
			    //will return false if RestListenerBase was encountered
			   !ChildClassScanner.visitUsedByClass(listener, deepListener, RestListenerTransducer.class);
	}
	
	/**
	 * access to system time.  This method is required so it can be monitored and simulated by unit tests.
	 */
	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}

	public final void blockChannelUntil(int channelId, long timeInMillis) {        
		channelBlocker.until(channelId, timeInMillis);
	}

	public final boolean isChannelBlocked(int channelId) {
		if (null != channelBlocker)  {
			return channelBlocker.isBlocked(channelId);
		} else {
			return false;
		}
	}

	public final long releaseChannelBlocks(long now) {
		if (null != channelBlocker) {
			channelBlocker.releaseBlocks(now);
			return channelBlocker.durationToNextRelease(now, -1);
		} else {
			return -1; //was not init so there are no possible blocked channels.
		}
	}

	public final long nanoTime() {
		return System.nanoTime();
	}

	public final Enum[] getStates() {
		return null==beginningState? new Enum[0] : beginningState.getClass().getEnumConstants();
	}

	public final void addStartupSubscription(CharSequence topic, int systemHash) {

		Pipe<MessagePubSub> pipe = getTempPipeOfStartupSubscriptions();

		if (PipeWriter.tryWriteFragment(pipe, MessagePubSub.MSG_SUBSCRIBE_100)) {
			PipeWriter.writeUTF8(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
			PipeWriter.writeInt(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, systemHash);
			PipeWriter.publishWrites(pipe);
		} else {
			throw new UnsupportedOperationException("Limited number of startup subscriptions "+maxStartupSubs+" encountered.");
		}
	}

	private final Pipe<MessagePubSub> getTempPipeOfStartupSubscriptions() {
		if (null==tempPipeOfStartupSubscriptions) {

			final PipeConfig<MessagePubSub> messagePubSubConfig = new PipeConfig<MessagePubSub>(MessagePubSub.instance, maxStartupSubs, maxTopicLengh);   
			tempPipeOfStartupSubscriptions = new Pipe<MessagePubSub>(messagePubSubConfig);

		}		

		return tempPipeOfStartupSubscriptions;
	}

	public final Pipe<MessagePubSub> consumeStartupSubscriptions() {
		Pipe<MessagePubSub> result = tempPipeOfStartupSubscriptions;
		tempPipeOfStartupSubscriptions = null;//no longer needed
		return result;
	}

	@Override
	public final void limitThreads(int threadLimit) {
		this.threadLimit = threadLimit;
		this.threadLimitHard = true;
	}

	@Override
	public void limitThreads() {
		this.threadLimit = idealThreadCount();
		this.threadLimitHard = true;
	}

	private int idealThreadCount() {
		return Runtime.getRuntime().availableProcessors()*4;
	}

	public final int parallelism() {
		return parallelism;
	}

	@Override
	public final void parallelism(int parallel) {
		parallelism = parallel;
	}

	
	private final TrieParserReader localReader = new TrieParserReader(0, true);
	
	@Override
	public long fieldId(int routeId, byte[] fieldName) {	
		return TrieParserReader.query(localReader, this.routeExtractionParser(routeId), fieldName, 0, fieldName.length, Integer.MAX_VALUE);
	}
		
	@Override
	public final int registerRoute(CharSequence route, byte[] ... headers) {
		if (route.length()==0) {
			throw new UnsupportedOperationException("path must be of length one or more and start with /");
		}
		if (route.charAt(0)!='/') {
			throw new UnsupportedOperationException("path must start with /");
		}
		return routerConfig().registerRoute(route, headers);
	}
	
	@Override
	public final int defineRoute(CharSequence route, byte[] ... headers) {
		if (route.length()==0) {
			throw new UnsupportedOperationException("path must be of length one or more and start with /");
		}
		if (route.charAt(0)!='/') {
			throw new UnsupportedOperationException("path must start with /");
		}
		return routerConfig().registerRoute(route, headers);
	}

	public final TrieParser routeExtractionParser(int route) {
		return routerConfig().extractionParser(route).getRuntimeParser();
	}
	
	public final int routeExtractionParserIndexCount(int route) {
		return routerConfig().extractionParser(route).getIndexCount();
	}
	
	public IntHashTable routeHeaderToPositionTable(int routeId) {
		return routerConfig().headerToPositionTable(routeId);
	}
	
	public TrieParser routeHeaderTrieParser(int routeId) {
		return routerConfig().headerTrieParser(routeId);
	}

	public final Pipe<HTTPRequestSchema> newHTTPRequestPipe(PipeConfig<HTTPRequestSchema> restPipeConfig) {
		final boolean hasNoRoutes = (0==routerConfig().routesCount());
		Pipe<HTTPRequestSchema> pipe = new Pipe<HTTPRequestSchema>(restPipeConfig) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<HTTPRequestSchema> createNewBlobReader() {
				return new HTTPRequestReader(this, hasNoRoutes);
			}
		};
		return pipe;
	}

	public final boolean isTelemetryEnabled() {
		return isTelemetryEnabled;
	}

	public int telmetryPort() {
		return telemetryPort;
	}
	
	public String telemetryHost() {
		return telemetryHost;
	}
	
	@Override
	public String enableTelemetry() {
		isTelemetryEnabled = true;
		return telemetryHost = NetGraphBuilder.bindHost();
	}
	
	@Override
	public String enableTelemetry(int port) {
		isTelemetryEnabled = true;
		telemetryPort = port;
		return telemetryHost = NetGraphBuilder.bindHost();
	}
	
	@Override
	public void enableTelemetry(String host, int port) {
		isTelemetryEnabled = true;
		telemetryPort = port;
		telemetryHost = host;
	}
	
	public final long getDefaultSleepRateNS() {
		return defaultSleepRateNS;
	}

	@Override
	public final void setDefaultRate(long ns) {
		defaultSleepRateNS = Math.max(ns, 2_000); //protect against too small 
	}


	public void buildStages(MsgRuntime runtime) {

		IntHashTable subscriptionPipeLookup2 = MsgRuntime.getSubPipeLookup(runtime); 
		GraphManager gm2 = MsgRuntime.getGraphManager(runtime);
		
		Pipe<NetResponseSchema>[] httpClientResponsePipes = GraphManager.allPipesOfTypeWithNoProducer(gm2, NetResponseSchema.instance);
		Pipe<MessageSubscription>[] subscriptionPipes = GraphManager.allPipesOfTypeWithNoProducer(gm2, MessageSubscription.instance);
		
		Pipe<TrafficOrderSchema>[] orderPipes = GraphManager.allPipesOfTypeWithNoConsumer(gm2, TrafficOrderSchema.instance);
		Pipe<ClientHTTPRequestSchema>[] httpClientRequestPipes = GraphManager.allPipesOfTypeWithNoConsumer(gm2, ClientHTTPRequestSchema.instance);			
		Pipe<MessagePubSub>[] messagePubSub = GraphManager.allPipesOfTypeWithNoConsumer(gm2, MessagePubSub.instance);
		Pipe<IngressMessages>[] ingressMessagePipes = GraphManager.allPipesOfTypeWithNoConsumer(gm2, IngressMessages.instance);
		
		
		int commandChannelCount = orderPipes.length;
		int eventSchemas = 0;
		
		IDX_MSG = (IntHashTable.isEmpty(subscriptionPipeLookup2) 
				    && subscriptionPipes.length==0 && messagePubSub.length==0) ? -1 : eventSchemas++;
		IDX_NET = useNetClient(httpClientRequestPipes) ? eventSchemas++ : -1;
						
        long timeout = 20_000; //20 seconds
		
		int maxGoPipeId = 0;
					
		int t = commandChannelCount;
								
		Pipe<TrafficReleaseSchema>[][] masterGoOut = new Pipe[eventSchemas][0];
		Pipe<TrafficAckSchema>[][]     masterAckIn = new Pipe[eventSchemas][0];

		if (IDX_MSG >= 0) {
			masterGoOut[IDX_MSG] = new Pipe[messagePubSub.length];
			masterAckIn[IDX_MSG] = new Pipe[messagePubSub.length];
		}		
		if (IDX_NET >= 0) {
			masterGoOut[IDX_NET] = new Pipe[httpClientResponsePipes.length];
			masterAckIn[IDX_NET] = new Pipe[httpClientResponsePipes.length];
		}		
				
		while (--t>=0) {
		
			int features = getFeatures(gm2, orderPipes[t]);
			
			Pipe<TrafficReleaseSchema>[] goOut = new Pipe[eventSchemas];
			Pipe<TrafficAckSchema>[] ackIn = new Pipe[eventSchemas];
			
			boolean isDynamicMessaging = (features&Behavior.DYNAMIC_MESSAGING) != 0;
			boolean isNetRequester     = (features&Behavior.NET_REQUESTER) != 0;

			boolean hasConnections = false;
			if (isDynamicMessaging) {
				hasConnections = true;		 		
		 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_MSG);
			}
			if (isNetRequester) {
				hasConnections = true;		 		
		 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_NET);
			}

			if (true | hasConnections) {
				TrafficCopStage trafficCopStage = new TrafficCopStage(gm, 
						timeout, orderPipes[t], 
						ackIn, goOut, 
						runtime, this);
			} else {
				//this optimization can no longer be done due to the use of shutdown on command channel.
				//    revisit this later...
				//TODO: we can reintroduce this as long has we have a stage here which does shutdown on -1;
				PipeCleanerStage.newInstance(gm, orderPipes[t]);
			}
		}
				
		initChannelBlocker(maxGoPipeId);
		
		buildHTTPClientGraph(httpClientResponsePipes, httpClientRequestPipes, masterGoOut, masterAckIn);
		
		/////////
		//always create the pub sub and state management stage?
		/////////
		//TODO: only create when subscriptionPipeLookup is not empty and subscriptionPipes has zero length.
		if (IDX_MSG<0) {
			logger.trace("saved some resources by not starting up the unused pub sub service.");
		} else {
			//logger.info("builder created pub sub");
		 	createMessagePubSubStage(subscriptionPipeLookup2, 
		 			ingressMessagePipes, messagePubSub, 
		 			masterGoOut[IDX_MSG], masterAckIn[IDX_MSG], 
		 			subscriptionPipes);
		}
	}

	
	protected void buildHTTPClientGraph(Pipe<NetResponseSchema>[] netResponsePipes,
			Pipe<ClientHTTPRequestSchema>[] netRequestPipes, Pipe<TrafficReleaseSchema>[][] masterGoOut,
			Pipe<TrafficAckSchema>[][] masterAckIn) {
		////////
		//create the network client stages
		////////
		if (useNetClient(netRequestPipes)) {
			
			int connectionsInBits=10;			
			int maxPartialResponses=4;
			boolean isTLS = isClientTLS();
			int responseQueue = 10;
			int responseSize = 1<<16;
			int outputsCount = 1;
			
			
			if (masterGoOut[IDX_NET].length != masterAckIn[IDX_NET].length) {
				throw new UnsupportedOperationException(masterGoOut[IDX_NET].length+"!="+masterAckIn[IDX_NET].length);
			}
			if (masterGoOut[IDX_NET].length != netRequestPipes.length) {
				throw new UnsupportedOperationException(masterGoOut[IDX_NET].length+"!="+netRequestPipes.length);
			}
			
			assert(masterGoOut[IDX_NET].length == masterAckIn[IDX_NET].length);
			assert(masterGoOut[IDX_NET].length == netRequestPipes.length);

			PipeConfig<NetPayloadSchema> clientNetRequestConfig = pcm.getConfig(NetPayloadSchema.class);
					
					
			//BUILD GRAPH
			ccm = new ClientCoordinator(connectionsInBits, maxPartialResponses, isTLS);
		
			Pipe<NetPayloadSchema>[] clientRequests = new Pipe[outputsCount];
			int r = outputsCount;
			while (--r>=0) {
				clientRequests[r] = new Pipe<NetPayloadSchema>(clientNetRequestConfig);		
			}
			HTTPClientRequestTrafficStage requestStage = new HTTPClientRequestTrafficStage(gm, this, ccm, netRequestPipes, masterGoOut[IDX_NET], masterAckIn[IDX_NET], clientRequests);
						

			NetGraphBuilder.buildHTTPClientGraph(gm, maxPartialResponses, ccm, 
					responseQueue, responseSize, clientRequests, netResponsePipes);
						
		}
	}


	protected int populateGoAckPipes(int maxGoPipeId, Pipe<TrafficReleaseSchema>[][] masterGoOut,
			Pipe<TrafficAckSchema>[][] masterAckIn, Pipe<TrafficReleaseSchema>[] goOut, Pipe<TrafficAckSchema>[] ackIn,
			int p) {
		addToLastNonNull(masterGoOut[p], goOut[p] = new Pipe<TrafficReleaseSchema>(this.pcm.getConfig(TrafficReleaseSchema.class)));
		
		maxGoPipeId = Math.max(maxGoPipeId, goOut[p].id);				
		
		addToLastNonNull(masterAckIn[p], ackIn[p] = new Pipe<TrafficAckSchema>(this.pcm.getConfig(TrafficAckSchema.class)));
		return maxGoPipeId;
	}
	

	private <S extends MessageSchema<S>> void addToLastNonNull(Pipe<S>[] pipes, Pipe<S> pipe) {
		int i = pipes.length;
		while (--i>=0) {
			if (null == pipes[i]) {
				pipes[i] = pipe;
				return;
			}
		}		
	}

	
	protected int getFeatures(GraphManager gm2, Pipe<TrafficOrderSchema> orderPipe) {
		PronghornStage producer = gm2.getRingProducer(gm,orderPipe.id);
		assert(producer instanceof ReactiveListenerStage) : "TrafficOrderSchema must only come from Reactor stages but was "+producer.getClass().getSimpleName();
		
		ReactiveListenerStage reactor =  (ReactiveListenerStage)producer;			
		int features = reactor.getFeatures(orderPipe);
		return features;
	}
	
	@Override
	public MQTTConfigImpl useMQTT(CharSequence host, int port, boolean isTLS, CharSequence clientId) {		
		return useMQTT(host, port, isTLS, clientId, DEFAULT_MAX_MQTT_IN_FLIGHT, DEFAULT_MAX__MQTT_MESSAGE);
	}

	@Override
	public MQTTConfigImpl useMQTT(CharSequence host, boolean isTLS, CharSequence clientId) {
		return useMQTT(host, isTLS ? tlsPort : defaultPort, isTLS, clientId, DEFAULT_MAX_MQTT_IN_FLIGHT, DEFAULT_MAX__MQTT_MESSAGE);
	}
		
	@Override
	public MQTTConfigImpl useMQTT(CharSequence host, int port, boolean isTLS, CharSequence clientId, int maxInFlight) {		
		return useMQTT(host, port, isTLS, clientId, maxInFlight, DEFAULT_MAX__MQTT_MESSAGE);	
	}
	
	@Override
	public MQTTConfigImpl useMQTT(CharSequence host, int port, boolean isTLS, CharSequence clientId, int maxInFlight, int maxMessageLength) {		
		if (maxInFlight>(1<<15)) {
			throw new UnsupportedOperationException("Does not suppport more than "+(1<<15)+" in flight");
		}
		if (maxMessageLength>(256*(1<<20))) {
			throw new UnsupportedOperationException("Specification does not support values larger than 256M");
		}
		 
		pcm.ensureSize(MessageSubscription.class, maxInFlight, maxMessageLength);
		
		//all these use a smaller rate to ensure MQTT can stay ahead of the internal message passing
		long rate = defaultSleepRateNS>200_000?defaultSleepRateNS/4:defaultSleepRateNS;
		
		return mqtt = new MQTTConfigImpl(host, port, clientId, 
				                    this, rate, 
				                    (short)maxInFlight, maxMessageLength, isTLS);
	}
	
	@Override
	public void privateTopics(String... topic) {
		privateTopics=topic;
	}

	@Override
	public String[] args() {
		return args;
	}

	public void blockChannelDuration(long durationNanos, int pipeId) {
		final long durationMills = durationNanos/1_000_000;
		final long remaningNanos = durationNanos%1_000_000;		
			    
	    if (remaningNanos>0) {
	    	try {
	    		long limit = System.nanoTime()+remaningNanos;
	    		
				Thread.sleep(0L, (int)remaningNanos);
				
				long dif;
				while ((dif = (limit-System.nanoTime()))>0) {
					if (dif>100) {
						Thread.yield();
					}
				}
				
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
    			return;
			}
	    }
	    if (durationMills>0) {
	    	
	    	//now pull the current time and wait until ms have passed
			blockChannelUntil(pipeId, currentTimeMillis() + durationMills );
	    }
	}

	/**
	 * Enables the child classes to modify which schemas are used.
	 * For the pi this allows for using i2c instead of digital or analog in transducers.
	 * 
	 * @param schema
	 */
	public MessageSchema schemaMapper(MessageSchema schema) {
		return schema;
	}




}
