package com.ociweb.gl.impl;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.api.Builder;
import com.ociweb.gl.api.CommandChannel;
import com.ociweb.gl.api.GreenRuntime;
import com.ociweb.gl.api.HTTPResponseListener;
import com.ociweb.gl.api.NetResponseWriter;
import com.ociweb.gl.api.PayloadReader;
import com.ociweb.gl.api.PubSubListener;
import com.ociweb.gl.api.RestListener;
import com.ociweb.gl.api.StateChangeListener;
import com.ociweb.gl.api.TimeTrigger;
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
import com.ociweb.pronghorn.network.HTTP1xRouterStageConfig;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderKey;
import com.ociweb.pronghorn.network.config.HTTPHeaderKeyDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.DataOutputBlobWriter;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeWriter;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.scheduling.FixedThreadsScheduler;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.scheduling.ThreadPerStageScheduler;
import com.ociweb.pronghorn.util.Blocker;

public class BuilderImpl implements Builder {

	protected boolean useNetClient;
	protected boolean useNetServer;

	private long timeTriggerRate;
	private long timeTriggerStart;
		
	private Blocker channelBlocker;

	public final GraphManager gm;
	
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
	private final int maxStartupSubs = 64;
	private final int maxTopicLengh  = 128;
	private Pipe<MessagePubSub> tempPipeOfStartupSubscriptions;
	/////////////////
	/////////////////

	protected ReentrantLock devicePinConfigurationLock = new ReentrantLock();

	
	private String bindHost = null;
	private int bindPort = -1;
	private boolean isLarge = false;
	private boolean isTLS = true; 
		
	
	//TODO: set these vales when we turn on the client usage??
	private int connectionsInBit = 3; 
	private int maxPartialResponse = 10;
	

	//////////////////////////////
	//support for REST modules and routing
	//////////////////////////////
	public final HTTPSpecification<HTTPContentTypeDefaults, HTTPRevisionDefaults, HTTPVerbDefaults, HTTPHeaderKeyDefaults> httpSpec = HTTPSpecification.defaultSpec();
	private final HTTP1xRouterStageConfig routerConfig = new HTTP1xRouterStageConfig(httpSpec); 
	//////////////////////////////
	//////////////////////////////

	
	public boolean isLarge() {
		return isLarge;
	}
	
	public boolean isTLS() {
		return isTLS;
	}
	
	public String bindHost() {
		return bindHost;
	}
	
	public int bindPort() {
		return bindPort;
	}
	
    public void enableServer(boolean isTLS, boolean isLarge, String bindHost, int bindPort) {
    	
    	this.useNetServer();
    	this.isTLS = isTLS;
    	this.isLarge = isLarge;
    	this.bindHost = bindHost;
    	this.bindPort = bindPort;
    	
    }

    
    public HTTP1xRouterStageConfig routerConfig() {
    	return routerConfig;
    }
    
    ////////////////////////////
    ///gather and store the server module pipes
    /////////////////////////////
    private ArrayList<Pipe<HTTPRequestSchema>>[][] collectedHTTPRequstPipes;
	private ArrayList<Pipe<ServerResponseSchema>>[] collectedServerResponsePipes;
    
	public void recordPipeMapping(Pipe<HTTPRequestSchema> httpRequestPipe, int routeIdx, int parallelId) {
		
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
	
		//logger.info("added pipe "+httpRequestPipe.id+" to Path "+routeIdx+" to RouterPara: "+parallelId);
		
		collectedHTTPRequstPipes[parallelId][routeIdx].add(httpRequestPipe);

	}
	
	
	public Pipe<HTTPRequestSchema>[] buildFromRequestArray(int r, int p) {
		ArrayList<Pipe<HTTPRequestSchema>> list = collectedHTTPRequstPipes[r][p];
		return (Pipe<HTTPRequestSchema>[]) list.toArray(new Pipe[list.size()]);
	}
	
	
	
