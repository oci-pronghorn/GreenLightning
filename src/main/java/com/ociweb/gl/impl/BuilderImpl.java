package com.ociweb.gl.impl;

import com.ociweb.gl.api.*;
import com.ociweb.gl.api.transducer.HTTPResponseListenerTransducer;
import com.ociweb.gl.api.transducer.PubSubListenerTransducer;
import com.ociweb.gl.api.transducer.RestListenerTransducer;
import com.ociweb.gl.api.transducer.StateChangeListenerTransducer;
import com.ociweb.gl.impl.http.client.HTTPClientConfigImpl;
import com.ociweb.gl.impl.http.server.HTTPResponseListenerBase;
import com.ociweb.gl.impl.mqtt.MQTTConfigImpl;
import com.ociweb.gl.impl.schema.*;
import com.ociweb.gl.impl.stage.*;
import com.ociweb.gl.impl.telemetry.TelemetryConfigImpl;
import com.ociweb.json.JSONExtractorImpl;
import com.ociweb.json.JSONType;
import com.ociweb.json.JSONAccumRule;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.HTTPServerConfig;
import com.ociweb.pronghorn.network.HTTPServerConfigImpl;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.config.*;
import com.ociweb.pronghorn.network.http.CompositePath;
import com.ociweb.pronghorn.network.http.CompositeRoute;
import com.ociweb.pronghorn.network.http.FieldExtractionDefinitions;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStageConfig;
import com.ociweb.pronghorn.network.http.HTTPClientRequestStage;
import com.ociweb.pronghorn.network.schema.*;
import com.ociweb.pronghorn.pipe.*;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.PronghornStageProcessor;
import com.ociweb.pronghorn.stage.file.FileGraphBuilder;
import com.ociweb.pronghorn.stage.file.NoiseProducer;
import com.ociweb.pronghorn.stage.file.schema.*;
import com.ociweb.pronghorn.stage.route.ReplicatorStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.struct.StructBuilder;
import com.ociweb.pronghorn.struct.StructRegistry;
import com.ociweb.pronghorn.util.*;
import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.json.decode.JSONTable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

public class BuilderImpl implements Builder {

	private static final int MIN_CYCLE_RATE = 1; //cycle rate can not be zero

	protected static final int MINIMUM_TLS_BLOB_SIZE = 1<<15;

	protected long timeTriggerRate;
	protected long timeTriggerStart;

	private Runnable cleanShutdownRunnable;
	private Runnable dirtyShutdownRunnable;
    
    int subscriptionPipeIdx = 0; //this implementation is dependent upon graphManager returning the pipes in the order created!
    final IntHashTable subscriptionPipeLookup = new IntHashTable(10);//NOTE: this is a maximum of 1024 listeners

	private Blocker channelBlocker;

	public final GraphManager gm;
	public final ArgumentParser args;
	
	private int threadLimit = -1;
	private boolean threadLimitHard = false;
	private boolean hasPrivateTopicsChecked = false;
	private boolean isAllPrivateTopics = false;

	private static final int DEFAULT_LENGTH = 16;

	protected static final long MS_TO_NS = 1_000_000;

	private static final Logger logger = LoggerFactory.getLogger(BuilderImpl.class);

	public final PipeConfigManager pcm = new PipeConfigManager();

	public Enum<?> beginningState;
    private int parallelismTracks = 1;//default is one
	private static final int BehaviorMask = 1<<31;//high bit on
	
	///////////////////////////////////////////////////////////////////
    //all non shutdown listening reactors will be shutdown only after the listeners have finished.
    public AtomicInteger liveShutdownListeners = new AtomicInteger();
    public AtomicInteger totalLiveReactors = new AtomicInteger();    
    public AtomicBoolean shutdownRequsted = new AtomicBoolean(false);
    public boolean shutdownIsComplete = false;
    public Runnable lastCall; //TODO: group these into an object for ReactiveListenerStage to use...
    /////////////////////////////////////////////

	/////////////////
	///Pipes for initial startup declared subscriptions. (Not part of graph)
    //TODO: should be zero unless startup is used.
	private final int maxStartupSubs = 256; //TODO: make a way to adjust this outside???
	private final int maxTopicLengh  = 128;
	private Pipe<MessagePubSub> tempPipeOfStartupSubscriptions;
	/////////////////
	/////////////////
	
    public int netResponsePipeIdxCounter = 0;//this implementation is dependent upon graphManager returning the pipes in the order created!
	private final boolean enableAutoPrivateTopicDiscovery = true;
	
    
    private long defaultSleepRateNS = 5_000;// should normally be between 900 and 20_000; 
    
	private final int shutdownTimeoutInSeconds = 1;

	protected ReentrantLock devicePinConfigurationLock = new ReentrantLock();

	private MQTTConfigImpl mqtt = null;
	private HTTPServerConfigImpl server = null;
	private TelemetryConfigImpl telemetry = null;
	private HTTPClientConfigImpl client = null;
	private ClientCoordinator ccm;

	protected int IDX_MSG = -1;
	protected int IDX_NET = -1;
	    
    private StageScheduler scheduler;
	   
    ///////
	//These topics are enforced so that they only go from one producer to a single consumer
	//No runtime subscriptions can pick them up
	//They do not go though the public router
	//private topics never share a pipe so the topic is not sent on the pipe only the payload
	//private topics are very performant and much more secure than pub/sub.
	//private topics have their own private pipe and can not be "imitated" by public messages
	//WARNING: private topics do not obey traffic cops and allow for immediate communications.
	///////
	//private String[] privateTopics = null;

	
    ////////////////////////////
    ///gather and store the server module pipes
    /////////////////////////////
    private ArrayList<Pipe<HTTPRequestSchema>>[][] collectedHTTPRequestPipes;
	private ArrayList<Pipe<ServerResponseSchema>>[] collectedServerResponsePipes;
	
	
	//////////////////////////////
	//support for REST modules and routing
	//////////////////////////////
	public final HTTPSpecification<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>
	             httpSpec = HTTPSpecification.defaultSpec();
	
	private HTTP1xRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>
	             routerConfig;	//////////////////////////////
	//////////////////////////////

	public void usePrivateTopicsExclusively() {
		if (hasPrivateTopicsChecked) {
			throw new UnsupportedOperationException("Must set in declare configuration section before startup");
		}
		isAllPrivateTopics = true;
	}

	/**
	 *
	 * @return false
	 */
	public boolean isAllPrivateTopics() {
		hasPrivateTopicsChecked = true;
		return isAllPrivateTopics;
	}
	    
    public final ReactiveOperators operators;

    //NOTE: needs re-work to be cleaned up
    private final HashMap<String,AtomicInteger> behaviorNames = new HashMap<String,AtomicInteger>();

	/**
	 * a method to validate fullName and add it to behaviorNames
	 * @param behaviorName String arg used in behaviorNames
	 * @param trackId int arg used with fullName if arg >= 0
	 * @return behaviorName + trackId
	 */
	//will throw if a duplicate stage name is detected.
    public String validateUniqueName(String behaviorName, int trackId) {
    	
    	String fullName = behaviorName;
    	//confirm stage name is not found..
    	if (behaviorNames.containsKey(behaviorName)) {
    		throw new UnsupportedOperationException("Duplicate name detected: "+behaviorName);
    	}
    	
    	if (trackId>=0) {
    		fullName = behaviorName+"."+trackId;
    		//additional check for name+"."+trackId
    		if (behaviorNames.containsKey(fullName)) {
        		throw new UnsupportedOperationException("Duplicate name detected: "+fullName);
        	}
    		//add the name+"."+name
    		behaviorNames.put(fullName,new AtomicInteger());//never add the root since we are watching that no one else did.
    	} else {
    		//add the stage name
    		behaviorNames.put(behaviorName,new AtomicInteger());
    	}
    	
    	return fullName;
    }
    
    
    //TODO: replace with add only general int to Object hash table?
    ////////////////////
    ///////////////////
    private IntHashTable netPipeLookup = new IntHashTable(7);//Initial default size

	/**
	 * a method that doubles IntHashTable if necessary and logs warning if !IntHashTable.setItem(netPipeLookup, uniqueId, pipeIdx)
	 * @param uniqueId int arg to specify id
	 * @param pipeIdx int arg to specify index
	 */
	public void registerHTTPClientId(final int uniqueId, int pipeIdx) {
		if (0==uniqueId) {
			throw new UnsupportedOperationException("Zero can not be used as the uniqueId");
		}
		if ( (IntHashTable.count(netPipeLookup)<<1) >= IntHashTable.size(netPipeLookup) ) {
			//must grow first since we are adding many entries
			netPipeLookup = IntHashTable.doubleSize(netPipeLookup);			
		}
		boolean addedItem = IntHashTable.setItem(netPipeLookup, uniqueId, pipeIdx);
        if (!addedItem) {
        	logger.warn("The route {} has already been assigned to a listener and can not be assigned to another.\n"
        			+ "Check that each HTTP Client consumer does not share an Id with any other.",uniqueId);
        }
    }

