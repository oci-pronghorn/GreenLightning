package com.ociweb.gl.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Behavior;
import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.GreenCommandChannel;
import com.ociweb.gl.api.HTTPClientConfig;
import com.ociweb.gl.api.HTTPRequestReader;
import com.ociweb.gl.api.HTTPServerConfig;
import com.ociweb.gl.api.ClientHostPortInstance;
import com.ociweb.gl.api.ListenerTransducer;
import com.ociweb.gl.api.MsgCommandChannel;
import com.ociweb.gl.api.MsgRuntime;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.TelemetryConfig;
import com.ociweb.gl.api.TimeTrigger;
import com.ociweb.gl.api.transducer.HTTPResponseListenerTransducer;
import com.ociweb.gl.api.transducer.PubSubListenerTransducer;
import com.ociweb.gl.api.transducer.RestListenerTransducer;
import com.ociweb.gl.api.transducer.StateChangeListenerTransducer;
import com.ociweb.gl.impl.http.client.HTTPClientConfigImpl;
import com.ociweb.gl.impl.http.server.HTTPResponseListenerBase;
import com.ociweb.gl.impl.http.server.HTTPServerConfigImpl;
import com.ociweb.gl.impl.mqtt.MQTTConfigImpl;
import com.ociweb.gl.impl.schema.IngressMessages;
import com.ociweb.gl.impl.schema.MessagePrivate;
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
import com.ociweb.gl.impl.telemetry.TelemetryConfigImpl;
import com.ociweb.json.JSONExtractorCompleted;
import com.ociweb.pronghorn.network.ClientCoordinator;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.TLSCertificates;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.http.CompositePath;
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
import com.ociweb.pronghorn.stage.route.ReplicatorStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.util.Appendables;
import com.ociweb.pronghorn.util.Blocker;
import com.ociweb.pronghorn.util.TrieParser;
import com.ociweb.pronghorn.util.TrieParserReader;

public class BuilderImpl implements Builder {

	//NB: The Green Lightning maximum header size 64K is defined here HTTP1xRouterStage.MAX_HEADER
	protected static final int MAXIMUM_INCOMMING_REST_SIZE = 2*HTTP1xRouterStage.MAX_HEADER;
	//  This has a large impact on memory usage but also on performance volume
	protected static final int MINIMUM_INCOMMING_REST_REQUESTS_IN_FLIGHT = 1<<6;
	protected static final int MINIMUM_TLS_BLOB_SIZE = 1<<15;

	protected long timeTriggerRate;
	protected long timeTriggerStart;

	
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
	
	public void usePrivateTopicsExclusively() {
		if (hasPrivateTopicsChecked) {
			throw new UnsupportedOperationException("Must set in declare configuration section before startup");
		}
		isAllPrivateTopics = true;
	}
	
	public boolean isAllPrivateTopics() {
		hasPrivateTopicsChecked = true;
		return isAllPrivateTopics;
	}
	    
    public final ReactiveOperators operators;

    private final Set<String> behaviorNames = new HashSet<String>();
    
    //will throw if a duplicate stage name is detected.
    public String validateUniqueName(String behaviorName, int trackId) {
    	
    	String fullName = behaviorName;
    	//confirm stage name is not found..
    	if (behaviorNames.contains(behaviorName)) {
    		throw new UnsupportedOperationException("Duplicate name detected: "+behaviorName);
    	}
    	
    	if (trackId>=0) {
    		fullName = behaviorName+"."+trackId;
    		//additional check for name+"."+trackId
    		if (behaviorNames.contains(fullName)) {
        		throw new UnsupportedOperationException("Duplicate name detected: "+fullName);
        	}
    		//add the name+"."+name
    		behaviorNames.add(fullName);//never add the root since we are watching that no one else did.
    	} else {
    		//add the stage name
    		behaviorNames.add(behaviorName);
    	}
    	
    	return fullName;
    }
    
    
    private IntHashTable netPipeLookup = new IntHashTable(7);//Initial default size

