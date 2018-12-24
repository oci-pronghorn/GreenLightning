package com.ociweb.gl.impl;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.ArgumentParser;
import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.DeclareBehavior;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.HTTPClientConfig;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPResponseReader;
import com.ociweb.gl.api.ListenerTransducer;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.RouteDefinition;
import com.ociweb.gl.api.TelemetryConfig;
import com.ociweb.gl.api.TimeTrigger;
import com.ociweb.gl.api.transducer.HTTPResponseListenerTransducer;
import com.ociweb.gl.api.transducer.PubSubListenerTransducer;
import com.ociweb.gl.api.transducer.RestListenerTransducer;
import com.ociweb.gl.api.transducer.StateChangeListenerTransducer;
import com.ociweb.gl.impl.http.client.HTTPClientConfigImpl;
import com.ociweb.gl.impl.http.server.HTTPResponseListenerBase;
import com.ociweb.gl.impl.mqtt.MQTTConfigImpl;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.schema.MessagePrivate;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficAckSchema;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.schema.TrafficReleaseSchema;
import com.ociweb.gl.impl.stage.BehaviorNameable;
import com.ociweb.gl.impl.stage.MessagePubSubTrafficStage;
import com.ociweb.gl.impl.stage.PendingStageBuildable;
import com.ociweb.gl.impl.stage.ReactiveListenerStage;
import com.ociweb.gl.impl.stage.ReactiveManagerPipeConsumer;
import com.ociweb.gl.impl.stage.ReactiveOperators;
import com.ociweb.gl.impl.stage.ReactiveProxyStage;
import com.ociweb.gl.impl.stage.TrafficCopStage;
import com.ociweb.gl.impl.telemetry.TelemetryConfigImpl;
import com.ociweb.json.decode.JSONExtractor;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.HTTPServerConfig;
import com.ociweb.pronghorn.network.HTTPServerConfigImpl;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.SSLUtil;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.TLSCerts;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeader;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.http.HTTPRouterStageConfig;
import com.ociweb.pronghorn.network.http.HTTPClientRequestStage;
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
import com.ociweb.pronghorn.stage.PronghornStageProcessor;
import com.ociweb.pronghorn.stage.encrypt.NoiseProducer;
import com.ociweb.pronghorn.stage.file.FileGraphBuilder;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobLoadConsumerSchema;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobLoadProducerSchema;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobLoadReleaseSchema;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobStoreConsumerSchema;
import com.ociweb.pronghorn.stage.file.schema.PersistedBlobStoreProducerSchema;
import com.ociweb.pronghorn.stage.memory.MemorySequentialReplayerStage;
import com.ociweb.pronghorn.stage.route.ReplicatorStage;
import com.ociweb.pronghorn.stage.scheduling.CoresUtil;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.struct.StructBuilder;
import com.ociweb.pronghorn.struct.StructRegistry;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.Blocker;
import com.ociweb.pronghorn.util.CharSequenceToUTF8;
import com.ociweb.pronghorn.util.CharSequenceToUTF8Local;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;
import com.ociweb.pronghorn.util.TrieParserReaderLocal;
import com.ociweb.pronghorn.util.math.PMath;

public abstract class BuilderImpl<R extends MsgRuntime<?,?,R>> implements Builder<R> {

	private static final int MIN_CYCLE_RATE = 1; //cycle rate can not be zero

	protected static final int MINIMUM_TLS_BLOB_SIZE = SSLUtil.MinTLSBlock;

	protected long timeTriggerRate;
	protected long timeTriggerStart;

	private Runnable cleanShutdownRunnable;
	private Runnable dirtyShutdownRunnable;
	   
    private int startupLimitMS = 40;

    
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

	public final PipeConfigManager pcm = new PipeConfigManager(4,2,64);
	
	public Enum<?> beginningState;
    
	private int behaviorTracks = 1;//default is one
    private DeclareBehavior<R> behaviorTracksDefinition;
    
    
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
	
	public int sessionCountBase = 0;
    
	// Must be just large enough so modern hardware will stay at 1% when we have no work.
    private long defaultSleepRateNS = 100_000;
    
	private final int shutdownTimeoutInSeconds = 1;