	/**
	 * A method to look up the client pipes http
	 * @param routeId route to be looked for in pipe
	 * @return IntHashTable.getItem(netPipeLookup, routeId)
	 */
	public int lookupHTTPClientPipe(int routeId) {
    	return IntHashTable.getItem(netPipeLookup, routeId);
    }

	public boolean hasHTTPClientPipe(int routeId) {
    	return IntHashTable.hasItem(netPipeLookup, routeId);
    }
///////////////////////////
	////////////////
	
	
	
	
	
	/**
	 *
	 * @return index message
	 */
	public int pubSubIndex() {
		return IDX_MSG;
	}

	/**
	 *
	 * @return net index
	 */
	public int netIndex() {
		return IDX_NET;
	}

	/**
	 *
	 * @param count int arg used to set count in MsgCommandChannel.publishGo
	 * @param gcc MsgCommandChannel<?>
	 */
	public void releasePubSubTraffic(int count, MsgCommandChannel<?> gcc) { //TODO: non descriptive arg name gcc??
		MsgCommandChannel.publishGo(count, IDX_MSG, gcc);
	}

	/**
	 *
	 * @return ccm
	 */
	public ClientCoordinator getClientCoordinator() {
		return ccm;
	}

	@Override
	public HTTPServerConfig useHTTP1xServer(int bindPort) {
		if (server != null) {
			throw new RuntimeException("Server already enabled");
		}
		
		return server = new HTTPServerConfigImpl(bindPort, this.pcm, gm.recordTypeData);
	}

	public final HTTPServerConfig getHTTPServerConfig() {
		return this.server;
	}

	/**
	 *
	 * @param b Behavior arg used in System.identityHashCode
	 * @return Behavior Mask
	 */
    public int behaviorId(Behavior b) {
    	return BehaviorMask | System.identityHashCode(b);
    }

    
    public final HTTP1xRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>
    	routerConfig() {
    	if (null==routerConfig) {
    		
    		routerConfig = new HTTP1xRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>(
    				httpSpec,
    				server.connectionStruct()); 
    	
    	}    	
    	return routerConfig;
    }

	/**
	 * A method to append pipe mapping and group ids when invoked by builder
	 * @param pipe Pipe<HTTPRequestSchema> arg used for routerConfig().appendPipeIdMappingForIncludedGroupIds
	 * @param parallelId int arg used for routerConfig().appendPipeIdMappingForIncludedGroupIds
	 * @param groupIds int arg used for routerConfig().appendPipeIdMappingForIncludedGroupIds
	 * @return routerConfig().appendPipeIdMappingForIncludedGroupIds(pipe, parallelId, collectedHTTPRequestPipes, groupIds)
	 */
	public final boolean appendPipeMappingIncludingGroupIds(Pipe<HTTPRequestSchema> pipe,
											            int parallelId,
											            int ... groupIds) {
		lazyCreatePipeLookupMatrix();
		return routerConfig().appendPipeIdMappingForIncludedGroupIds(pipe, parallelId, collectedHTTPRequestPipes, groupIds);
	}

	/**
	 * A method to append pipe mapping but not including group ids when invoked by builder
	 * @param pipe Pipe<HTTPRequestSchema> arg used for routerConfig().appendPipeIdMappingForExcludedGroupIds
	 * @param parallelId int arg used for routerConfig().appendPipeIdMappingForExcludedGroupIds
	 * @param groupIds int arg used for routerConfig().appendPipeIdMappingForExcludedGroupIds
	 * @return routerConfig().appendPipeIdMappingForExcludedGroupIds(pipe, parallelId, collectedHTTPRequestPipes, groupIds)
	 */
	public final boolean appendPipeMappingExcludingGroupIds(Pipe<HTTPRequestSchema> pipe,
					                            int parallelId,
					                            int ... groupIds) {
		lazyCreatePipeLookupMatrix();
		return routerConfig().appendPipeIdMappingForExcludedGroupIds(pipe, parallelId, collectedHTTPRequestPipes, groupIds);
	}

	/**
	 *
	 * @param pipe Pipe<HTTPRequestSchema> arg used for routerConfig().appendPipeIdMappingForAllGroupIds
	 * @param parallelId int arg used for routerConfig().appendPipeIdMappingForAllGroupIds
	 * @return routerConfig().appendPipeIdMappingForAllGroupIds(pipe, parallelId, collectedHTTPRequestPipes)
	 */
	public final boolean appendPipeMappingAllGroupIds(Pipe<HTTPRequestSchema> pipe,
									int parallelId) {
		lazyCreatePipeLookupMatrix();
		return routerConfig().appendPipeIdMappingForAllGroupIds(pipe, parallelId, collectedHTTPRequestPipes);
	}

	/**
	 *
	 * @return collectedHTTPRequestPipes
	 */
	final ArrayList<Pipe<HTTPRequestSchema>>[][] targetPipeMapping() {
		lazyCreatePipeLookupMatrix();
		return collectedHTTPRequestPipes;
	}

	/**
	 *
	 * @param r int arg used as index in collectedHTTPRequestPipes array
	 * @param p int arg used as index in collectedHTTPRequestPipes array
	 * @return null!= collectedHTTPRequestPipes ? collectedHTTPRequestPipes[r][p] : new ArrayList<Pipe<HTTPRequestSchema>>()
	 */
	public final ArrayList<Pipe<HTTPRequestSchema>> buildFromRequestArray(int r, int p) {
		assert(null== collectedHTTPRequestPipes || r< collectedHTTPRequestPipes.length);
		assert(null== collectedHTTPRequestPipes || p< collectedHTTPRequestPipes[r].length) : "p "+p+" vs "+ collectedHTTPRequestPipes[r].length;
		return null!= collectedHTTPRequestPipes ? collectedHTTPRequestPipes[r][p] : new ArrayList<Pipe<HTTPRequestSchema>>();
	}
	
	
	private void lazyCreatePipeLookupMatrix() {
		if (null== collectedHTTPRequestPipes) {
			
			int parallelism = parallelTracks();
			int routesCount = routerConfig().totalPathsCount();
			
			assert(parallelism>=1);
			assert(routesCount>-1);	
			
			//for catch all route since we have no specific routes.
			if (routesCount==0) {
				routesCount = 1;
			}
			
			collectedHTTPRequestPipes = (ArrayList<Pipe<HTTPRequestSchema>>[][]) new ArrayList[parallelism][routesCount];
			
			int p = parallelism;
			while (--p>=0) {
				int r = routesCount;
				while (--r>=0) {
					collectedHTTPRequestPipes[p][r] = new ArrayList();
				}
			}
		}
	}


	/**
	 *
	 * @param netResponse arg used for collectedServerResponse
	 * @param parallelInstanceId int arg used for collectedServerResponse
	 */
	public final void recordPipeMapping(Pipe<ServerResponseSchema> netResponse, int parallelInstanceId) {
		
		if (null == collectedServerResponsePipes) {
			int parallelism = parallelTracks();
			collectedServerResponsePipes =  (ArrayList<Pipe<ServerResponseSchema>>[]) new ArrayList[parallelism];
			
			int p = parallelism;
			while (--p>=0) {
				collectedServerResponsePipes[p] = new ArrayList();
			}
			
		}
		
		collectedServerResponsePipes[parallelInstanceId].add(netResponse);
		
	}

	/**
	 *
	 * @param r int arg used in collectedServerResponsePipes
	 * @return (Pipe<ServerResponseSchema>[]) list.toArray(new Pipe[list.size()])
	 */
	public final Pipe<ServerResponseSchema>[] buildToOrderArray(int r) {
		if (null==collectedServerResponsePipes || collectedServerResponsePipes.length==0) {
			return new Pipe[0];
		} else {
			ArrayList<Pipe<ServerResponseSchema>> list = collectedServerResponsePipes[r];
			return (Pipe<ServerResponseSchema>[]) list.toArray(new Pipe[list.size()]);
		}
	}

	/**
	 *
	 * @param config
	 * @param parallelInstanceId int arg used for recordPipeMapping
	 * @return pipe
	 */
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
		this.args = new ArgumentParser(args);
		
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

	/**
	 *
	 * @param rateInMS Rate in milliseconds to trigger events.
	 *
	 * @return this
	 */
	public final Builder setTimerPulseRate(long rateInMS) {
		timeTriggerRate = rateInMS;
		timeTriggerStart = System.currentTimeMillis()+rateInMS;
		return this;
	}

	/**
	 *
	 * @param trigger {@link TimeTrigger} to use for controlling trigger rate.
	 *
	 * @return this
	 */
	public final Builder setTimerPulseRate(TimeTrigger trigger) {	
		long period = trigger.getRate();
		timeTriggerRate = period;
		long now = System.currentTimeMillis();		
		long soFar = (now % period);		
		timeTriggerStart = (now - soFar) + period;				
		return this;
	}

	@Override
	public final HTTPClientConfig useNetClient() {
		return useNetClient(TLSCertificates.defaultCerts);
	}

	@Override
	public final HTTPClientConfig useInsecureNetClient() {
		return useNetClient((TLSCertificates) null);
	}

	@Override
	public HTTPClientConfigImpl useNetClient(TLSCertificates certificates) {
		if (client != null) throw new RuntimeException("Client already enabled");
		this.client = new HTTPClientConfigImpl(certificates);
		this.client.beginDeclarations();
		return client;
	}

