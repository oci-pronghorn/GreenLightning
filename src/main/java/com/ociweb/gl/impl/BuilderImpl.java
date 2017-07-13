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
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.StateChangeListener;
import com.ociweb.gl.api.TimeTrigger;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.gl.impl.stage.HTTPClientRequestStage;
import com.ociweb.gl.impl.stage.MessagePubSubStage;
import com.ociweb.gl.impl.stage.ReactiveListenerStage;
import com.ociweb.gl.impl.stage.TrafficCopStage;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
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

public class BuilderImpl implements Builder {

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

	protected final PipeConfig<TrafficReleaseSchema> releasePipesConfig   = new PipeConfig<TrafficReleaseSchema>(TrafficReleaseSchema.instance, DEFAULT_LENGTH);
	protected final PipeConfig<TrafficOrderSchema> orderPipesConfig       = new PipeConfig<TrafficOrderSchema>(TrafficOrderSchema.instance, DEFAULT_LENGTH);
	protected final PipeConfig<TrafficAckSchema> ackPipesConfig           = new PipeConfig<TrafficAckSchema>(TrafficAckSchema.instance, DEFAULT_LENGTH);

	protected static final long MS_TO_NS = 1_000_000;

	private static final Logger logger = LoggerFactory.getLogger(BuilderImpl.class);

	public final PipeConfig<HTTPRequestSchema> restPipeConfig = new PipeConfig<HTTPRequestSchema>(HTTPRequestSchema.instance, 1<<9, 256);
	
	public Enum<?> beginningState;
    private int parallelism = 1;//default is one

	/////////////////
	///Pipes for initial startup declared subscriptions. (Not part of graph)
    //TODO: should be zero unless startup is used.
	private final int maxStartupSubs = 256; //TODO: make a way to adjust this outside???
	private final int maxTopicLengh  = 128;
	private Pipe<MessagePubSub> tempPipeOfStartupSubscriptions;
	/////////////////
	/////////////////
    
    private long defaultSleepRateNS = 1_200;// should normally be between 900 and 10_000; 
    
	private final int shutdownTimeoutInSeconds = 1;

	protected ReentrantLock devicePinConfigurationLock = new ReentrantLock();

	protected MQTTConfigImpl mqtt = null;
		
	private String bindHost = null;
	private int bindPort = -1;
	private boolean isLarge = false;
	private boolean isTLS = true; 
	private boolean isTelemetryEnabled = false;
	
	//TODO: set these vales when we turn on the client usage??
	private int connectionsInBit = 3; 
	private int maxPartialResponse = 10;
	
	protected int IDX_MSG = -1;
	protected int IDX_NET = -1;
	   
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
	public final HTTPSpecification<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults> httpSpec = HTTPSpecification.defaultSpec();
	private final HTTP1xRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults> routerConfig
	                               = new HTTP1xRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>(httpSpec); 
	//////////////////////////////
	//////////////////////////////

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
	
	public final boolean isTLS() {
		return isTLS;
	}
	
	public final String bindHost() {
		return bindHost;
	}
	
	public final int bindPort() {
		return bindPort;
	}
	
    public final void enableServer(boolean isTLS, boolean isLarge, String bindHost, int bindPort) {
    	
    	this.useNetServer();
    	this.isTLS = isTLS;
    	this.isLarge = isLarge;
    	this.bindHost = bindHost;
    	this.bindPort = bindPort;
    	
    }
 
    public final void enableServer(boolean isTLS, int bindPort) {
    	enableServer(isTLS,false,NetGraphBuilder.bindHost(),bindPort);
    }
    
    public final void enableServer(int bindPort) {
    	enableServer(true,false,NetGraphBuilder.bindHost(),bindPort);
    }
    
    
    public int behaviorId(Behavior b) {
    	return System.identityHashCode(b); //TODO: we may want to find a beter way to compute this, not sure.
    }
    