	public void registerHTTPClientId(int routeId, int pipeIdx) {
				
		if ( (IntHashTable.count(netPipeLookup)<<1) >= IntHashTable.size(netPipeLookup) ) {
			//must grow first since we are adding many entries
			netPipeLookup = IntHashTable.doubleSize(netPipeLookup);			
		}
				
		//TODO: netPipeLookup is the entry point for JSON extraction??
		//      we need to stroe extracor so its done when we do the lookup.
		
		
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

	public ClientCoordinator getClientCoordinator() {
		return ccm;
	}

	@Override
	public HTTPServerConfig useHTTP1xServer(int bindPort) {
		if (server != null) throw new RuntimeException("Server already enabled");
		this.server = new HTTPServerConfigImpl(bindPort);
		server.beginDeclarations();
		return server;
	}

	public final HTTPServerConfig getHTTPServerConfig() {
		return this.server;
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
 
    
	public final boolean appendPipeMappingIncludingGroupIds(Pipe<HTTPRequestSchema> pipe,
											            int parallelId,
											            int ... groupIds) {
		lazyCreatePipeLookupMatrix();
		return routerConfig().appendPipeIdMappingForIncludedGroupIds(pipe, parallelId, collectedHTTPRequstPipes, groupIds);
	}
    
	public final boolean appendPipeMappingExcludingGroupIds(Pipe<HTTPRequestSchema> pipe,
					                            int parallelId,
					                            int ... groupIds) {
		lazyCreatePipeLookupMatrix();
		return routerConfig().appendPipeIdMappingForExcludedGroupIds(pipe, parallelId, collectedHTTPRequstPipes, groupIds);
	}
	
	public final boolean appendPipeMappingAllGroupIds(Pipe<HTTPRequestSchema> pipe,
									int parallelId) {
		lazyCreatePipeLookupMatrix();
		return routerConfig().appendPipeIdMappingForAllGroupIds(pipe, parallelId, collectedHTTPRequstPipes);
	}
	
	final ArrayList<Pipe<HTTPRequestSchema>>[][] targetPipeMapping() {
		lazyCreatePipeLookupMatrix();
		return collectedHTTPRequstPipes;
	}
	
	public final ArrayList<Pipe<HTTPRequestSchema>> buildFromRequestArray(int r, int p) {
		assert(r<collectedHTTPRequstPipes.length);
		assert(p<collectedHTTPRequstPipes[r].length) : "p "+p+" vs "+collectedHTTPRequstPipes[r].length;
		return null!=collectedHTTPRequstPipes ? collectedHTTPRequstPipes[r][p] : new ArrayList<Pipe<HTTPRequestSchema>>();
	}
	
	
	private void lazyCreatePipeLookupMatrix() {
		if (null==collectedHTTPRequstPipes) {
			
			int parallelism = parallelTracks();
			int routesCount = routerConfig().totalPathsCount();
			
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
		this.args = new ArgumentParser(args);
		
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

	public final HTTPClientConfig getHTTPClientConfig() {
		return this.client;
	}

	public final long getTriggerRate() {
		return timeTriggerRate;
	}
	public final long getTriggerStart() {
		return timeTriggerStart;
	}

    public <R extends ReactiveListenerStage> R createReactiveListener(GraphManager gm,  Behavior listener, 
    		                		Pipe<?>[] inputPipes, Pipe<?>[] outputPipes, 
    		                		ArrayList<ReactiveManagerPipeConsumer> consumers,
    		                		int parallelInstance, String nameId) {
    	assert(null!=listener);
    	
    	return (R) new ReactiveListenerStage(gm, listener, 
    			                             inputPipes, outputPipes, 
    			                             consumers, this, parallelInstance, nameId);
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

	protected final void createMessagePubSubStage(
			MsgRuntime<?,?> runtime,
			IntHashTable subscriptionPipeLookup,
			Pipe<IngressMessages>[] ingressMessagePipes,
			Pipe<MessagePubSub>[] messagePubSub,
			Pipe<TrafficReleaseSchema>[] masterMsggoOut, 
			Pipe<TrafficAckSchema>[] masterMsgackIn, 
			Pipe<MessageSubscription>[] subscriptionPipes) {


		new MessagePubSubStage(this.gm, runtime, subscriptionPipeLookup, this, 
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


	
	public final void addStartupSubscription(CharSequence topic, int systemHash, int parallelInstance) {

		Pipe<MessagePubSub> pipe = getTempPipeOfStartupSubscriptions();

		if (PipeWriter.tryWriteFragment(pipe, MessagePubSub.MSG_SUBSCRIBE_100)) {
			
    		DataOutputBlobWriter<MessagePubSub> output = PipeWriter.outputStream(pipe);
    		output.openField();
    		output.append(topic);
    		//this is in a track amay need a suffix.
    		if (parallelInstance>=0) { 
    			if (BuilderImpl.hasNoUnscopedTopics()) {
    				//add suffix..
    				output.append('/');
    				Appendables.appendValue(output, parallelInstance);
    			} else {
    				if (BuilderImpl.notUnscoped(reader, output)) {
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
		
		if (telemetry != null) {
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

	
	private final TrieParserReader localReader = new TrieParserReader(0, true);
	
	@Override
	public long fieldId(int routeId, byte[] fieldName) {	
		return TrieParserReader.query(localReader, this.routeExtractionParser(routeId), fieldName, 0, fieldName.length, Integer.MAX_VALUE);
	}
	
	@Override
	public final CompositePath defineRoute(JSONExtractorCompleted extractor, byte[] ... headers) {
		return routerConfig().registerCompositeRoute(extractor, headers);
	}
	@Override
	public final CompositePath defineRoute(byte[] ... headers) {
		return routerConfig().registerCompositeRoute(headers);
	}

	
	@Override
	public final int defineRoute(CharSequence route, JSONExtractorCompleted extractor, byte[] ... headers) {
		return routerConfig().registerCompositeRoute(extractor,headers).path(route).routeId();
	}
	
	@Override
	public final int defineRoute(CharSequence route, byte[] ... headers) {
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
		return routerConfig().httpSpec.headerParser();
	}

	public final Pipe<HTTPRequestSchema> newHTTPRequestPipe(PipeConfig<HTTPRequestSchema> restPipeConfig) {
		final boolean hasNoRoutes = (0==routerConfig().totalPathsCount());
		Pipe<HTTPRequestSchema> pipe = new Pipe<HTTPRequestSchema>(restPipeConfig) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<HTTPRequestSchema> createNewBlobReader() {
				return new HTTPRequestReader(this, hasNoRoutes);
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
		
		if (threadLimit>0) {
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
		defaultSleepRateNS = Math.max(ns, 2000); //protect against too small 
	}


	public void buildStages(MsgRuntime runtime) {

		IntHashTable subscriptionPipeLookup2 = MsgRuntime.getSubPipeLookup(runtime); 
		GraphManager gm = MsgRuntime.getGraphManager(runtime);
		
		Pipe<NetResponseSchema>[] httpClientResponsePipes = GraphManager.allPipesOfTypeWithNoProducer(gm, NetResponseSchema.instance);
		Pipe<MessageSubscription>[] subscriptionPipes = GraphManager.allPipesOfTypeWithNoProducer(gm, MessageSubscription.instance);
		
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
						
        long timeout = 20_000; //20 seconds
		
		int maxGoPipeId = 0;
					
								
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
				
		int copGoAck = commandChannelCount;
		//logger.info("command channel count to be checked {}",copGoAck);
		while (--copGoAck>=0) {
		
			Pipe<TrafficReleaseSchema>[] goOut = new Pipe[eventSchemas];
			Pipe<TrafficAckSchema>[] ackIn = new Pipe[eventSchemas];
			
			//only setup the go and in pipes if the cop is used.
			if (null != orderPipes[copGoAck]) {
				int features = getFeatures(gm, orderPipes[copGoAck]);
				boolean hasConnections = false;
				if ((features&Behavior.DYNAMIC_MESSAGING) != 0) {
					hasConnections = true;		 		
			 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_MSG);
				}
				if ((features&Behavior.NET_REQUESTER) != 0) {
					hasConnections = true;		 		
			 		maxGoPipeId = populateGoAckPipes(maxGoPipeId, masterGoOut, masterAckIn, goOut, ackIn, IDX_NET);
				}
				TrafficCopStage.newInstance(gm, 
											timeout, orderPipes[copGoAck], 
											ackIn, goOut, 
											runtime, this);
			} else {
				logger.info("oops get features skipped since no cops but needed for private topics");
			}

//			if (true | hasConnections) {
//				TrafficCopStage trafficCopStage = new TrafficCopStage(gm, 
//						timeout, orderPipes[t], 
//						ackIn, goOut, 
//						runtime, this);
//			} else {
//				//this optimization can no longer be done due to the use of shutdown on command channel.
//				//    revisit this later...
//				//TODO: we can reintroduce this as long has we have a stage here which does shutdown on -1;
//				PipeCleanerStage.newInstance(gm, orderPipes[t]);
//			}
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
			
			int maxPartialResponses = Math.max(2,ClientHostPortInstance.getSessionCount());	
			int connectionsInBits = (int)Math.ceil(Math.log(maxPartialResponses)/Math.log(2));

			int netResponseCount = 8;
			int responseQueue = 10;
			
			//must be adjusted together
			int outputsCount = 1; //count of pipes to channel writer
			int clientWriters = 1; //count of channel writer stages
			
			//TODO: delete this block if this works, these checks where here before we removed cops.
//			if (masterGoOut[IDX_NET].length != masterAckIn[IDX_NET].length) {
//				throw new UnsupportedOperationException(masterGoOut[IDX_NET].length+"!="+masterAckIn[IDX_NET].length);
//			}
//			if (masterGoOut[IDX_NET].length != netRequestPipes.length) {
//				throw new UnsupportedOperationException(masterGoOut[IDX_NET].length+"!="+netRequestPipes.length);
//			}
//			
//			assert(masterGoOut[IDX_NET].length == masterAckIn[IDX_NET].length);
//			assert(masterGoOut[IDX_NET].length == netRequestPipes.length);

			PipeConfig<NetPayloadSchema> clientNetRequestConfig = pcm.getConfig(NetPayloadSchema.class);
					
			//BUILD GRAPH
			ccm = new ClientCoordinator(connectionsInBits, maxPartialResponses, this.client.getCertificates());
		
			Pipe<NetPayloadSchema>[] clientRequests = new Pipe[outputsCount];
			int r = outputsCount;
			while (--r>=0) {
				clientRequests[r] = new Pipe<NetPayloadSchema>(clientNetRequestConfig);		
			}
			HTTPClientRequestTrafficStage requestStage = new HTTPClientRequestTrafficStage(gm, runtime, this, ccm, netRequestPipes, masterGoOut[IDX_NET], masterAckIn[IDX_NET], clientRequests);


			int releaseCount = 1024;
			int writeBufferMultiplier = 20;
			int responseUnwrapCount = 2;
			int clientWrapperCount = 2;
	
			
			NetGraphBuilder.buildHTTPClientGraph(gm, ccm, 
												responseQueue, clientRequests, 
												netResponsePipes,
												netResponseCount,
												releaseCount,
												writeBufferMultiplier,
												responseUnwrapCount,
												clientWrapperCount,
												clientWriters);
						
		}
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
		assert(producer instanceof ReactiveListenerStage) : "TrafficOrderSchema must only come from Reactor stages but was "+producer.getClass().getSimpleName();
		
		return ((ReactiveListenerStage)producer).getFeatures(orderPipe);
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
	
	///////////////////////////////////
	//fields supporting private topics
	///////////////////////////////////
	private final TrieParser privateTopicSource = new TrieParser();
	private final TrieParser privateTopicTarget = new TrieParser();		
	private final List<List<PrivateTopic>> privateSourceTopics = new ArrayList<List<PrivateTopic>>();
	private final List<List<PrivateTopic>> privateTargetTopics = new ArrayList<List<PrivateTopic>>();
	private final TrieParserReader reader = new TrieParserReader();
    private final List<String> dynamicTopicPublishers = new ArrayList<String>();
	private final List<String> dynamicTopicSubscribers = new ArrayList<String>();
	//TODO: MUST HAVE ARRAY OF TOPICS TO LOOK UP BY PIPE?
	///////////////////////////////////		
	
	public List<PrivateTopic> getPrivateTopicsFromSource(String source) {
		int sourceId = (int)TrieParserReader.query(reader, privateTopicSource, source);
		List<PrivateTopic> result = (sourceId<0) ? Collections.EMPTY_LIST : privateSourceTopics.get(sourceId);		
		return result;
	}
	
	public List<PrivateTopic> getPrivateTopicsFromTarget(String target) {
		int targetId = (int)TrieParserReader.query(reader, privateTopicTarget, target);
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
		
		if (targets.length<=1) {
			throw new UnsupportedOperationException("only call this with multiple targets");
		}
				
		boolean hideTopics = false;
		PrivateTopic sourcePT = new PrivateTopic(topic, 
				                                 queueLength, 
				                                 maxMessageSize, 
				                                 hideTopics,
				                                 this);
		
		List<PrivateTopic> localSourceTopics = null;
		int sourceId = (int)TrieParserReader.query(reader, privateTopicSource, source);
		if (sourceId<0) {
			localSourceTopics = new ArrayList<PrivateTopic>();			
			privateTopicSource.setUTF8Value(source, privateSourceTopics.size());
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
				
				trgtTopics[t] = new PrivateTopic(topic, trgtConfig, this);
				
				trgts[t] = trgtTopics[t].getPipe(pt);
	
				List<PrivateTopic> localTargetTopics = null;
				int targetId = (int)TrieParserReader.query(reader, privateTopicTarget, targets[t]);
				if (targetId<0) {
					localTargetTopics = new ArrayList<PrivateTopic>();
					privateTopicTarget.setUTF8Value( targets[t], privateTargetTopics.size());
					privateTargetTopics.add(localTargetTopics);
				} else {
					localTargetTopics = privateTargetTopics.get(targetId); 
				}
				
				localTargetTopics.add(trgtTopics[t]);
			}
			ReplicatorStage.newInstance(gm, src, trgts);
		
		}
		
		
		
		
		
		
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
		
		List<PrivateTopic> localSourceTopics = null;
		int sourceId = (int)TrieParserReader.query(reader, privateTopicSource, source);
		if (sourceId<0) {
			localSourceTopics = new ArrayList<PrivateTopic>();
			//logger.info("record source '{}'",source);
			privateTopicSource.setUTF8Value(source, privateSourceTopics.size());
			privateSourceTopics.add(localSourceTopics);
		} else {
			localSourceTopics = privateSourceTopics.get(sourceId);
		}
		localSourceTopics.add(obj);
		
		List<PrivateTopic> localTargetTopics = null;
		int targetId = (int)TrieParserReader.query(reader, privateTopicTarget, target);
		if (targetId<0) {
			localTargetTopics = new ArrayList<PrivateTopic>();
			//logger.info("record target '{}'",target);
			privateTopicTarget.setUTF8Value(target, privateTargetTopics.size());
			privateTargetTopics.add(localTargetTopics);
		} else {
			localTargetTopics = privateTargetTopics.get(targetId); 
		}
		localTargetTopics.add(obj);
		
		
		
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

}