	/**
	 *
	 * @return this.client
	 */
	public final HTTPClientConfig getHTTPClientConfig() {
		return this.client;
	}

	/**
	 *
	 * @return timeTriggerRate
	 */
	public final long getTriggerRate() {
		return timeTriggerRate;
	}

	/**
	 *
	 * @return timeTriggerStart
	 */
	public final long getTriggerStart() {
		return timeTriggerStart;
	}

	/**
	 *
	 * @param gm {@link GraphManager} arg used in new ReactiveListenerStage
	 * @param listener {@link Behavior} arg used in ReactiveListenerStage
	 * @param inputPipes arg used in ReactiveListenerStage
	 * @param outputPipes arg used in ReactiveListenerStage
	 * @param consumers arg used in ReactiveListenerStage
	 * @param parallelInstance int arg used in ReactiveListenerStage
	 * @param nameId String arg used in ReactiveListenerStage
	 * @return new reactive listener stage
	 */
    public <R extends ReactiveListenerStage> R createReactiveListener(GraphManager gm,  Behavior listener, 
    		                		Pipe<?>[] inputPipes, Pipe<?>[] outputPipes, 
    		                		ArrayList<ReactiveManagerPipeConsumer> consumers,
    		                		int parallelInstance, String nameId) {
    	assert(null!=listener);
    	
    	return (R) new ReactiveListenerStage(gm, listener, 
    			                             inputPipes, outputPipes, 
    			                             consumers, this, parallelInstance, nameId);
    }

    @Deprecated
	public <G extends MsgCommandChannel> G newCommandChannel(
												int features,
			                                    int parallelInstanceId,
			                                    PipeConfigManager pcm
			                                ) {
		return (G) new GreenCommandChannel(gm, this, features, parallelInstanceId,
				                           pcm);
	}

    /**
     *
     * @param parallelInstanceId MsgCommandChannel arg used for GreenCommandChannel(gm, this, 0, parallelInstanceId, pcm)
     * @param pcm int arg used for GreenCommandChannel(gm, this, 0, parallelInstanceId, pcm)
     * @return new GreenCommandChannel(gm, this, 0, parallelInstanceId, pcm)
     */
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

	protected final void createMessagePubSubStage(
			MsgRuntime<?,?> runtime,
			IntHashTable subscriptionPipeLookup,
			Pipe<IngressMessages>[] ingressMessagePipes,
			Pipe<MessagePubSub>[] messagePubSub,
			Pipe<TrafficReleaseSchema>[] masterMsggoOut, 
			Pipe<TrafficAckSchema>[] masterMsgackIn, 
			Pipe<MessageSubscription>[] subscriptionPipes) {


		new MessagePubSubTrafficStage(this.gm, runtime, subscriptionPipeLookup, this, 
				                ingressMessagePipes, messagePubSub, 
				                masterMsggoOut, masterMsgackIn, subscriptionPipes);


	}

    /**
     *
     * @param runtime final MsgRuntime arg used in createScheduler
     * @return createScheduler(runtime, null, null)
     */
	public StageScheduler createScheduler(final MsgRuntime runtime) {
		return createScheduler(runtime, null, null);
	}

    /**
     *
     * @param runtime final MsgRuntime arg used to check if  arg.builder.threadLimit > 0
     * @param cleanRunnable final Runnable arg used with runtime.addCleanShutdownRunnable
     * @param dirtyRunnable final Runnable arg used with runtime.addDirtyShutdownRunnable
     * @return scheduler
     */
	public StageScheduler createScheduler(final MsgRuntime runtime,
										  final	Runnable cleanRunnable,
										  final	Runnable dirtyRunnable) {

		final StageScheduler scheduler = 
				 runtime.builder.threadLimit>0 ?				
		         StageScheduler.defaultScheduler(gm, 
		        		 runtime.builder.threadLimit, 
		        		 runtime.builder.threadLimitHard) :
		         StageScheduler.defaultScheduler(gm);

		runtime.addCleanShutdownRunnable(cleanRunnable);
		runtime.addDirtyShutdownRunnable(dirtyRunnable);

		return scheduler;
	}

	private final int getShutdownSeconds() {
		return shutdownTimeoutInSeconds;
	}

	
	protected final ChildClassScannerVisitor deepListener = new ChildClassScannerVisitor<ListenerTransducer>() {
		@Override
		public boolean visit(ListenerTransducer child, Object topParent, String name) {
			return false;
		}		
	};
	
	public final boolean isListeningToSubscription(Behavior listener) {
			
		//NOTE: we only call for scan if the listener is not already of this type
		return listener instanceof PubSubMethodListenerBase ||
			   listener instanceof StateChangeListenerBase<?> 
		       || !ChildClassScanner.visitUsedByClass(null, listener, deepListener, PubSubListenerTransducer.class)
		       || !ChildClassScanner.visitUsedByClass(null, listener, deepListener, StateChangeListenerTransducer.class);
	}

	public final boolean isListeningToHTTPResponse(Object listener) {
		return listener instanceof HTTPResponseListenerBase ||
			   //will return false if HTTPResponseListenerBase was encountered
			  !ChildClassScanner.visitUsedByClass(null, listener, deepListener, HTTPResponseListenerTransducer.class);
	}