	public void recordPipeMapping(Pipe<ServerResponseSchema> netResponse, int parallelInstanceId) {
		
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
	

	public Pipe<ServerResponseSchema>[] buildToOrderArray(int r) {
		ArrayList<Pipe<ServerResponseSchema>> list = collectedServerResponsePipes[r];
		return (Pipe<ServerResponseSchema>[]) list.toArray(new Pipe[list.size()]);
	}
	
	
    public Pipe<ServerResponseSchema> newNetResposnePipe(PipeConfig<ServerResponseSchema> config, int parallelInstanceId) {
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
	
    //liniear search only used once in startup method for the stage.
	public void lookupRouteAndPara(Pipe<?> localPipe, int idx, int[] routes, int[] para) {
		int p = parallelism();

		while (--p >= 0) {

			int r = routerConfig().routesCount();
			while (--r >= 0) {
				ArrayList<Pipe<HTTPRequestSchema>> pipeList = collectedHTTPRequstPipes[p][r];
			
				if (pipeList.contains(localPipe)) {
					routes[idx] = r;
					para[idx] = p;	
					return;
				}
			}
		}
		throw new UnsupportedOperationException("can not find "+localPipe);
	}
	////////////////////////////////
	
	public BuilderImpl(GraphManager gm) {	

		this.gm = gm;

		this.getTempPipeOfStartupSubscriptions().initBuffers();
	}

	public <E extends Enum<E>> boolean isValidState(E state) {

		if (null!=beginningState) {
			return beginningState.getClass()==state.getClass();    		
		}
		return false;
	}

	public <E extends Enum<E>> Builder startStateMachineWith(E state) {   	
		beginningState = state;	
		return this;
	}

	public Builder setTriggerRate(long rateInMS) {
		timeTriggerRate = rateInMS;
		timeTriggerStart = System.currentTimeMillis()+rateInMS;
		return this;
	}
	
	public Builder setTriggerRate(TimeTrigger trigger) {	
		long period = trigger.getRate();
		timeTriggerRate = period;
		long now = System.currentTimeMillis();		
		long soFar = (now % period);		
		timeTriggerStart = (now - soFar) + period;				
		return this;
	}

	public Builder useNetClient() {
		this.useNetClient = true;
		return this;
	}

	public Builder useNetServer() {
		this.useNetServer = true;
		return this;
	}

	public boolean isUseNetServer() {
		return this.useNetServer;
	}

	
	public long getTriggerRate() {
		return timeTriggerRate;
	}
	public long getTriggerStart() {
		return timeTriggerStart;
	}

    public ReactiveListenerStage createReactiveListener(GraphManager gm,  Object listener, Pipe<?>[] inputPipes, Pipe<?>[] outputPipes) {
        return new ReactiveListenerStage(gm, listener, inputPipes, outputPipes, this);
    }

	public CommandChannel newCommandChannel(
												int features,
			                                    int parallelInstanceId,
											    PipeConfig<MessagePubSub> pubSubConfig,
									            PipeConfig<ClientHTTPRequestSchema> netRequestConfig,
									            PipeConfig<TrafficOrderSchema> orderPipe,
									            PipeConfig<ServerResponseSchema> netResponseconfig
			                                ) {
		return new CommandChannel(gm, this, features, parallelInstanceId, pubSubConfig, netRequestConfig, orderPipe, netResponseconfig);
	}

	static final boolean debug = false;

	public void shutdown() {
		//can be overridden by specific hardware impl if shutdown is supported.
	}



	public final void buildStages(
			IntHashTable subscriptionPipeLookup,
			IntHashTable netPipeLookup,			
			Pipe<MessageSubscription>[] subscriptionPipes, //one for each listener of this type (subscription per pipe)
			Pipe<NetResponseSchema>[] netResponsePipes,

			Pipe<TrafficOrderSchema>[] orderPipes,    //one for each command channel 

			Pipe<MessagePubSub>[] messagePubSub,      //one for each command channel 
			Pipe<ClientHTTPRequestSchema>[] netRequestPipes  //one for each command channel
			) {

		int commandChannelCount = orderPipes.length;
		
		
		int eventSchemas = 0;
		

		int TYPE_MSG = eventSchemas++;
		int TYPE_NET = useNetClient(netPipeLookup, netResponsePipes, netRequestPipes) ? eventSchemas++ : -1;
						

		Pipe<TrafficReleaseSchema>[][] masterGoOut = new Pipe[eventSchemas][commandChannelCount];
		Pipe<TrafficAckSchema>[][]     masterAckIn = new Pipe[eventSchemas][commandChannelCount];

		long timeout = 20_000; //20 seconds

		int maxGoPipeId = 0;
		int t = commandChannelCount;
		while (--t>=0) {

			int p = eventSchemas;//major command requests that can come from commandChannels
			Pipe<TrafficReleaseSchema>[] goOut = new Pipe[p];
			Pipe<TrafficAckSchema>[] ackIn = new Pipe[p];
			while (--p>=0) {
				masterGoOut[p][t] = goOut[p] = new Pipe<TrafficReleaseSchema>(releasePipesConfig);
				maxGoPipeId = Math.max(maxGoPipeId, goOut[p].id);
				
				masterAckIn[p][t] = ackIn[p]=new Pipe<TrafficAckSchema>(ackPipesConfig);								
			}
			
			TrafficCopStage trafficCopStage = new TrafficCopStage(gm, timeout, orderPipes[t], ackIn, goOut);

		}
		
		
		
		////////
		//create the network client stages
		////////
		if (useNetClient(netPipeLookup, netResponsePipes, netRequestPipes)) {
			
			if (masterGoOut[TYPE_NET].length != masterAckIn[TYPE_NET].length) {
				throw new UnsupportedOperationException(masterGoOut[TYPE_NET].length+"!="+masterAckIn[TYPE_NET].length);
			}
			if (masterGoOut[TYPE_NET].length != netRequestPipes.length) {
				throw new UnsupportedOperationException(masterGoOut[TYPE_NET].length+"!="+netRequestPipes.length);
			}
			
			assert(masterGoOut[TYPE_NET].length == masterAckIn[TYPE_NET].length);
			assert(masterGoOut[TYPE_NET].length == netRequestPipes.length);
			
				
			PipeConfig<NetPayloadSchema> clientNetRequestConfig = new PipeConfig<NetPayloadSchema>(NetPayloadSchema.instance,4,16000); 		

			//BUILD GRAPH
			
			int connectionsInBits=10;			
			int maxPartialResponses=4;
			ClientCoordinator ccm = new ClientCoordinator(connectionsInBits, maxPartialResponses);

			//TODO: tie this in tonight.
			int inputsCount = 1;
			int outputsCount = 1;
			Pipe<NetPayloadSchema>[] clientRequests = new Pipe[outputsCount];
			int r = outputsCount;
			while (--r>=0) {
				clientRequests[r] = new Pipe<NetPayloadSchema>(clientNetRequestConfig);		
			}
			HTTPClientRequestStage requestStage = new HTTPClientRequestStage(gm, this, ccm, netRequestPipes, masterGoOut[TYPE_NET], masterAckIn[TYPE_NET], clientRequests);
			
			
			NetGraphBuilder.buildHTTPClientGraph(true, gm, maxPartialResponses, ccm, netPipeLookup, 10, 1<<15, clientRequests, netResponsePipes, 2, 2, 2, 2048, 64, 1<<19); 
						
		}// else {
			//System.err.println("skipped  "+IntHashTable.isEmpty(netPipeLookup)+"  "+netResponsePipes.length+"   "+netRequestPipes.length  );
		//}
		
		/////////
		//always create the pub sub and state management stage?
		/////////
		//TODO: only create when subscriptionPipeLookup is not empty and subscriptionPipes has zero length.
		if (IntHashTable.isEmpty(subscriptionPipeLookup) 
			&& subscriptionPipes.length==0
			&& messagePubSub.length==0
			&& masterGoOut[TYPE_MSG].length==0
			&& masterAckIn[TYPE_MSG].length==0) {
			logger.trace("saved some resources by not starting up the unused pub sub service.");
		} else {
		 	createMessagePubSubStage(subscriptionPipeLookup, messagePubSub, masterGoOut[TYPE_MSG], masterAckIn[TYPE_MSG], subscriptionPipes);
		}

		channelBlocker = new Blocker(maxGoPipeId+1);
		   
	       
	}

	private boolean useNetClient(IntHashTable netPipeLookup, Pipe<NetResponseSchema>[] netResponsePipes, Pipe<ClientHTTPRequestSchema>[] netRequestPipes) {

		return !IntHashTable.isEmpty(netPipeLookup) && (netResponsePipes.length!=0) && (netRequestPipes.length!=0);
	}

	private void createMessagePubSubStage(IntHashTable subscriptionPipeLookup,
			Pipe<MessagePubSub>[] messagePubSub,
			Pipe<TrafficReleaseSchema>[] masterMsggoOut, 
			Pipe<TrafficAckSchema>[] masterMsgackIn, 
			Pipe<MessageSubscription>[] subscriptionPipes) {


		new MessagePubSubStage(this.gm, subscriptionPipeLookup, this, messagePubSub, masterMsggoOut, masterMsgackIn, subscriptionPipes);


	}

	public StageScheduler createScheduler(GreenRuntime iotDeviceRuntime) {
		
		final StageScheduler scheduler =  threadLimit <= 0 ? new ThreadPerStageScheduler(gm): 
			                                                 new FixedThreadsScheduler(gm, threadLimit, threadLimitHard);

		Runtime.getRuntime().addShutdownHook(new Thread() {
			public void run() {
				scheduler.shutdown();
				scheduler.awaitTermination(30, TimeUnit.MINUTES);
			}
		});
		return scheduler;
	}


	public boolean isListeningToSubscription(Object listener) {
		return listener instanceof PubSubListener || listener instanceof StateChangeListener<?>;
	}

	public boolean isListeningToHTTPResponse(Object listener) {
		return listener instanceof HTTPResponseListener;
	}

	public boolean isListeningHTTPRequest(Object listener) {
		return listener instanceof RestListener;
	}
	
	/**
	 * access to system time.  This method is required so it can be monitored and simulated by unit tests.
	 */
	public long currentTimeMillis() {
		return System.currentTimeMillis();
	}

	public void blockChannelUntil(int channelId, long timeInMillis) {        
		channelBlocker.until(channelId, timeInMillis);
	}

	public boolean isChannelBlocked(int channelId) {
		if (null != channelBlocker)  {
			return channelBlocker.isBlocked(channelId);
		} else {
			return false;
		}
	}

	public long releaseChannelBlocks(long now) {
		if (null != channelBlocker) {
			channelBlocker.releaseBlocks(now);
			return channelBlocker.durationToNextRelease(now, -1);
		} else {
			return -1; //was not init so there are no possible blocked channels.
		}
	}

	public long nanoTime() {
		return System.nanoTime();
	}

	public Enum[] getStates() {
		return null==beginningState? new Enum[0] : beginningState.getClass().getEnumConstants();
	}

	public void addStartupSubscription(CharSequence topic, int systemHash) {

		Pipe<MessagePubSub> pipe = getTempPipeOfStartupSubscriptions();

		if (PipeWriter.tryWriteFragment(pipe, MessagePubSub.MSG_SUBSCRIBE_100)) {
			PipeWriter.writeUTF8(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_TOPIC_1, topic);
			PipeWriter.writeInt(pipe, MessagePubSub.MSG_SUBSCRIBE_100_FIELD_SUBSCRIBERIDENTITYHASH_4, systemHash);
			PipeWriter.publishWrites(pipe);
		} else {
			throw new UnsupportedOperationException("Limited number of startup subscriptions "+maxStartupSubs+" encountered.");
		}
	}

	private Pipe<MessagePubSub> getTempPipeOfStartupSubscriptions() {
		if (null==tempPipeOfStartupSubscriptions) {

			final PipeConfig<MessagePubSub> messagePubSubConfig = new PipeConfig<MessagePubSub>(MessagePubSub.instance, maxStartupSubs,maxTopicLengh);   
			tempPipeOfStartupSubscriptions = new Pipe<MessagePubSub>(messagePubSubConfig);

		}		

		return tempPipeOfStartupSubscriptions;
	}

	public Pipe<MessagePubSub> consumeStartupSubscriptions() {
		Pipe<MessagePubSub> result = tempPipeOfStartupSubscriptions;
		tempPipeOfStartupSubscriptions = null;//no longer needed
		return result;
	}

	@Override
	public void limitThreads(int threadLimit) {
		this.threadLimit = threadLimit;
		this.threadLimitHard = true;
	}

	@Override
	public void limitThreads() {
		this.threadLimit = Runtime.getRuntime().availableProcessors();
		this.threadLimitHard = false;
	}

	public int parallelism() {
		return parallelism;
	}

	@Override
	public void parallelism(int parallel) {
		parallelism = parallel;
	}

	
	@Override
	public int registerRoute(CharSequence route, HTTPHeaderKey ... headers) {
		
		return routerConfig.registerRoute(route, headerMask(headers));

	}

	private long headerMask(HTTPHeaderKey... headers) {
		long headerLong = 0;
		int i = headers.length;
		while (--i>=0) {
			
			int ord = headers[i].ordinal();			
			if (ord >= 63) {
				throw new UnsupportedOperationException("Used headers must have idx values < 63. Please ask to have this limit raised.");
			}			
			headerLong |= (1L << ord);
			
		}
		return headerLong;
	}


	public ClientCoordinator getClientCoordinator() {

		return useNetClient ? new ClientCoordinator(connectionsInBit, maxPartialResponse) : null;
		
	}

	public Pipe<HTTPRequestSchema> createHTTPRequestPipe(PipeConfig<HTTPRequestSchema> restPipeConfig, int routeIndex, int parallelInstance) {
		Pipe<HTTPRequestSchema> pipe = newHTTPRequestPipe(restPipeConfig);		
		recordPipeMapping(pipe, routeIndex, parallelInstance);		
		return pipe;
	}

	public Pipe<HTTPRequestSchema> newHTTPRequestPipe(PipeConfig<HTTPRequestSchema> restPipeConfig) {
		Pipe<HTTPRequestSchema> pipe = new Pipe<HTTPRequestSchema>(restPipeConfig) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<HTTPRequestSchema> createNewBlobReader() {
				return new PayloadReader(this);
			}
		};
		return pipe;
	}


	




}