	protected ReentrantLock devicePinConfigurationLock = new ReentrantLock();

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
	
	private HTTPRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>
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
	
    public int getStartupLimitMS() {
    	return startupLimitMS;
    }
    
    public void setStartupLimitMS(int startupLimitMS) {
    	this.startupLimitMS = startupLimitMS;
    }
	
    public final ReactiveOperators operators;

    //NOTE: needs re-work to be cleaned up
    private final HashMap<String,AtomicInteger> behaviorNames = new HashMap<String,AtomicInteger>();

	/**
	 * a method to validate fullName and add it to behaviorNames
	 * @param behaviorName String arg used in behaviorNames
	 * @param trackId int arg used with fullName if arg GTE 0
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
	 * @param gcc MsgCommandChannel
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
	
		if (this.behaviorTracksDefinition == null) {
			this.behaviorTracksDefinition = new DeclareBehavior<R>() {
				@Override
				public void declareBehavior(R runtime) {
				}				
			};
		}
		
		return server = new HTTPServerConfigImpl(bindPort, 
												pcm,
												new PipeConfigManager(),
												gm.recordTypeData);
	}
	
	@Override
	public HTTPServerConfig useHTTP1xServer(int bindPort, DeclareBehavior<R> behaviorDefinition) {		
		return useHTTP1xServer(bindPort, -1, behaviorDefinition);
	}
	
	@Override
	public HTTPServerConfig useHTTP1xServer(int bindPort, int tracks, DeclareBehavior<R> behaviorDefinition) {
		if (server != null) {
			throw new UnsupportedOperationException("Server already enabled, microservice runtime only supports one server.");
		}
		if (tracks<=0) {
			//auto select tracks based on cores
			int availableProcessors = CoresUtil.availableProcessors();
			//if for large processor count we want to reduce the socket readers by increasing the sub pipes count
			int subTracks = (availableProcessors >= 16)? 3: 2; //this value can only be 2 or 3 at this time
			tracks = Math.max(1, subTracks*PMath.nextPrime(((int)(availableProcessors*.75))/subTracks) ); //one pipeline track per core	
			tracks = Math.min(tracks, availableProcessors);
			
			String maxTrack = System.getProperty("greenlightning.tracks.max");
			if (null!=maxTrack) {
				try {
				tracks = Math.min(tracks, Integer.parseInt(maxTrack));
				} catch (Exception e) {
					logger.warn("unable to enforce greenlightning.tracks.max property '{}', the value was not parsable",maxTrack);
				}				
			}		
			
		}
		
		this.behaviorTracks = tracks;
		
		if (this.behaviorTracksDefinition!=null) {
			throw new UnsupportedOperationException("tracks may not be set more than once");
		}
		
		this.behaviorTracksDefinition = behaviorDefinition;
		
		return server = new HTTPServerConfigImpl(bindPort, 
												pcm,
												new PipeConfigManager(),
												gm.recordTypeData);
		
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

    
    public final HTTPRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>
    	routerConfig() {
    	if (null==routerConfig) {
    		assert(null!=server) : "No server defined!";
    		if (null==server) {
    			throw new UnsupportedOperationException("Server must be defined BEFORE any routes.");
    		}
    		routerConfig = new HTTPRouterStageConfig<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderDefaults>(
    				httpSpec,
    				server.connectionStruct()); 
    	
    	}    	
    	return routerConfig;
    }

	/**
	 * A method to append pipe mapping and group ids when invoked by builder
	 * @param pipe Pipe arg used for routerConfig().appendPipeIdMappingForIncludedGroupIds
	 * @param track int arg used for routerConfig().appendPipeIdMappingForIncludedGroupIds
	 * @param routeIds int arg used for routerConfig().appendPipeIdMappingForIncludedGroupIds
	 * @return routerConfig().appendPipeIdMappingForIncludedGroupIds(pipe, parallelId, collectedHTTPRequestPipes, groupIds)
	 */
	public final boolean appendPipeMappingIncludingGroupIds(Pipe<HTTPRequestSchema> pipe,
											            int track,
											            int ... routeIds) {
		lazyCreatePipeLookupMatrix();
		return routerConfig().appendPipeIdMappingForIncludedGroupIds(pipe, track, collectedHTTPRequestPipes, routeIds);
	}