	public final boolean isListeningHTTPRequest(Object listener) {
		return listener instanceof RestMethodListenerBase ||
			    //will return false if RestListenerBase was encountered
			   !ChildClassScanner.visitUsedByClass(null, listener, deepListener, RestListenerTransducer.class);
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


	
	public final void addStartupSubscription(CharSequence topic, int systemHash, int parallelInstance) {

		Pipe<MessagePubSub> pipe = getTempPipeOfStartupSubscriptions();

		if (PipeWriter.tryWriteFragment(pipe, MessagePubSub.MSG_SUBSCRIBE_100)) {
			
    		DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(pipe);
    		output.openField();
    		output.append(topic);
    		//this is in a track and may need a suffix.
    		if (parallelInstance>=0) { 
    			if (BuilderImpl.hasNoUnscopedTopics()) {
    				//add suffix..
    				output.append('/');
    				Appendables.appendValue(output, parallelInstance);
    			} else {
    				if (BuilderImpl.notUnscoped(TrieParserReaderLocal.get(), output)) {
    					//add suffix
        				output.append('/');
        				Appendables.appendValue(output, parallelInstance);
    				}
    			}
    		}
    		
    		output.closeHighLevelField(MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1);	
			//PipeWriter.writeUTF8(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
	
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
		
		if (telemetry != null && threadLimit>0 && threadLimit<64) {
			//must ensure telemetry has the threads it needs.
			threadLimit+=2; 
		}
		
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

	@Override
	public final int parallelTracks() {
		return parallelismTracks;
	}
	
	@Override
	public final void parallelTracks(int trackCount) {
		assert(trackCount>0);
		parallelismTracks = trackCount;
	}

	
	private final TrieParserReader localReader = new TrieParserReader(true);
	
	@Override
	public long fieldId(int routeId, byte[] fieldName) {	
		return TrieParserReader.query(localReader, this.routeExtractionParser(routeId), fieldName, 0, fieldName.length, Integer.MAX_VALUE);
	}
	
	@Override
	@Deprecated
	public final CompositePath defineRoute(JSONExtractorCompleted extractor, HTTPHeader ... headers) {
		return routerConfig().registerCompositeRoute(extractor, headers);
	}
	
//	@Override
//	@Deprecated
//	public final CompositePath defineRoute(HTTPHeader ... headers) {
//		return routerConfig().registerCompositeRoute( headers);
//	}
	
	@Override
	public final RouteDefinition defineRoute(HTTPHeader ... headers) {
		
		return new RouteDefinition() {
	
			CompositeRoute route = null;
			
			@Override
			public CompositeRoute path(CharSequence path) {
				return (null==route) ? routerConfig().registerCompositeRoute(headers).path(path) :  route;
			}
			
			@Override
			public ExtractedJSONFieldsForRoute parseJSON() {
								
				
				return new ExtractedJSONFieldsForRoute() {

					@Override
					public CompositeRoute path(CharSequence path) {
						return route = routerConfig().registerCompositeRoute(ex.finish(), headers).path(path);
					}					
					
					JSONTable<JSONExtractor> ex = new JSONExtractor().begin();
					
					@Override
					public <T extends Enum<T>> ExtractedJSONFieldsForRoute stringField(boolean isAligned, JSONAccumRule accumRule,
																				String extractionPath, T field) {
						Object temp = ex.element(JSONType.TypeString, isAligned, accumRule).asField(extractionPath, field);
						assert(temp == ex) : "internal error, the same instance should have been returned";
						return this;
					}
					
					@Override
					public <T extends Enum<T>> ExtractedJSONFieldsForRoute stringField(String extractionPath, T field) {
						Object temp = ex.element(JSONType.TypeString, false, null).asField(extractionPath, field);
						assert(temp == ex) : "internal error, the same instance should have been returned";
						return this;
					}
	
					
					@Override
					public <T extends Enum<T>> ExtractedJSONFieldsForRoute integerField(boolean isAligned, JSONAccumRule accumRule,
							String extractionPath, T field) {
						Object temp = ex.element(JSONType.TypeInteger, isAligned, accumRule).asField(extractionPath, field);
						assert(temp == ex) : "internal error, the same instance should have been returned";
						return this;
					}
					
					@Override
					public <T extends Enum<T>> ExtractedJSONFieldsForRoute integerField(String extractionPath, T field) {
						Object temp = ex.element(JSONType.TypeInteger, false, null).asField(extractionPath, field);
						assert(temp == ex) : "internal error, the same instance should have been returned";
						return this;
					}
					
					@Override
					public <T extends Enum<T>> ExtractedJSONFieldsForRoute decimalField(boolean isAligned, JSONAccumRule accumRule,
							String extractionPath, T field) {
						Object temp = ex.element(JSONType.TypeDecimal, isAligned, accumRule).asField(extractionPath, field);
						assert(temp == ex) : "internal error, the same instance should have been returned";
						return this;
					}
					
					@Override
					public <T extends Enum<T>> ExtractedJSONFieldsForRoute decimalField(String extractionPath, T field) {
						Object temp = ex.element(JSONType.TypeDecimal, false, null).asField(extractionPath, field);
						assert(temp == ex) : "internal error, the same instance should have been returned";
						return this;
					}
					
					@Override
					public <T extends Enum<T>> ExtractedJSONFieldsForRoute booleanField(boolean isAligned, JSONAccumRule accumRule,
							String extractionPath, T field) {
						Object temp = ex.element(JSONType.TypeBoolean, isAligned, accumRule).asField(extractionPath, field);
						assert(temp == ex) : "internal error, the same instance should have been returned";
						return this;
					}
					
					@Override
					public <T extends Enum<T>> ExtractedJSONFieldsForRoute booleanField(String extractionPath, T field) {
						Object temp = ex.element(JSONType.TypeBoolean, false, null).asField(extractionPath, field);
						assert(temp == ex) : "internal error, the same instance should have been returned";
						return this;
					}
				};
			}			
			
		

		};
	}
	
	
	@Override
	public final JSONExtractor defineJSONSDecoder() {
		return new JSONExtractor();
	}
	@Override
	public final JSONExtractor defineJSONSDecoder(boolean writeDot) {
		return new JSONExtractor(writeDot);
	}

	public final TrieParser routeExtractionParser(int route) {
		return routerConfig().extractionParser(route).getRuntimeParser();
	}
	
	public final int routeExtractionParserIndexCount(int route) {
		return routerConfig().extractionParser(route).getIndexCount();
	}

	public final Pipe<HTTPRequestSchema> newHTTPRequestPipe(PipeConfig<HTTPRequestSchema> restPipeConfig) {
		final boolean hasNoRoutes = (0==routerConfig().totalPathsCount());
		Pipe<HTTPRequestSchema> pipe = new Pipe<HTTPRequestSchema>(restPipeConfig) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<HTTPRequestSchema> createNewBlobReader() {
				return new HTTPRequestReader(this, hasNoRoutes, routerConfig());
			}
		};
		return pipe;
	}
	
	@Override
	public TelemetryConfig enableTelemetry() {
		return enableTelemetry(null, TelemetryConfig.defaultTelemetryPort);
	}
	
	@Override
	public TelemetryConfig enableTelemetry(int port) {
		return enableTelemetry(null, port);
	}

	@Override
	public TelemetryConfig enableTelemetry(String host) {
		return enableTelemetry(host, TelemetryConfig.defaultTelemetryPort);
	}
	
	@Override
	public TelemetryConfig enableTelemetry(String host, int port) {
		if (telemetry != null) {
			throw new RuntimeException("Telemetry already enabled");
		}

		this.telemetry = new TelemetryConfigImpl(host, port);
		this.telemetry.beginDeclarations();
		
		if (threadLimit>0 && threadLimit>0 && threadLimit<64) {
			//we must increase the thread limit to ensure telemetry is not started
			threadLimit += 2;			
		}
		
		return this.telemetry;
	}

	public TelemetryConfig getTelemetryConfig() {
		return this.telemetry;
	}
	
	public final long getDefaultSleepRateNS() {
		return defaultSleepRateNS;
	}

	@Override
	public final void setDefaultRate(long ns) {
		//new Exception("setting new rate "+ns).printStackTrace();
		defaultSleepRateNS = Math.max(ns, MIN_CYCLE_RATE); //protect against negative and zero
	}

	public void buildStages(MsgRuntime runtime) {

		IntHashTable subscriptionPipeLookup2 = MsgRuntime.getSubPipeLookup(runtime); 
		GraphManager gm = MsgRuntime.getGraphManager(runtime);

		Pipe<MessageSubscription>[] subscriptionPipes = GraphManager.allPipesOfTypeWithNoProducer(gm, MessageSubscription.instance);
		Pipe<NetResponseSchema>[] httpClientResponsePipes = GraphManager.allPipesOfTypeWithNoProducer(gm, NetResponseSchema.instance);
		
		
		Pipe<TrafficOrderSchema>[] orderPipes = GraphManager.allPipesOfTypeWithNoConsumer(gm, TrafficOrderSchema.instance);		
		Pipe<ClientHTTPRequestSchema>[] httpClientRequestPipes = GraphManager.allPipesOfTypeWithNoConsumer(gm, ClientHTTPRequestSchema.instance);			
		Pipe<MessagePubSub>[] messagePubSub = GraphManager.allPipesOfTypeWithNoConsumer(gm, MessagePubSub.instance);
		Pipe<IngressMessages>[] ingressMessagePipes = GraphManager.allPipesOfTypeWithNoConsumer(gm, IngressMessages.instance);
		
		//TODO: no longer right now that we have no cops..
		int commandChannelCount = orderPipes.length;
		
		
		int eventSchemas = 0;
		
		IDX_MSG = (IntHashTable.isEmpty(subscriptionPipeLookup2) 
				    && subscriptionPipes.length==0 && messagePubSub.length==0) ? -1 : eventSchemas++;
		IDX_NET = useNetClient(httpClientRequestPipes) ? eventSchemas++ : -1;
						
        long timeout = 240_000; //240 seconds so we have time to capture telemetry.
		
		int maxGoPipeId = 0;
					
								
		Pipe<TrafficReleaseSchema>[][] masterGoOut = new Pipe[eventSchemas][0];
		Pipe<TrafficAckSchema>[][]     masterAckIn = new Pipe[eventSchemas][0];

		if (IDX_MSG >= 0) {
			masterGoOut[IDX_MSG] = new Pipe[messagePubSub.length];
			masterAckIn[IDX_MSG] = new Pipe[messagePubSub.length];
		}		
		if (IDX_NET >= 0) {

			assert(httpClientRequestPipes.length>0);
			masterGoOut[IDX_NET] = new Pipe[httpClientRequestPipes.length];
			masterAckIn[IDX_NET] = new Pipe[httpClientRequestPipes.length];
		}		
				
		int copGoAck = commandChannelCount;
		//logger.info("\ncommand channel count to be checked {}",copGoAck);
		while (--copGoAck>=0) {
		
			Pipe<TrafficReleaseSchema>[] goOut = new Pipe[eventSchemas];
			Pipe<TrafficAckSchema>[] ackIn = new Pipe[eventSchemas];
			
			//only setup the go and in pipes if the cop is used.
			if (null != orderPipes[copGoAck]) {

				//this only returns the features associated with this order pipe, eg only the ones needing a cop.
				final int features = getFeatures(gm, orderPipes[copGoAck]);
		
				if ((features&Behavior.DYNAMIC_MESSAGING) != 0) {
			 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_MSG);
				}
				if ((features&Behavior.NET_REQUESTER) != 0) {
			 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_NET);
				}
				//logger.info("\nnew traffic cop for graph {}",gm.name);
				TrafficCopStage.newInstance(gm, 
											timeout, orderPipes[copGoAck], 
											ackIn, goOut, 
											runtime, this);
			} else {
				logger.info("\noops get features skipped since no cops but needed for private topics");
			}

		}
				
		initChannelBlocker(maxGoPipeId);



		
		buildHTTPClientGraph(runtime, httpClientResponsePipes, httpClientRequestPipes, masterGoOut, masterAckIn);
		
		/////////
		//always create the pub sub and state management stage?
		/////////
		//TODO: only create when subscriptionPipeLookup is not empty and subscriptionPipes has zero length.
		if (IDX_MSG<0) {
			logger.trace("saved some resources by not starting up the unused pub sub service.");
		} else {
			
			if (!isAllPrivateTopics) {
			
				//logger.info("builder created pub sub");
			 	createMessagePubSubStage(runtime, subscriptionPipeLookup2, 
			 			ingressMessagePipes, messagePubSub, 
			 			masterGoOut[IDX_MSG], masterAckIn[IDX_MSG], 
			 			subscriptionPipes);
			}
		}
	}

	
	public void buildHTTPClientGraph(
			MsgRuntime<?,?> runtime,
			Pipe<NetResponseSchema>[] netResponsePipes,
			Pipe<ClientHTTPRequestSchema>[] netRequestPipes, 
			Pipe<TrafficReleaseSchema>[][] masterGoOut,
			Pipe<TrafficAckSchema>[][] masterAckIn) {
		////////
		//create the network client stages
		////////
		if (useNetClient(netRequestPipes)) {
			int tracks = Math.max(1, runtime.getBuilder().parallelTracks());			
			int maxPartialResponses = Math.max(2,ClientHostPortInstance.getSessionCount());
			int maxClientConnections = 4*(maxPartialResponses*tracks);
			int connectionsInBits = (int)Math.ceil(Math.log(maxClientConnections)/Math.log(2));

			int netResponseCount = 8;
			int responseQueue = 10;
			
			//must be adjusted together

			int releaseCount = 1024;
			int responseUnwrapCount = this.client.isTLS()? 2 : 1;
			int clientWrapperCount = this.client.isTLS()? 2 : 1;
			int outputsCount = clientWrapperCount*(this.client.isTLS()? 2 : 1); //Multipler per session for total connections ,count of pipes to channel writer
	
			//due to deadlocks which may happen in TLS handshake we must have as many or more clientWriters
			//as we have SSLEngineUnwrapStages so each can complete its handshake
			int clientWriters = responseUnwrapCount; //count of channel writer stages, can be larger than Unwrap count if needed
			
			PipeConfig<NetPayloadSchema> clientNetRequestConfig = pcm.getConfig(NetPayloadSchema.class);
					
			//BUILD GRAPH
			ccm = new ClientCoordinator(connectionsInBits, maxPartialResponses,
					                    this.client.getCertificates(), gm.recordTypeData);
		
			Pipe<NetPayloadSchema>[] clientRequests = new Pipe[outputsCount];
			int r = outputsCount;
			while (--r>=0) {
				clientRequests[r] = new Pipe<NetPayloadSchema>(clientNetRequestConfig);		
			}
			
			if (isAllNull(masterGoOut[IDX_NET])) {
				//this one has much lower latency and should be used if possible
				new HTTPClientRequestStage(gm, ccm, netRequestPipes, clientRequests);
			} else {	
				
				assert(netRequestPipes.length == masterGoOut[IDX_NET].length);
				assert(masterAckIn[IDX_NET].length == masterGoOut[IDX_NET].length);
				
				
				logger.info("Warning, the slower HTTP Client Request code was called. 2ms latency may be introduced.");
				
				//this may stay for as long as 2ms before returning due to timeout of
				//traffic logic, this is undesirable in some low latency cases.
				new HTTPClientRequestTrafficStage(
						gm, runtime, this, 
						ccm, netRequestPipes, 
						masterGoOut[IDX_NET], masterAckIn[IDX_NET], 
						clientRequests);
			}

			
			NetGraphBuilder.buildHTTPClientGraph(gm, ccm, 
												responseQueue, clientRequests, 
												netResponsePipes,
												netResponseCount,
												releaseCount,
												responseUnwrapCount,
												clientWrapperCount,
												clientWriters);
						
		}
	}