    public final HTTP1xRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults> routerConfig() {
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
	
    //Linear search only used once in startup method for the stage.
	public final void lookupRouteAndPara(Pipe<?> localPipe, int idx, int[] routes, int[] para) {

		int p = parallelism();
		while (--p >= 0) {

			int r = routerConfig().routesCount();
			while (--r >= 0) {
				if (collectedHTTPRequstPipes[p][r].contains(localPipe)) {
					routes[idx] = r;
					para[idx] = p;	
					return;
				}
			}
		}
		throw new UnsupportedOperationException("can not find "+localPipe);
	}
	////////////////////////////////
	
	public BuilderImpl(GraphManager gm, String[] args) {	

		this.gm = gm;
		this.getTempPipeOfStartupSubscriptions().initBuffers();
		this.args = args;
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

	@Deprecated
	public final Builder setTriggerRate(long rateInMS) {
		return setTimerPulseRate(rateInMS);
	}
	
	public final Builder setTimerPulseRate(long rateInMS) {
		timeTriggerRate = rateInMS;
		timeTriggerStart = System.currentTimeMillis()+rateInMS;
		return this;
	}
	
	@Deprecated
	public final Builder setTriggerRate(TimeTrigger trigger) {
		return setTimerPulseRate(trigger);
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

	public final Builder useNetServer() {
		this.useNetServer = true;
		return this;
	}

	public final boolean isUseNetServer() {
		return this.useNetServer;
	}

	
	public final long getTriggerRate() {
		return timeTriggerRate;
	}
	public final long getTriggerStart() {
		return timeTriggerStart;
	}

    public <R extends ReactiveListenerStage> R createReactiveListener(GraphManager gm,  Object listener, Pipe<?>[] inputPipes, Pipe<?>[] outputPipes, int parallelInstance) {
        return (R) new ReactiveListenerStage(gm, listener, inputPipes, outputPipes, this, parallelInstance);
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
		//can be overridden by specific hardware impl if shutdown is supported.
	}

	protected void initChannelBlocker(int maxGoPipeId) {
		channelBlocker = new Blocker(maxGoPipeId+1);
	}

	private final boolean useNetClient(IntHashTable netPipeLookup, Pipe<NetResponseSchema>[] netResponsePipes, Pipe<ClientHTTPRequestSchema>[] netRequestPipes) {

		return !IntHashTable.isEmpty(netPipeLookup) && (netResponsePipes.length!=0) && (netRequestPipes.length!=0);
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
				
		int ideal = idealThreadCount();
		if (threadLimit<=0 && GraphManager.countStages(gm) > 10*ideal) {
			//do not allow the ThreadPerStageScheduler to be used, we must group
			threadLimit = idealThreadCount()*4;//this must be large so give them a few more
			threadLimitHard = true;//must make this a hard limit or we can saturate the system easily.
		}
		
		final StageScheduler scheduler =  threadLimit <= 0 ? new ThreadPerStageScheduler(this.gm): 
			                                                 new FixedThreadsScheduler(this.gm, this.threadLimit, this.threadLimitHard);
		
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

	public final boolean isListeningToSubscription(Object listener) {
		return listener instanceof PubSubListener || listener instanceof StateChangeListener<?>;
	}

	public final boolean isListeningToHTTPResponse(Object listener) {
		return listener instanceof HTTPResponseListener;
	}

	public final boolean isListeningHTTPRequest(Object listener) {
		return listener instanceof RestListener;
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
		this.threadLimitHard = false;
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
		return TrieParserReader.query(localReader, this.extractionParser(routeId), fieldName, 0, fieldName.length, Integer.MAX_VALUE);
	}
		
	@Override
	public final int registerRoute(CharSequence route, byte[] ... headers) {		
		return routerConfig.registerRoute(route, headers);
	}

	public final TrieParser extractionParser(int route) {
		return routerConfig.extractionParser(route).getRuntimeParser();
	}
	
	public final int extractionParserIndexCount(int route) {
		return routerConfig.extractionParser(route).getIndexCount();
	}
	
	public IntHashTable headerToPositionTable(int routeId) {
		return routerConfig.headerToPositionTable(routeId);
	}
	
	public TrieParser headerTrieParser(int routeId) {
		return routerConfig.headerTrieParser(routeId);
	}

	public final ClientCoordinator getClientCoordinator() {
		boolean isTLS = true;
		return useNetClient ? new ClientCoordinator(connectionsInBit, maxPartialResponse, isTLS) : null;
		
	}

	public final Pipe<HTTPRequestSchema> newHTTPRequestPipe(PipeConfig<HTTPRequestSchema> restPipeConfig) {
		Pipe<HTTPRequestSchema> pipe = new Pipe<HTTPRequestSchema>(restPipeConfig) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<HTTPRequestSchema> createNewBlobReader() {
				return new HTTPRequestReader(this);
			}
		};
		return pipe;
	}

	public final boolean isTelemetryEnabled() {
		return isTelemetryEnabled;
	}

	@Override
	public void enableTelemetry(boolean enable) {
		//TODO: this must be fixed because it eliminates the ability to remove this from the code...
		isTelemetryEnabled = enable;
	}

	@Override
	public void enableTelemetry() {
		isTelemetryEnabled = true;
		
		//TODO: build more of the object here if possible
	}
	
	public final long getDefaultSleepRateNS() {
		return defaultSleepRateNS;
	}

	@Override
	public final void setDefaultRate(long ns) {
		defaultSleepRateNS = ns;
	}



	public void buildStages(IntHashTable subscriptionPipeLookup2, IntHashTable netPipeLookup2, GraphManager gm2) {
		Pipe<MessageSubscription>[] subscriptionPipes = GraphManager.allPipesOfType(gm2, MessageSubscription.instance);
		Pipe<NetResponseSchema>[] netResponsePipes = GraphManager.allPipesOfType(gm2, NetResponseSchema.instance);
		Pipe<TrafficOrderSchema>[] orderPipes = GraphManager.allPipesOfType(gm2, TrafficOrderSchema.instance);
		Pipe<MessagePubSub>[] messagePubSub = GraphManager.allPipesOfType(gm2, MessagePubSub.instance);
		Pipe<ClientHTTPRequestSchema>[] netRequestPipes = GraphManager.allPipesOfType(gm2, ClientHTTPRequestSchema.instance);
		Pipe<IngressMessages>[] ingressMessagePipes = GraphManager.allPipesOfType(gm2, IngressMessages.instance);
		
		int commandChannelCount = orderPipes.length;
		int eventSchemas = 0;
		
		IDX_MSG = (IntHashTable.isEmpty(subscriptionPipeLookup2) && subscriptionPipes.length==0 && messagePubSub.length==0) ? -1 : eventSchemas++;
		IDX_NET = useNetClient(netPipeLookup2, netResponsePipes, netRequestPipes) ? eventSchemas++ : -1;
						
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
			masterGoOut[IDX_NET] = new Pipe[netResponsePipes.length];
			masterAckIn[IDX_NET] = new Pipe[netResponsePipes.length];
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

			if (hasConnections) {
				TrafficCopStage trafficCopStage = new TrafficCopStage(gm, timeout, orderPipes[t], ackIn, goOut);
			} else {
				PipeCleanerStage.newInstance(gm, orderPipes[t]);
			}
		}
		
		
		
		
		initChannelBlocker(maxGoPipeId);
		
		
		////////
		//create the network client stages
		////////
		if (useNetClient(netPipeLookup2, netResponsePipes, netRequestPipes)) {
			
			if (masterGoOut[IDX_NET].length != masterAckIn[IDX_NET].length) {
				throw new UnsupportedOperationException(masterGoOut[IDX_NET].length+"!="+masterAckIn[IDX_NET].length);
			}
			if (masterGoOut[IDX_NET].length != netRequestPipes.length) {
				throw new UnsupportedOperationException(masterGoOut[IDX_NET].length+"!="+netRequestPipes.length);
			}
			
			assert(masterGoOut[IDX_NET].length == masterAckIn[IDX_NET].length);
			assert(masterGoOut[IDX_NET].length == netRequestPipes.length);
			
				
			PipeConfig<NetPayloadSchema> clientNetRequestConfig = new PipeConfig<NetPayloadSchema>(NetPayloadSchema.instance,4,16000); 		
		
			//BUILD GRAPH
			
			int connectionsInBits=10;			
			int maxPartialResponses=4;
			boolean isTLS = true;
			ClientCoordinator ccm = new ClientCoordinator(connectionsInBits, maxPartialResponses, isTLS);
		
			//TODO: tie this in tonight.
			int inputsCount = 1;
			int outputsCount = 1;
			Pipe<NetPayloadSchema>[] clientRequests = new Pipe[outputsCount];
			int r = outputsCount;
			while (--r>=0) {
				clientRequests[r] = new Pipe<NetPayloadSchema>(clientNetRequestConfig);		
			}
			HTTPClientRequestStage requestStage = new HTTPClientRequestStage(gm, this, ccm, netRequestPipes, masterGoOut[IDX_NET], masterAckIn[IDX_NET], clientRequests);
						
			NetGraphBuilder.buildHTTPClientGraph(gm, maxPartialResponses, ccm, netPipeLookup2, 10, 1<<15, clientRequests, netResponsePipes);
						
		}
		
		/////////
		//always create the pub sub and state management stage?
		/////////
		//TODO: only create when subscriptionPipeLookup is not empty and subscriptionPipes has zero length.
		if (IDX_MSG<0) {
			logger.trace("saved some resources by not starting up the unused pub sub service.");
		} else {
			//logger.info("builder created pub sub");
		 	createMessagePubSubStage(subscriptionPipeLookup2, ingressMessagePipes, messagePubSub, masterGoOut[IDX_MSG], masterAckIn[IDX_MSG], subscriptionPipes);
		}
	}


	protected int populateGoAckPipes(int maxGoPipeId, Pipe<TrafficReleaseSchema>[][] masterGoOut,
			Pipe<TrafficAckSchema>[][] masterAckIn, Pipe<TrafficReleaseSchema>[] goOut, Pipe<TrafficAckSchema>[] ackIn,
			int p) {
		addToLastNonNull(masterGoOut[p], goOut[p] = new Pipe<TrafficReleaseSchema>(releasePipesConfig));
		
		maxGoPipeId = Math.max(maxGoPipeId, goOut[p].id);				
		
		addToLastNonNull(masterAckIn[p], ackIn[p] = new Pipe<TrafficAckSchema>(ackPipesConfig));
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
	public MQTTConfigImpl useMQTT(CharSequence host, int port, CharSequence clientId) {		
		return mqtt = new MQTTConfigImpl(host, port, clientId, this, defaultSleepRateNS);
	}
	
	@Override
	public void privateTopics(String... topic) {
		privateTopics=topic;
	}

	@Override
	public String[] args() {
		return args;
	}



}