	/**
	 * A method to append pipe mapping but not including group ids when invoked by builder
	 * @param pipe Pipe arg used for routerConfig().appendPipeIdMappingForExcludedGroupIds
	 * @param parallelId int arg used for routerConfig().appendPipeIdMappingForExcludedGroupIds
	 * @param routeIds int arg used for routerConfig().appendPipeIdMappingForExcludedGroupIds
	 * @return routerConfig().appendPipeIdMappingForExcludedGroupIds(pipe, parallelId, collectedHTTPRequestPipes, groupIds)
	 */
	public final boolean appendPipeMappingExcludingGroupIds(Pipe<HTTPRequestSchema> pipe,
					                            int parallelId,
					                            int ... routeIds) {
		lazyCreatePipeLookupMatrix();
		return routerConfig().appendPipeIdMappingForExcludedGroupIds(pipe, parallelId, collectedHTTPRequestPipes, routeIds);
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
	 * @param track int arg used as index in collectedHTTPRequestPipes array
	 * @param route int arg used as index in collectedHTTPRequestPipes array
	 * @return null!= collectedHTTPRequestPipes ? collectedHTTPRequestPipes[r][p] : new ArrayList()
	 */
	public final ArrayList<Pipe<HTTPRequestSchema>> buildFromRequestArray(int track, int route) {
		assert(null== collectedHTTPRequestPipes || track< collectedHTTPRequestPipes.length);
		assert(null== collectedHTTPRequestPipes || route< collectedHTTPRequestPipes[track].length) : "p "+route+" vs "+ collectedHTTPRequestPipes[track].length;
		return null!= collectedHTTPRequestPipes ? collectedHTTPRequestPipes[track][route] : new ArrayList<Pipe<HTTPRequestSchema>>();
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
	 * @param track int arg used in collectedServerResponsePipes
	 * @return (Pipe[]) list.toArray(new Pipe[list.size()])
	 */
	public final Pipe<ServerResponseSchema>[] buildToOrderArray(int track) {
		if (null==collectedServerResponsePipes || collectedServerResponsePipes.length==0) {
			return new Pipe[0];
		} else {
			ArrayList<Pipe<ServerResponseSchema>> list = collectedServerResponsePipes[track];
			return (Pipe<ServerResponseSchema>[]) list.toArray(new Pipe[list.size()]);
		}
	}

	/**
	 *
	 * @param config PipeConfig
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
			
		int maxMessagesQueue = 8;
		int maxMessageSize = 256;
		this.pcm.addConfig(new PipeConfig<MessageSubscription>(MessageSubscription.instance,
															maxMessagesQueue,
															maxMessageSize)); 		


		this.pcm.addConfig(new PipeConfig<TrafficReleaseSchema>(TrafficReleaseSchema.instance, DEFAULT_LENGTH));
		this.pcm.addConfig(new PipeConfig<TrafficAckSchema>(TrafficAckSchema.instance, DEFAULT_LENGTH));

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
		return useNetClient(TLSCerts.define());
	}

	@Override
	public final HTTPClientConfig useInsecureNetClient() {
		return useNetClient((TLSCertificates) null);
	}

	@Override
	public HTTPClientConfigImpl useNetClient(TLSCertificates certificates) {
		if (client != null) {
			throw new RuntimeException("Client already enabled");
		}
		this.client = new HTTPClientConfigImpl(certificates, this.pcm);
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
		return (G) new GreenCommandChannel(this, features, parallelInstanceId,
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
		return (G) new GreenCommandChannel(this, 0, parallelInstanceId,
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
			MsgRuntime<?,?,?> runtime,
			IntHashTable subscriptionPipeLookup,
			Pipe<IngressMessages>[] ingressMessagePipes,
			Pipe<MessagePubSub>[] messagePubSub,
			Pipe<TrafficReleaseSchema>[] masterMsggoOut, 
			Pipe<TrafficAckSchema>[] masterMsgackIn, 
			Pipe<MessageSubscription>[] subscriptionPipes) {

		//NOTE: there is an issue with late subscriptions this count may be too small.
		//assert(subscriptionPipes.length>0) : "should be 1 or more subscription pipes. MessagePubSub pipes: "+messagePubSub.length;
	
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
     * @param runtime final MsgRuntime arg used to check if  arg.builder.threadLimit GT 0
     * @param cleanRunnable final Runnable arg used with runtime.addCleanShutdownRunnable
     * @param dirtyRunnable final Runnable arg used with runtime.addDirtyShutdownRunnable
     * @return scheduler
     */
	public StageScheduler createScheduler(final MsgRuntime runtime,
										  final	Runnable cleanRunnable,
										  final	Runnable dirtyRunnable) {

		final StageScheduler scheduler = 
				MsgRuntime.builder(runtime).threadLimit>0 ?				
		         StageScheduler.defaultScheduler(gm, 
		        		 MsgRuntime.builder(runtime).threadLimit, 
		        		 MsgRuntime.builder(runtime).threadLimitHard) :
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
		return CoresUtil.availableProcessors()*4;
	}

	@Override
	public final int parallelTracks() {
		return behaviorTracks;
	}
	
	public final DeclareBehavior<R> behaviorDefinition() {
		return behaviorTracksDefinition;
	}
	
	@Override
	public void parallelTracks(int trackCount, DeclareBehavior<R> behaviorDefinition) {
		assert(trackCount>0);
		
		this.behaviorTracks = trackCount;
		if (this.behaviorTracksDefinition!=null) {
			throw new UnsupportedOperationException("tracks may not be set more than once");
		}
		this.behaviorTracksDefinition = behaviorDefinition;
		
	}
	
	@Override
	public final RouteDefinition defineRoute(HTTPHeader ... headers) {			
		return new RouteDefinitionImpl(this.routerConfig(), headers);
	}
	
	
	@Override
	public final JSONExtractor defineJSONSDecoder() {
		return new JSONExtractor();
	}
	@Override
	public final JSONExtractor defineJSONSDecoder(boolean writeDot) {
		return new JSONExtractor(writeDot);
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
	public void enableTelemetryLogging() {
		enableTelemetry(null, 0);
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
				
		if (threadLimit>0 && threadLimit>0 && threadLimit<64) {
			//we must increase the thread limit to ensure telemetry is not starved
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
			MsgRuntime<?,?,?> runtime,
			Pipe<NetResponseSchema>[] netResponsePipes,
			Pipe<ClientHTTPRequestSchema>[] netRequestPipes, 
			Pipe<TrafficReleaseSchema>[][] masterGoOut,
			Pipe<TrafficAckSchema>[][] masterAckIn) {
		////////
		//create the network client stages
		////////
		if (useNetClient(netRequestPipes)) {
			
			{
				int tracks = Math.max(1, runtime.getBuilder().parallelTracks());			
				int totalSessions = ClientHostPortInstance.getSessionCount()-sessionCountBase;
				ccm = new ClientCoordinator((int)Math.ceil(Math.log(4*(totalSessions*tracks))/Math.log(2)), 
											totalSessions,
	                    					this.client.getCertificates(), gm.recordTypeData);
			}
				
			int responseUnwrapCount = this.client.isTLS()? 
					             Math.min(this.client.getUnwrapCount(), netResponsePipes.length) //how many decrypters					             
					             : 2;
			
		    // !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			//TODO: must have N pipes from HTTPClientRequests -> wrap for the count of client connections
			//TODO: if true then we may need a lock for this wrap logic..	             
			
		    int clientSocketWriters = client.getSocketWriterCount();
			int clientWrapperCount = this.client.isTLS()? clientSocketWriters*2 : clientSocketWriters; //writer/encrypter units
						
			int totalOutputPipes = Math.min(clientWrapperCount*client.getConcurentPipesPerWriter(),ClientHostPortInstance.getSessionCount());
			assert(totalOutputPipes<=ClientHostPortInstance.getSessionCount()) : "do not need more output  pipes than we have sesssions.";
		
			if (this.client.isTLS()) {
				pcm.ensureSize(NetPayloadSchema.class, 8, SSLUtil.MinTLSBlock); ///must be large enough for encrypt/decrypt 
			}
			PipeConfig<NetPayloadSchema> clientNetRequestConfig = pcm.getConfig(NetPayloadSchema.class);
					
			//BUILD GRAPH
		
			Pipe<NetPayloadSchema>[] clientRequests = new Pipe[totalOutputPipes];
			int r = totalOutputPipes;
			while (--r>=0) {
				clientRequests[r] = new Pipe<NetPayloadSchema>(clientNetRequestConfig);		
			}			
			
			
			NetGraphBuilder.buildHTTPClientGraph(gm, ccm, 
					this.client.getResponseQueue(), clientRequests, 
					netResponsePipes,
					this.client.getNetResponseCount(),
					this.client.getReleaseCount(),
					responseUnwrapCount,
					clientWrapperCount,
					clientSocketWriters);

			assert(isAllNull(masterGoOut[IDX_NET])) : "ordered traffic calling not supported with HTTP Client.";
			
			HTTPClientRequestStage.newInstance(gm, ccm, netRequestPipes, clientRequests);
							
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
		
		//all these use a smaller rate to ensure MQTT can stay ahead of the internal message passing
		long rate = defaultSleepRateNS>200_000?defaultSleepRateNS/4:defaultSleepRateNS;
		
		return buildMQTTBridge(gm, host, port, clientId, maxInFlight, maxMessageLength, pcm, rate);
	}

	private static MQTTConfigImpl buildMQTTBridge(GraphManager gm, CharSequence host, int port, CharSequence clientId, 
													int maxInFlight, int maxMessageLength, 
													PipeConfigManager localPCM, long rate) {
		ClientCoordinator.registerDomain(host);		
		if (maxInFlight>(1<<15)) {
			throw new UnsupportedOperationException("Does not suppport more than "+(1<<15)+" in flight");
		}
		if (maxMessageLength>(256*(1<<20))) {
			throw new UnsupportedOperationException("Specification does not support values larger than 256M");
		}
		 
		localPCM.ensureSize(MessageSubscription.class, maxInFlight, maxMessageLength);
		

		MQTTConfigImpl mqttBridge = new MQTTConfigImpl(host, port, clientId,
				                    gm, rate, 
				                    (short)maxInFlight, maxMessageLength);
		mqttBridge.beginDeclarations();
		return mqttBridge;
	}
	
	@Override
	public void useInsecureSerialStores(int instances, int largestBlock) {
		logger.warn("Non encrypted serial stores are in use. Please call useSerialStores with a passphrase to ensure data is encrypted.");
		
		int j = instances;
		while (--j>=0) {					
			buildSerialStore(j, null, largestBlock, SequentialReplayerImpl.File);
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
			buildSerialStore(j, noiseProducer, largestBlock, SequentialReplayerImpl.File);
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
			buildSerialStore(j, noiseProducer, largestBlock, SequentialReplayerImpl.File);
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
	

	private void buildSerialStore(int id, NoiseProducer noiseProducer, int largestBlock, SequentialReplayerImpl sri) {
		final short maxInFlightCount = 4;

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
	
		//NOTE: the above pipes will dangle but will remain in the created order so that
		//      they can be attached to the right command channels upon definition

		serialStoreReleaseAck[id] = fromStoreRelease;
		serialStoreReplay[id] = fromStoreConsumer;
		serialStoreWriteAck[id] = fromStoreProducer;
		serialStoreRequestReplay[id] = toStoreConsumer;
		serialStoreWrite[id] = toStoreProducer;
		
		//do not set so we will use the system default.
		PronghornStageProcessor stageProcessor = (gm,stage)-> {
			GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, (long) -1, stage);
			GraphManager.addNota(gm, GraphManager.DOT_BACKGROUND, "cornsilk2", stage);
		};

		switch (sri) {
			case File:
				buildLocalFileSequentialReplayer(id, noiseProducer, largestBlock, 
						maxInFlightCount, fromStoreRelease, fromStoreConsumer, fromStoreProducer, 
						toStoreConsumer, toStoreProducer, stageProcessor);
				break;
			case Memory:
				assert(null==noiseProducer) : "Memory sequential replayer does not support encryption";
				buildMemorySequentialReplayer(id, largestBlock, 
						maxInFlightCount, fromStoreRelease, fromStoreConsumer, fromStoreProducer, 
						toStoreConsumer, toStoreProducer, stageProcessor);
				break;
			case Raft:
				buildRaftSequentialReplayer(id, noiseProducer, largestBlock, 
						maxInFlightCount, fromStoreRelease, fromStoreConsumer, fromStoreProducer, 
						toStoreConsumer, toStoreProducer, stageProcessor);
				break;				
		}
		
	}

	private void buildRaftSequentialReplayer(int id, NoiseProducer noiseProducer, int largestBlock,
			short maxInFlightCount, Pipe<PersistedBlobLoadReleaseSchema> fromStoreRelease,
			Pipe<PersistedBlobLoadConsumerSchema> fromStoreConsumer,
			Pipe<PersistedBlobLoadProducerSchema> fromStoreProducer,
			Pipe<PersistedBlobStoreConsumerSchema> toStoreConsumer,
			Pipe<PersistedBlobStoreProducerSchema> toStoreProducer, PronghornStageProcessor stageProcessor) {
		
		
		throw new UnsupportedOperationException("not yet implemented.");
		
		
	}

	private void buildMemorySequentialReplayer(int id, int largestBlock, short maxInFlightCount, 
			Pipe<PersistedBlobLoadReleaseSchema>  fromStoreRelease,
			Pipe<PersistedBlobLoadConsumerSchema> fromStoreConsumer,
			Pipe<PersistedBlobLoadProducerSchema> fromStoreProducer,
			Pipe<PersistedBlobStoreConsumerSchema> toStoreConsumer,
			Pipe<PersistedBlobStoreProducerSchema> toStoreProducer, 
			PronghornStageProcessor stageProcessor) {
		
		MemorySequentialReplayerStage.newInstance(gm,
				fromStoreRelease,fromStoreConsumer,fromStoreProducer,
				toStoreConsumer,toStoreProducer			
				);
		
	}

	private void buildLocalFileSequentialReplayer(int id, NoiseProducer noiseProducer, int largestBlock,
			final short maxInFlightCount, Pipe<PersistedBlobLoadReleaseSchema> fromStoreRelease,
			Pipe<PersistedBlobLoadConsumerSchema> fromStoreConsumer,
			Pipe<PersistedBlobLoadProducerSchema> fromStoreProducer,
			Pipe<PersistedBlobStoreConsumerSchema> toStoreConsumer,
			Pipe<PersistedBlobStoreProducerSchema> toStoreProducer, 
			PronghornStageProcessor stageProcessor) {
		
		File targetDirectory = null;
		try {
			targetDirectory = new File(Files.createTempDirectory("serialStore"+id).toString());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}	
		FileGraphBuilder.buildSequentialReplayer(gm, 
											fromStoreRelease, fromStoreConsumer, fromStoreProducer, 
											toStoreConsumer, toStoreProducer, 
											maxInFlightCount, largestBlock, targetDirectory, 
											noiseProducer, stageProcessor);
		
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
		
		int pt = behaviorTracks<1?1:behaviorTracks;
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
		PrivateTopic obj = new PrivateTopic(topic, config, hideTopics, this);
		
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

//	@Override
//	public void setGlobalSLALatencyNS(long ns) {
//		GraphManager.addDefaultNota(gm, GraphManager.SLA_LATENCY, ns);
//	}

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
			
			for(int i = 0; i < pendingStagesToBuild.size(); i++) {			
				PendingStageBuildable stage = pendingStagesToBuild.get(i);	
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
	private BehaviorNameable[] possiblePrivateCmds = new BehaviorNameable[16];
	
	//ReactiveListenerStage
	private ArrayList<PendingStageBuildable>[] possiblePrivateBehaviors = new ArrayList[16];
	
	
	private int[] possiblePrivateTopicsProducerCount = new int[16];
	private CharSequence[] possiblePrivateTopicsTopic = new CharSequence[16];
	
	public void possiblePrivateTopicProducer(BehaviorNameable producer, String topic, int track) {
		
		
		//do not want to confuse all the tracks while looking for private topics, we only look at no track and first track
		if (track<=0) {
		
			int id = (int)TrieParserReaderLocal.get().query(possibleTopics, topic);
			if (-1 == id) {
				growPossiblePrivateTopics();			
				possiblePrivateCmds[possiblePrivateTopicsCount] = producer;
				possiblePrivateTopicsTopic[possiblePrivateTopicsCount]=topic;
				possiblePrivateTopicsProducerCount[possiblePrivateTopicsCount]++;
				possibleTopics.setUTF8Value(topic, possiblePrivateTopicsCount++);
			} else {		
	            //only record once for same channel and topic pair
				if (producer != possiblePrivateCmds[id]) {
					possiblePrivateCmds[id] = producer;
					possiblePrivateTopicsProducerCount[id]++;
				}
			}
		}
	}
	
	public void possiblePrivateTopicConsumer(PendingStageBuildable listener, CharSequence topic, int track) {
			
		assert(null!=listener.behaviorName());
		//do not want to confuse all the tracks while looking for private topics, we only look at no track and first track
		if (track<=0) {
			int id = (int)TrieParserReaderLocal.get().query(possibleTopics, topic);
			if (-1 == id) {
				growPossiblePrivateTopics();			
				if (null==possiblePrivateBehaviors[possiblePrivateTopicsCount]) {
					possiblePrivateBehaviors[possiblePrivateTopicsCount] = new ArrayList<PendingStageBuildable>();
				}
				possiblePrivateBehaviors[possiblePrivateTopicsCount].add(listener);				
				possiblePrivateTopicsTopic[possiblePrivateTopicsCount]=topic;
				possibleTopics.setUTF8Value(topic, possiblePrivateTopicsCount++);			
			} else {
				
				if (null==possiblePrivateBehaviors[id]) {
					possiblePrivateBehaviors[id] = new ArrayList();
				}
				possiblePrivateBehaviors[id].add(listener);
				
			}
		}
	}

	
	private void growPossiblePrivateTopics() {
		if (possiblePrivateTopicsCount == possiblePrivateBehaviors.length) {
			//grow
			BehaviorNameable[] newCmds = new BehaviorNameable[possiblePrivateTopicsCount*2];
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
			
			
			
			logger.trace("possible private topic {} {}->{}",topic, possiblePrivateTopicsProducerCount[i], null==possiblePrivateBehaviors[i] ? -1 :possiblePrivateBehaviors[i].size());
			
			boolean madePrivate = false;
			String reasonSkipped = "";
			final int consumers = (null==possiblePrivateBehaviors[i]) ? 0 : possiblePrivateBehaviors[i].size();
			if (possiblePrivateTopicsProducerCount[i]==1) {
				if ((null!=possiblePrivateBehaviors[i]) && ((possiblePrivateBehaviors[i].size())>=1)) {
					//may be valid check that is is not on the list.
					if (!skipTopic(topic)) {
						
						BehaviorNameable producer = possiblePrivateCmds[i];
						String producerName = producer.behaviorName();
						if (null!=producerName) {
							int j = possiblePrivateBehaviors[i].size();
							
							PipeConfig<MessagePrivate> config = (producer instanceof MsgCommandChannel) ?
									                            ((MsgCommandChannel)producer).pcm.getConfig(MessagePrivate.class) :
									                            null;

						    if (j==1) {
						    	String consumerName = (possiblePrivateBehaviors[i].get(0)).behaviorName();
								if (null!=consumerName) {
										if (null!=config) {
											definePrivateTopic(config, topic, producerName, consumerName);
										} else {
											definePrivateTopic(topic, producerName, consumerName);
										}
										madePrivate = true;
	
								}
						    	
						    } else if (j>1) {
						    	String[] names = new String[j];
						    							    	
								while (--j>=0) {
									String consumerName = (possiblePrivateBehaviors[i].get(j)).behaviorName();
									if (null==consumerName) {
										reasonSkipped ="Reason: one of the consuming behaviors have no name.";
										break;
									}
									names[j] = consumerName;
								}
								if (j<0) {
									if (null!=config) {
										defPrivateTopics(config, topic, producerName, names);
									} else {
										definePrivateTopic(topic, producerName, names);
									}
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
			logger.debug("MadePrivate: {} Topic: {} Producers: {} Consumers: {}   {} ", madePrivate, topic, possiblePrivateTopicsProducerCount[i], consumers, reasonSkipped);
			
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

		
	
	
}