	private boolean isAllNull(Pipe<?>[] pipes) {
		int p = pipes.length;
		while (--p>=0) {
			if (pipes[p]!=null) {
				return false;
			}
		}
		return true;
	}

	protected int populateGoAckPipes(int maxGoPipeId, Pipe<TrafficReleaseSchema>[][] masterGoOut,
			Pipe<TrafficAckSchema>[][] masterAckIn, Pipe<TrafficReleaseSchema>[] goOut, Pipe<TrafficAckSchema>[] ackIn,
			int p) {
		
		if (p>=0) {
			addToLastNonNull(masterGoOut[p], goOut[p] = new Pipe<TrafficReleaseSchema>(this.pcm.getConfig(TrafficReleaseSchema.class)));
			maxGoPipeId = Math.max(maxGoPipeId, goOut[p].id);				
			addToLastNonNull(masterAckIn[p], ackIn[p] = new Pipe<TrafficAckSchema>(this.pcm.getConfig(TrafficAckSchema.class)));
		}
		
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

	
	protected int getFeatures(GraphManager gm, Pipe<TrafficOrderSchema> orderPipe) {
		PronghornStage producer = GraphManager.getRingProducer(gm, orderPipe.id);
		assert(producer instanceof ReactiveProxyStage) : "TrafficOrderSchema must only come from Reactor stages but was "+producer.getClass().getSimpleName();
		
		return ((ReactiveProxyStage)producer).getFeatures(orderPipe);
	}
	
	@Override
	public MQTTConfigImpl useMQTT(CharSequence host, int port, CharSequence clientId) {
		return useMQTT(host, port, clientId, MQTTConfigImpl.DEFAULT_MAX_MQTT_IN_FLIGHT, MQTTConfigImpl.DEFAULT_MAX__MQTT_MESSAGE);
	}
		
	@Override
	public MQTTConfigImpl useMQTT(CharSequence host, int port, CharSequence clientId, int maxInFlight) {
		return useMQTT(host, port, clientId, maxInFlight, MQTTConfigImpl.DEFAULT_MAX__MQTT_MESSAGE);
	}

	@Override
	public MQTTConfigImpl useMQTT(CharSequence host, int port, CharSequence clientId, int maxInFlight, int maxMessageLength) {
		ClientCoordinator.registerDomain(host);		
		if (maxInFlight>(1<<15)) {
			throw new UnsupportedOperationException("Does not suppport more than "+(1<<15)+" in flight");
		}
		if (maxMessageLength>(256*(1<<20))) {
			throw new UnsupportedOperationException("Specification does not support values larger than 256M");
		}
		 
		pcm.ensureSize(MessageSubscription.class, maxInFlight, maxMessageLength);
		
		//all these use a smaller rate to ensure MQTT can stay ahead of the internal message passing
		long rate = defaultSleepRateNS>200_000?defaultSleepRateNS/4:defaultSleepRateNS;

		MQTTConfigImpl mqttBridge = new MQTTConfigImpl(host, port, clientId,
				                    this, rate, 
				                    (short)maxInFlight, maxMessageLength);
		mqtt = mqttBridge;
		mqttBridge.beginDeclarations();
		return mqtt;
	}
	
	@Override
	public void useInsecureSerialStores(int instances, int largestBlock) {
		logger.warn("Non encrypted serial stores are in use. Please call useSerialStores with a passphrase to ensure data is encrypted.");
		
		int j = instances;
		while (--j>=0) {					
			buildSerialStore(j, null, largestBlock);
		}
	}
	
	@Override
	public void useSerialStores(int instances, int largestBlock, String passphrase) {
			
		CharSequenceToUTF8 charSequenceToUTF8 = CharSequenceToUTF8Local.get();
		SecureRandom sr = new SecureRandom(charSequenceToUTF8.convert(passphrase).asBytes());
		charSequenceToUTF8.clear();
		NoiseProducer noiseProducer = new NoiseProducer(sr);
				
		serialStoreReleaseAck = new Pipe[instances];
		serialStoreReplay = new Pipe[instances];
		serialStoreWriteAck = new Pipe[instances];
		serialStoreRequestReplay = new Pipe[instances];
		serialStoreWrite = new Pipe[instances];
		
		int j = instances;
		while (--j>=0) {					
			buildSerialStore(j, noiseProducer, largestBlock);
		}
	}
	
	@Override
	public void useSerialStores(int instances, int largestBlock, byte[] passphrase) {
		
		SecureRandom sr = new SecureRandom(passphrase);	
		NoiseProducer noiseProducer = new NoiseProducer(sr);
				
		serialStoreReleaseAck = new Pipe[instances];
		serialStoreReplay = new Pipe[instances];
		serialStoreWriteAck = new Pipe[instances];
		serialStoreRequestReplay = new Pipe[instances];
		serialStoreWrite = new Pipe[instances];
		
		for(int j=0; j<instances; j++) {					
			buildSerialStore(j, noiseProducer, largestBlock);
		}
	}

	/////////////////////////
	//holding these for build lookup
	/////////////////////////
	public Pipe<PersistedBlobLoadReleaseSchema>[] serialStoreReleaseAck;
	public Pipe<PersistedBlobLoadConsumerSchema>[] serialStoreReplay;
	public Pipe<PersistedBlobLoadProducerSchema>[] serialStoreWriteAck;
	public Pipe<PersistedBlobStoreConsumerSchema>[] serialStoreRequestReplay;
	public Pipe<PersistedBlobStoreProducerSchema>[] serialStoreWrite;
	

	public Pipe<NetResponseSchema> buildNetResponsePipe() {
				
		Pipe<NetResponseSchema> netResponsePipe = new Pipe<NetResponseSchema>(pcm.getConfig(NetResponseSchema.class)) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<NetResponseSchema> createNewBlobReader() {
				return new HTTPResponseReader(this, httpSpec);
			}
		};
		return netResponsePipe;
	}
	
	private void buildSerialStore(int id, 
			                      NoiseProducer noiseProducer,
			                      int largestBlock) {
		
		File targetDirectory = null;
		try {
			targetDirectory = new File(Files.createTempDirectory("serialStore"+id).toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}		
		
		final short maxInFlightCount = 4;
		
		buildSerialStore(id, noiseProducer, largestBlock, targetDirectory, maxInFlightCount);
		
	}

	private void buildSerialStore(int id, NoiseProducer noiseProducer, int largestBlock, File targetDirectory,
			final short maxInFlightCount) {
		Pipe<PersistedBlobLoadReleaseSchema> fromStoreRelease = 
				PersistedBlobLoadReleaseSchema.instance.newPipe(maxInFlightCount, 0);
		Pipe<PersistedBlobLoadConsumerSchema> fromStoreConsumer =
				PersistedBlobLoadConsumerSchema.instance.newPipe(maxInFlightCount, largestBlock);
		Pipe<PersistedBlobLoadProducerSchema> fromStoreProducer =
				PersistedBlobLoadProducerSchema.instance.newPipe(maxInFlightCount, 0);
		Pipe<PersistedBlobStoreConsumerSchema> toStoreConsumer =
				PersistedBlobStoreConsumerSchema.instance.newPipe(maxInFlightCount, 0);
		Pipe<PersistedBlobStoreProducerSchema> toStoreProducer =
				PersistedBlobStoreProducerSchema.instance.newPipe(maxInFlightCount, largestBlock);
	
		serialStoreReleaseAck[id] = fromStoreRelease;
		serialStoreReplay[id] = fromStoreConsumer;
		serialStoreWriteAck[id] = fromStoreProducer;
		serialStoreRequestReplay[id] = toStoreConsumer;
		serialStoreWrite[id] = toStoreProducer;
		
		//NOTE: the above pipes will dangle but will remain in the created order so that
		//      they can be attached to the right command channels upon definition
		
	
		long rate=-1;//do not set so we will use the system default.
		String backgroundColor="cornsilk2";
				
		PronghornStageProcessor proc = new PronghornStageProcessor() {
			@Override
			public void process(GraphManager gm, PronghornStage stage) {
				GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, rate, stage);
				GraphManager.addNota(gm, GraphManager.DOT_BACKGROUND, backgroundColor, stage);
			}			
		};
		
		FileGraphBuilder.buildSequentialReplayer(gm, 
				fromStoreRelease, fromStoreConsumer, fromStoreProducer, 
				toStoreConsumer, toStoreProducer, 
				maxInFlightCount, largestBlock, targetDirectory, 
				noiseProducer, proc);
	}
	
	///////////////////////////////////
	//fields supporting private topics
	///////////////////////////////////
	private final TrieParser privateTopicSource = new TrieParser(64, 2, false, false, false);
	private final TrieParser privateTopicTarget = new TrieParser(64, 2, false, false, false);		
	private final List<List<PrivateTopic>> privateSourceTopics = new ArrayList<List<PrivateTopic>>();
	private final List<List<PrivateTopic>> privateTargetTopics = new ArrayList<List<PrivateTopic>>();
    private final List<String> dynamicTopicPublishers = new ArrayList<String>();
	private final List<String> dynamicTopicSubscribers = new ArrayList<String>();
	//TODO: MUST HAVE ARRAY OF TOPICS TO LOOK UP BY PIPE?
	///////////////////////////////////		
	
	public List<PrivateTopic> getPrivateTopicsFromSource(String source) {
				
		byte[] bytes = CharSequenceToUTF8Local.get().convert(source).append(" ").asBytes();		
		int sourceId = (int)TrieParserReader.query(TrieParserReaderLocal.get(), privateTopicSource, bytes, 0, bytes.length, Integer.MAX_VALUE);
		List<PrivateTopic> result = (sourceId<0) ? Collections.EMPTY_LIST : privateSourceTopics.get(sourceId);		
		return result;
	}
	
	public List<PrivateTopic> getPrivateTopicsFromTarget(String target) {
		byte[] bytes = CharSequenceToUTF8Local.get().convert(target).append(" ").asBytes();	
		int targetId = (int)TrieParserReader.query(TrieParserReaderLocal.get(), privateTopicTarget, bytes, 0, bytes.length, Integer.MAX_VALUE);
		List<PrivateTopic> result = (targetId<0) ? Collections.EMPTY_LIST: privateTargetTopics.get(targetId);
		return result;
	}	
	

	public static TrieParser unScopedTopics = null; //package protected static, all unscoped topics
	
	@Override
	public void defineUnScopedTopic(String topic) {
		
		if (null == unScopedTopics) {
			unScopedTopics = new TrieParser();			
		}
		
		unScopedTopics.setUTF8Value(topic, 1);

	}
	
	@Override
	public void definePrivateTopic(String topic, String source, String ... targets) {
		
		definePrivateTopic(10, 10000, topic, source, targets);
		
	}
	
	@Override
	public void definePrivateTopic(int queueLength, int maxMessageSize, String topic, String source, String ... targets) {
		
		PipeConfig<MessagePrivate> config = new PipeConfig<MessagePrivate>(MessagePrivate.instance, queueLength, maxMessageSize);
				
		defPrivateTopics(config, topic, source, targets);
				
		
		
	}

	public void defPrivateTopics(PipeConfig<MessagePrivate> config, String topic, String source, String... targets) {
		if (targets.length<=1) {
			throw new UnsupportedOperationException("only call this with multiple targets");
		}
		
		boolean hideTopics = false;
		PrivateTopic sourcePT = new PrivateTopic(topic, 
												 config, 
				                                 hideTopics,
				                                 this);
		
		List<PrivateTopic> localSourceTopics = null;
		byte[] bytes = CharSequenceToUTF8Local.get().convert(source).append(" ").asBytes();
		int sourceId = (int)TrieParserReader.query(TrieParserReaderLocal.get(), privateTopicSource, bytes, 0, bytes.length, Integer.MAX_VALUE);
		if (sourceId<0) {
			localSourceTopics = new ArrayList<PrivateTopic>();			
			privateTopicSource.setValue(bytes, privateSourceTopics.size());
			privateSourceTopics.add(localSourceTopics);
		} else {
			localSourceTopics = privateSourceTopics.get(sourceId);
		}
		localSourceTopics.add(sourcePT);
		
		int pt = parallelismTracks<1?1:parallelismTracks;
		while (--pt>=0) {
		
			Pipe<MessagePrivate> src = sourcePT.getPipe(pt);
			PipeConfig<MessagePrivate> trgtConfig = src.config().grow2x();
			int t = targets.length;
			Pipe[] trgts = new Pipe[t];
			PrivateTopic[] trgtTopics = new PrivateTopic[t];
			while (--t>=0) {
				
				trgtTopics[t] = new PrivateTopic(sourcePT.topic, trgtConfig, this);
				
				trgts[t] = trgtTopics[t].getPipe(pt);
	
				List<PrivateTopic> localTargetTopics = null;
				byte[] tbytes = CharSequenceToUTF8Local.get().convert(targets[t]).append(" ").asBytes();
				int targetId = (int)TrieParserReader.query(TrieParserReaderLocal.get(), privateTopicTarget, tbytes, 0, tbytes.length, Integer.MAX_VALUE);
				if (targetId<0) {
					localTargetTopics = new ArrayList<PrivateTopic>();
					privateTopicTarget.setValue(tbytes, privateTargetTopics.size());
					privateTargetTopics.add(localTargetTopics);
				} else {
					localTargetTopics = privateTargetTopics.get(targetId); 
				}
				
				localTargetTopics.add(trgtTopics[t]);
			}
			ReplicatorStage.newInstance(gm, src, trgts);
		
		}
	}
	
	//these are to stop some topics from becomming private.
	private ArrayList<String[]> publicTopics = new ArrayList<String[]>();
	
	
	@Override
	public void definePublicTopics(String ... topics) {
		publicTopics.add(topics);
	}
	
	@Override
	public void definePrivateTopic(String topic, String source, String target) {
		definePrivateTopic(10, 10000, topic, source, target);
	}
	
	@Override
	public void definePrivateTopic(int queueLength, int maxMessageSize, 
			                        String topic, String source, String target) {
		
		boolean hideTopics = false;
		PrivateTopic obj = new PrivateTopic(topic, queueLength, 
				                            maxMessageSize, hideTopics,
				                            this);
		
		privateTopicsFromProducer(source).add(obj);
		
		privateTopicsFromConsumer(target).add(obj);
		
	}

	void definePrivateTopic(PipeConfig<MessagePrivate> config, String topic, String source, String target) {

		boolean hideTopics = false;
		PrivateTopic obj = new PrivateTopic(topic, config, hideTopics,
				                            this);
		
		privateTopicsFromProducer(source).add(obj);
		
		privateTopicsFromConsumer(target).add(obj);
		
	}

	
	private List<PrivateTopic> privateTopicsFromConsumer(String target) {
		List<PrivateTopic> localTargetTopics = null;
		byte[] tbytes = CharSequenceToUTF8Local.get().convert(target).append(" ").asBytes();
		int targetId = (int)TrieParserReader.query(TrieParserReaderLocal.get(), privateTopicTarget, tbytes, 0, tbytes.length, Integer.MAX_VALUE);
		if (targetId<0) {
			localTargetTopics = new ArrayList<PrivateTopic>();
			//logger.info("record target '{}'",target);
			privateTopicTarget.setValue(tbytes, targetId = privateTargetTopics.size());
			privateTargetTopics.add(localTargetTopics);
		} else {
			localTargetTopics = privateTargetTopics.get(targetId); 
		}
		return localTargetTopics;
	}

	private List<PrivateTopic> privateTopicsFromProducer(String source) {
		List<PrivateTopic> localSourceTopics = null;
		byte[] bytes = CharSequenceToUTF8Local.get().convert(source).append(" ").asBytes();
		int sourceId = (int)TrieParserReader.query(TrieParserReaderLocal.get(), privateTopicSource, bytes, 0, bytes.length, Integer.MAX_VALUE);
		if (sourceId<0) {
			localSourceTopics = new ArrayList<PrivateTopic>();
			privateTopicSource.setValue(bytes, sourceId = privateSourceTopics.size());
			privateSourceTopics.add(localSourceTopics);		
		} else {
			localSourceTopics = privateSourceTopics.get(sourceId);
		}
		return localSourceTopics;
	}

	@Override
	public void enableDynamicTopicPublish(String id) {
		dynamicTopicPublishers.add(id);
	}

	@Override
	public void enableDynamicTopicSubscription(String id) {
		dynamicTopicSubscribers.add(id);
	}
	
	//////////////////////////////////
	

	@Override
	public String[] args() {
		return args.args();
	}

	@Override
	public boolean hasArgument(String longName, String shortName) {
		return args.hasArgument(longName, shortName);
	}

	@Override
	public String getArgumentValue(String longName, String shortName, String defaultValue) {
		return args.getArgumentValue(longName, shortName, defaultValue);
	}

	@Override
	public Boolean getArgumentValue(String longName, String shortName, Boolean defaultValue) {
		return args.getArgumentValue(longName, shortName, defaultValue);
	}

	@Override
	public Character getArgumentValue(String longName, String shortName, Character defaultValue) {
		return args.getArgumentValue(longName, shortName, defaultValue);
	}

	@Override
	public Byte getArgumentValue(String longName, String shortName, Byte defaultValue) {
		return args.getArgumentValue(longName, shortName, defaultValue);
	}

	@Override
	public Short getArgumentValue(String longName, String shortName, Short defaultValue) {
		return args.getArgumentValue(longName, shortName, defaultValue);
	}

	@Override
	public Long getArgumentValue(String longName, String shortName, Long defaultValue) {
		return args.getArgumentValue(longName, shortName, defaultValue);
	}

	@Override
	public Integer getArgumentValue(String longName, String shortName, Integer defaultValue) {
		return args.getArgumentValue(longName, shortName, defaultValue);
	}

	@Override
	public <T extends Enum<T>> T getArgumentValue(String longName, String shortName, Class<T> c, T defaultValue) {
		return args.getArgumentValue(longName, shortName, c, defaultValue);
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

	public void finalizeDeclareConnections() {
		// two of these are recalculating the same local host address when host is null
		if (server != null) {
			server.finalizeDeclareConnections();
		}
		if (client != null) {
			client.finalizeDeclareConnections();
		}
		if (telemetry != null) {
			telemetry.finalizeDeclareConnections();
		}
	}

	public static boolean notUnscoped(TrieParserReader reader, DataOutputBlobWriter<MessagePubSub> output){
			return (-1 == output.startsWith(reader, BuilderImpl.unScopedTopics ));
	}

	public static boolean hasNoUnscopedTopics() {
		return null==BuilderImpl.unScopedTopics;
	}

	@Override
	public void setGlobalSLALatencyNS(long ns) {
		GraphManager.addDefaultNota(gm, GraphManager.SLA_LATENCY, ns);
	}

	@Override
	public long lookupFieldByName(int id, String name) {
		if ((id & StructRegistry.IS_STRUCT_BIT) == 0) {
			//this is a route so we must covert to struct
			id = routerConfig.getStructIdForRouteId(id);
		}
		return gm.recordTypeData.fieldLookup(name, id);
	}

	@Override
	public long lookupFieldByIdentity(int id, Object obj) {
		if ((id & StructRegistry.IS_STRUCT_BIT) == 0) {
			//this is a route so we must covert to struct
			id = routerConfig.getStructIdForRouteId(id);
		}
		return gm.recordTypeData.fieldLookupByIdentity(obj, id);
	}

	@Override
	public StructBuilder defineStruct() {
		return StructBuilder.newStruct(gm.recordTypeData);
	}

	@Override
	public StructBuilder extendStruct(StructBuilder template) {
		return StructBuilder.newStruct(gm.recordTypeData, template);
	}

	 
	private ArrayList<PendingStageBuildable> pendingStagesToBuild = new ArrayList<PendingStageBuildable>();
	
	/**
	 * Store the reactive listeners until they are all created.
	 * The actual stages are created at once after they are all registered.
	 * @param buildable
	 */
	public void pendingInit(PendingStageBuildable buildable) {
		pendingStagesToBuild.add(buildable);
	}

	public void initAllPendingReactors() {
		if (null!=pendingStagesToBuild) {

			if (enableAutoPrivateTopicDiscovery) {			
				defineAutoDiscoveredPrivateTopcis();//must be done before the initRealStage call.
			}
			
			for(PendingStageBuildable stage: pendingStagesToBuild) {
				stage.initRealStage();
			}
			pendingStagesToBuild = null;
		}
	}

	/////////////////////////////////////////////////////////////////////
	/////////////Auto discovery of private topics
	///////////////////////////////////////////////////////////////////////
	//find topic matches where we have 1 channel producer and 1 behavior consumer
	//////////////////////////////////////////////////////////////////////////
	
	private TrieParser possibleTopics = new TrieParser();
	private int possiblePrivateTopicsCount = 0;
	private MsgCommandChannel[] possiblePrivateCmds = new MsgCommandChannel[16];
	
	//ReactiveListenerStage
	private ArrayList[] possiblePrivateBehaviors = new ArrayList[16];
	
	
	private int[] possiblePrivateTopicsProducerCount = new int[16];
	private CharSequence[] possiblePrivateTopicsTopic = new CharSequence[16];
	
	public void possiblePrivateTopicProducer(MsgCommandChannel<?> cmdChannel, String topic) {

		
		int id = (int)TrieParserReaderLocal.get().query(possibleTopics, topic);
		if (-1 == id) {
			growPossiblePrivateTopics();			
			possiblePrivateCmds[possiblePrivateTopicsCount] = cmdChannel;
			possiblePrivateTopicsTopic[possiblePrivateTopicsCount]=topic;
			possiblePrivateTopicsProducerCount[possiblePrivateTopicsCount]++;
			possibleTopics.setUTF8Value(topic, possiblePrivateTopicsCount++);
		} else {		
            //only record once for same channel and topic pair
			if (cmdChannel != possiblePrivateCmds[id]) {
				possiblePrivateCmds[id] = cmdChannel;
				possiblePrivateTopicsProducerCount[id]++;
			}
		}
	}
	
	public void possiblePrivateTopicConsumer(ReactiveListenerStage listener, CharSequence topic) {
				
		int id = (int)TrieParserReaderLocal.get().query(possibleTopics, topic);
		if (-1 == id) {
			growPossiblePrivateTopics();			
			if (null==possiblePrivateBehaviors[possiblePrivateTopicsCount]) {
				possiblePrivateBehaviors[possiblePrivateTopicsCount] = new ArrayList();
			}
			possiblePrivateBehaviors[possiblePrivateTopicsCount].add(listener);
			
			possiblePrivateTopicsTopic[possiblePrivateTopicsCount]=topic;
			
			//new Exception("added topic "+topic+" now consumers total "+possiblePrivateBehaviors[possiblePrivateTopicsCount].size()+" position "+possiblePrivateTopicsCount).printStackTrace();;
			
			
			possibleTopics.setUTF8Value(topic, possiblePrivateTopicsCount++);			
		} else {
			
			if (null==possiblePrivateBehaviors[id]) {
				possiblePrivateBehaviors[id] = new ArrayList();
			}
			possiblePrivateBehaviors[id].add(listener);
			
		}
	}

	
	private void growPossiblePrivateTopics() {
		if (possiblePrivateTopicsCount == possiblePrivateBehaviors.length) {
			//grow
			MsgCommandChannel[] newCmds = new MsgCommandChannel[possiblePrivateTopicsCount*2];
			System.arraycopy(possiblePrivateCmds, 0, newCmds, 0, possiblePrivateTopicsCount);
			possiblePrivateCmds = newCmds;
			
			ArrayList[] newBeh = new ArrayList[possiblePrivateTopicsCount*2];
			System.arraycopy(possiblePrivateBehaviors, 0, newBeh, 0, possiblePrivateTopicsCount);
			possiblePrivateBehaviors = newBeh;
			
			int[] newProducer = new int[possiblePrivateTopicsCount*2];
			System.arraycopy(possiblePrivateTopicsProducerCount, 0, newProducer, 0, possiblePrivateTopicsCount);
			possiblePrivateTopicsProducerCount = newProducer;

			String[] newTopics = new String[possiblePrivateTopicsCount*2];
			System.arraycopy(possiblePrivateTopicsTopic, 0, newTopics, 0, possiblePrivateTopicsCount);
			possiblePrivateTopicsTopic = newTopics;
						
		}
	}
	
	public void defineAutoDiscoveredPrivateTopcis() {

		//logger.info("possible private topics {} ",possiblePrivateTopicsCount);
		int actualPrivateTopicsFound = 0;
		int i = possiblePrivateTopicsCount;
		while (--i>=0) {			
			
			String topic = possiblePrivateTopicsTopic[i].toString();					
			//logger.info("possible private topic {} {}->{}",topic, possiblePrivateTopicsProducerCount[i], null==possiblePrivateBehaviors[i] ? -1 :possiblePrivateBehaviors[i].size());
			boolean madePrivate = false;
			String reasonSkipped = "";
			final int consumers = (null==possiblePrivateBehaviors[i]) ? 0 : possiblePrivateBehaviors[i].size();
			if (possiblePrivateTopicsProducerCount[i]==1) {
				if ((null!=possiblePrivateBehaviors[i]) && ((possiblePrivateBehaviors[i].size())>=1)) {
					//may be valid check that is is not on the list.
					if (!skipTopic(topic)) {
						
						MsgCommandChannel msgCommandChannel = possiblePrivateCmds[i];
						String producerName = msgCommandChannel.behaviorName();
						if (null!=producerName) {
							int j = possiblePrivateBehaviors[i].size();
							PipeConfig<MessagePrivate> config = msgCommandChannel.pcm.getConfig(MessagePrivate.class);

						    if (j==1) {
						    	String consumerName = ((ReactiveListenerStage)(possiblePrivateBehaviors[i].get(0))).behaviorName();
								if (null!=consumerName) {
										definePrivateTopic(config, topic, producerName, consumerName);
										madePrivate = true;
	
								}
						    	
						    } else if (j>1) {
						    	String[] names = new String[j];
						    							    	
								while (--j>=0) {
									String consumerName = ((ReactiveListenerStage)(possiblePrivateBehaviors[i].get(j))).behaviorName();
									if (null==consumerName) {
										reasonSkipped ="Reason: one of the consuming behaviors have no name.";
										break;
									}
									names[j] = consumerName;
								}
								if (j<0) {
									defPrivateTopics(config, topic, producerName, names);
									madePrivate = true;
								}
							
						    }
							
							if (madePrivate) {
								actualPrivateTopicsFound++;
							}
							
							
							
						} else {
							reasonSkipped = "Reason: Producer had no name";
						}
					} else {
						reasonSkipped = "Reason: Explicitly set as not to be private in behavior";
					}
				} else {
					reasonSkipped = "Reason: Must have 1 or more consumers";
				}
			} else {
				reasonSkipped = "Reason: Must have single producer";
			}
			logger.info("MadePrivate: {} Topic: {} Producers: {} Consumers: {}   {} ", madePrivate, topic, possiblePrivateTopicsProducerCount[i], consumers, reasonSkipped);
			
			
		}
		
		//hack test as we figure out what TODO: about this
		if (actualPrivateTopicsFound == possiblePrivateTopicsCount && (!messageRoutingRequired)) {
			isAllPrivateTopics = true;
		}
		
	}
	//iterate over single matches 

	private boolean skipTopic(CharSequence topic) {
		boolean skipTopic = false;
		int w = publicTopics.size();
		while (--w>=0) {
			
			if (isFoundInArray(topic, w)) {
				skipTopic = true;
				break;
			}
		}
		return skipTopic;
	}

	private boolean isFoundInArray(CharSequence topic, int w) {

		String[] s = publicTopics.get(w);						
		int x = s.length;
		while (--x>=0) {			
			if (isMatch(topic, s, x)) {
				return true;
			}			
		}
		return false;
	}

	private boolean isMatch(CharSequence topic, String[] s, int x) {

		if (topic.length() == s[x].length()) {
			int z = topic.length();
			while (--z >= 0) {									
				if (topic.charAt(z) != s[x].charAt(z)) {
					return false;									
				}
			}
		} else {
			return false;
		}
		return true;
	}

	public void populateListenerIdentityHash(Behavior listener) {
		//store this value for lookup later
		//logger.info("adding hash listener {} to pipe  ",System.identityHashCode(listener));
		if (!IntHashTable.setItem(subscriptionPipeLookup, System.identityHashCode(listener), subscriptionPipeIdx++)) {
			throw new RuntimeException("Could not find unique identityHashCode for "+listener.getClass().getCanonicalName());
		}
		
		assert(!IntHashTable.isEmpty(subscriptionPipeLookup));
	}
	
	public void populateListenerIdentityHash(int hash) {
		//store this value for lookup later
		//logger.info("adding hash listener {} to pipe  ",System.identityHashCode(listener));
		if (!IntHashTable.setItem(subscriptionPipeLookup, hash, subscriptionPipeIdx++)) {
			throw new RuntimeException("Could not find unique identityHashCode for "+hash);
		}
		
		assert(!IntHashTable.isEmpty(subscriptionPipeLookup));
	}

	public IntHashTable getSubPipeLookup() {
		return subscriptionPipeLookup;
	}

	public void setCleanShutdownRunnable(Runnable cleanRunnable) {
		cleanShutdownRunnable = cleanRunnable;
	}

	public void setDirtyShutdownRunnable(Runnable dirtyRunnable) {
		dirtyShutdownRunnable = dirtyRunnable;
	}

	public void requestShutdown() {
		requestShutdown(3);
	}
	
	public void requestShutdown(int secondsTimeout) {
		
		if (ReactiveListenerStage.isShutdownRequested(this)) {
			return;//do not do again.
		}
		
    	final Runnable lastCallClean = new Runnable() {    		
    		@Override
    		public void run() {
    			
    			//all the software has now stopped so shutdown the hardware now.
    			shutdown();
    			
    			if (null!=cleanShutdownRunnable) {
    				cleanShutdownRunnable.run();
    			}
    				    			
    		}    		
    	};
    	
    	final Runnable lastCallDirty = new Runnable() {    		
    		@Override
    		public void run() {
    			
    			//all the software has now stopped so shutdown the hardware now.
    			shutdown();
    			
    			if (null!=dirtyShutdownRunnable) {
    				dirtyShutdownRunnable.run();
    			}
    			
    		}    		
    	};
    	
    	//notify all the reactors to begin shutdown.
    	ReactiveListenerStage.requestSystemShutdown(this, new Runnable() {

			@Override
			public void run() {
				
				logger.info("Scheduler {} shutdown ", scheduler.getClass().getSimpleName());
				scheduler.shutdown();
			
				scheduler.awaitTermination(secondsTimeout, TimeUnit.SECONDS, lastCallClean, lastCallDirty);
				
			}
    		
    	});
    	
    	
	}

	public void setScheduler(StageScheduler s) {
		this.scheduler = s;
	}

	public StageScheduler getScheduler() {
		return this.scheduler;
	}

	private boolean messageRoutingRequired = false;
	
	public void messageRoutingRequired() {
		messageRoutingRequired = true;
	}

	public int lookupTargetPipe(ClientHostPortInstance session, Behavior listener) {
		int lookupHTTPClientPipe;
		if (hasHTTPClientPipe(session.sessionId)) {
			lookupHTTPClientPipe = lookupHTTPClientPipe(session.sessionId);
		} else {
			lookupHTTPClientPipe = lookupHTTPClientPipe(behaviorId(listener));
		}
		return lookupHTTPClientPipe;
	}

	public void populatePrivateTopicPipeNames(byte[][] names) {
		
		int st = privateSourceTopics.size();
		while (--st>=0) {
			List<PrivateTopic> local = privateSourceTopics.get(st);
			if (null!=local) {
				int l = local.size();
				while (--l>=0) {
					local.get(l).populatePrivateTopicPipeNames(names);
				}
			}
		}
		
		int tt = privateTargetTopics.size();
		while (--tt>=0) {
			List<PrivateTopic> local = privateTargetTopics.get(tt);
			if (null!=local) {
				int l = local.size();
				while (--l>=0) {
					local.get(l).populatePrivateTopicPipeNames(names);
				}
			}
			
		}
		
	}

	public String generateBehaviorName(Object listener) {
		
		return generateBehaviorNameFromClass(listener.getClass());
		
	}

	public String generateBehaviorNameFromClass(Class<? extends Object> clazz) {
		String base = clazz.getSimpleName();
		synchronized(behaviorNames) {		
			if (!behaviorNames.containsKey(base)) {
				behaviorNames.put(base, new AtomicInteger());			
			}
		}
		return base+behaviorNames.get(base).incrementAndGet();
	}

	public static String buildTrackTopic(final CharSequence baseTopic, final byte[] track) {

		if (null != track) {
			if (hasNoUnscopedTopics()) {//normal case where topics are scoped
				return baseTopic+new String(track);
			} else {
				//if scoped then add suffix
				if ((-1 == TrieParserReaderLocal.get().query(unScopedTopics, baseTopic))) {
					return baseTopic+new String(track);
				}
			}
		}
		return baseTopic.toString();
	}

	//common method for building topic suffix
	public static byte[] trackNameBuilder(int parallelInstanceId) {
		return parallelInstanceId<0 ? null : ( "/"+Integer.toString(parallelInstanceId)).getBytes();
	}


	
	/////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////
	/////////////////////////////////////////////////////////////
	
	
	
	
	
}
