package com.ociweb.gl.api;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.BridgeConfigImpl;
import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.ChildClassScanner;
import com.ociweb.gl.impl.ChildClassScannerVisitor;
import com.ociweb.gl.impl.RestListenerBase;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.stage.EgressConverter;
import com.ociweb.gl.impl.stage.IngressConverter;
import com.ociweb.gl.impl.stage.ReactiveListenerStage;
import com.ociweb.gl.impl.stage.ReactiveManagerPipeConsumer;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.ServerPipesConfig;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStageConfig;
import com.ociweb.pronghorn.network.module.FileReadModuleStage;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.PipeConfigManager;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.route.ReplicatorStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;

public class MsgRuntime<B extends BuilderImpl, L extends ListenerFilter> {
 
    public static final Logger logger = LoggerFactory.getLogger(MsgRuntime.class);

    
    protected static final int nsPerMS = 1_000_000;
	public B builder;

	protected final GraphManager gm;

    protected final String[] args;
    
    protected StageScheduler scheduler;
    
    protected String telemetryHost;
    
    //NOTE: keep short since the MessagePubSubStage will STOP consuming message until the one put on here
    //      is actually taken off and consumed.  We have little benefit to making this longer.
    protected static final int defaultCommandChannelSubscriberLength = 8;
    
    protected static final int defaultCommandChannelLength = 16;
    protected static final int defaultCommandChannelMaxPayload = 256; //largest i2c request or pub sub payload
    protected static final int defaultCommandChannelHTTPMaxPayload = 1<<14; //must be at least 32K for TLS support

    protected boolean transducerAutowiring = true;

	private PipeConfig<HTTPRequestSchema> fileRequestConfig;// = builder.restPipeConfig.grow2x();

    protected int netResponsePipeIdx = 0;//this implementation is dependent upon graphManager returning the pipes in the order created!
    protected int subscriptionPipeIdx = 0; //this implementation is dependent upon graphManager returning the pipes in the order created!
    protected final IntHashTable subscriptionPipeLookup = new IntHashTable(10);//NOTE: this is a maximum of 1024 listeners
    protected final IntHashTable netPipeLookup = new IntHashTable(10);//NOTE: this is a maximum of 1024 listeners
    
    private BridgeConfig[] bridges = new BridgeConfig[0];
    
	protected int parallelInstanceUnderActiveConstruction = -1;
	
	protected Pipe<?>[] outputPipes = null;
    protected ChildClassScannerVisitor gatherPipesVisitor = new ChildClassScannerVisitor<MsgCommandChannel>() {
    	
		@Override
		public boolean visit(MsgCommandChannel cmdChnl, Object topParent) {
			    
            IntHashTable usageChecker = getUsageChecker();
            if (null!=usageChecker) {
				if (!ChildClassScanner.notPreviouslyHeld(cmdChnl, topParent, usageChecker)) {
	            	logger.error("Command channel found in "+
	            			topParent.getClass().getSimpleName()+
	            	             " can not be used in more than one Behavior");                	
	            	assert(false) : "A CommandChannel instance can only be used exclusivly by one object or lambda. Double check where CommandChannels are passed in.";                   
	            }
            }
            
            MsgCommandChannel.setListener(cmdChnl, (Behavior)topParent);
            //add this to the count of publishers
            //CharSequence[] supportedTopics = cmdChnl.supportedTopics();
            //get count of subscribers per topic as well.
			//get the pipe ID of the singular PubSub...
			
			outputPipes = PronghornStage.join(outputPipes, cmdChnl.getOutputPipes());
			return true;//keep looking
					
		}

    };
    
    public void disableTransducerAutowiring() {
    	transducerAutowiring = false;
    }
    
    private void keepBridge(BridgeConfig bridge) {
    	boolean isFound = false;
    	int i = bridges.length;
    	while (--i>=0) {
    		isFound |= bridge == bridges[0];
    	}
    	if (!isFound) {
    		
    		i = bridges.length;
    		BridgeConfig[] newArray = new BridgeConfig[i+1];
    		System.arraycopy(bridges, 0, newArray, 0, i);
    		newArray[i] = bridge;
    		bridges = newArray;
    		
    	}
    }
    
    
	public MsgRuntime(String[] args) {
		 this.gm = new GraphManager();
		 this.args = args;
	}

    public String[] args() {
    	return args;
    }
    
	public final <T,S> S bridgeSubscription(CharSequence topic, BridgeConfig<T,S> config) {		
		long id = config.addSubscription(topic);
		keepBridge(config);
		return config.subscriptionConfigurator(id);
	}
	public final <T,S> S bridgeSubscription(CharSequence internalTopic, CharSequence extrnalTopic, BridgeConfig<T,S> config) {		
		long id = config.addSubscription(internalTopic,extrnalTopic);
		keepBridge(config);
		return config.subscriptionConfigurator(id);
	}
	public final <T,S> S bridgeSubscription(CharSequence internalTopic, CharSequence extrnalTopic, BridgeConfig<T,S> config, IngressConverter converter) {		
		long id = config.addSubscription(internalTopic,extrnalTopic,converter);
		keepBridge(config);
		return config.subscriptionConfigurator(id);
	}
	
	public final <T,S> T bridgeTransmission(CharSequence topic, BridgeConfig<T,S> config) {		
		long id = config.addTransmission(this, topic);
		keepBridge(config);
		return config.transmissionConfigurator(id);
	}
	public final <T,S> T bridgeTransmission(CharSequence internalTopic, CharSequence extrnalTopic, BridgeConfig<T,S> bridge) {		
		long id = bridge.addTransmission(this, internalTopic,extrnalTopic);
		keepBridge(bridge);
		return bridge.transmissionConfigurator(id);
	}	
	public final <T,S> T bridgeTransmission(CharSequence internalTopic, CharSequence extrnalTopic, BridgeConfig<T,S> config, EgressConverter converter) {		
		long id = config.addTransmission(this, internalTopic,extrnalTopic, converter);
		keepBridge(config);
		return config.transmissionConfigurator(id);
	}	
	
    public final L addRestListener(RestListener listener) {
    	return (L) registerListenerImpl(listener);
    }
        
    public final L addResponseListener(HTTPResponseListener listener) {
    	return (L) registerListenerImpl(listener);
    }
    
    public final L addStartupListener(StartupListener listener) {
        return (L) registerListenerImpl(listener);
    }
	    
    public final L addShutdownListener(ShutdownListener listener) {
        return (L) registerListenerImpl(listener);
    }	
    
    public final L addTimePulseListener(TimeListener listener) {
        return (L) registerListenerImpl(listener);
    }
    
    public final L addPubSubListener(PubSubListener listener) {
        return (L) registerListenerImpl(listener);
    }

    public final <E extends Enum<E>> L addStateChangeListener(StateChangeListener<E> listener) {
        return (L) registerListenerImpl(listener);
    }
      
    public L registerListener(Behavior listener) {
    	return (L) registerListenerImpl(listener);
    }
  
   
    public long fieldId(int routeId, byte[] fieldName) {
    	return builder.fieldId(routeId, fieldName);
    }    
    
    protected void logStageScheduleRates() {
        int totalStages = GraphManager.countStages(gm);
           for(int i=1;i<=totalStages;i++) {
               PronghornStage s = GraphManager.getStage(gm, i);
               if (null != s) {
                   
                   Object rate = GraphManager.getNota(gm, i, GraphManager.SCHEDULE_RATE, null);
                   if (null == rate) {
                       logger.debug("{} is running without breaks",s);
                   } else  {
                       logger.debug("{} is running at rate of {}",s,rate);
                   }
               }
               
           }
    }
    
    public String getArgumentValue(String longName, String shortName, String defaultValue) {
    	return getOptArg(longName,shortName, args, defaultValue);
    }
    
    public boolean hasArgument(String longName, String shortName) {
    	return hasArg(longName, shortName, this.args);
    }

    public static String getOptArg(String longName, String shortName, String[] args, String defaultValue) {
        
        String prev = null;
        for (String token : args) {
            if (longName.equals(prev) || shortName.equals(prev)) {
                if (token == null || token.trim().length() == 0 || token.startsWith("-")) {
                    return defaultValue;
                }
                return reportChoice(longName, shortName, token.trim());
            }
            prev = token;
        }
        return reportChoice(longName, shortName, defaultValue);
    }
    

    public static boolean hasArg(String longName, String shortName, String[] args) {
        for(String token : args) {
            if(longName.equals(token) || shortName.equals(token)) {
            	reportChoice(longName, shortName, "");
                return true;
            }
        }
        return false;
    }
    
    static String reportChoice(final String longName, final String shortName, final String value) {
        System.out.append(longName).append(" ").append(shortName).append(" ").append(value).append("\n");
        return value;
    }
    

    protected void configureStageRate(Object listener, ReactiveListenerStage stage) {
        //if we have a time event turn it on.
        long rate = builder.getTriggerRate();
        if (rate>0 && listener instanceof TimeListener) {
            stage.setTimeEventSchedule(rate, builder.getTriggerStart());
            //Since we are using the time schedule we must set the stage to be faster
            long customRate =   (rate*nsPerMS)/NonThreadScheduler.granularityMultiplier;// in ns and guanularityXfaster than clock trigger
            long appliedRate = Math.min(customRate,builder.getDefaultSleepRateNS());
            GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, appliedRate, stage);
        }
    }

      
    
    public StageScheduler getScheduler() {
        return scheduler;
    }    

    public void shutdownRuntime() {
    	//only do if not already done.
    	if (!ReactiveListenerStage.isShutdownRequested()) {
    	
	    	if (null == scheduler || null == builder) {
	    		System.exit(0);
	    		return;
	    	}
	    	
	    	final Runnable lastCall = new Runnable() {    		
	    		@Override
	    		public void run() {
	    			//all the software has now stopped so shutdown the hardware now.
	    			builder.shutdown();
	    		}    		
	    	};
	    	
	    	//notify all the reactors to begin shutdown.
	    	ReactiveListenerStage.requestSystemShutdown(new Runnable() {
	
				@Override
				public void run() {
					scheduler.shutdown();
					scheduler.awaitTermination(3, TimeUnit.SECONDS, lastCall, lastCall);
				}
	    		
	    	});
    	}
    }

    public void shutdownRuntime(int timeoutInSeconds) {
        //clean shutdown providing time for the pipe to empty
        scheduler.shutdown();
        scheduler.awaitTermination(timeoutInSeconds, TimeUnit.SECONDS); //timeout error if this does not exit cleanly withing this time.
        //all the software has now stopped so now shutdown the hardware.
        builder.shutdown();
    }
    

	//////////
    //only build this when assertions are on
    //////////
    public static IntHashTable cmdChannelUsageChecker;
    static {
        assert(setupForChannelAssertCheck());
    }    
    private static boolean setupForChannelAssertCheck() {
        cmdChannelUsageChecker = new IntHashTable(9);
        return true;
    }    
    private static IntHashTable getUsageChecker() {
    	return cmdChannelUsageChecker;
    }
    
    protected int addGreenPipesCount(Behavior listener, int pipesCount) {
		
		if (this.builder.isListeningToHTTPResponse(listener)) {
        	pipesCount++; //these are calls to URL responses        	
        }
        
        if (this.builder.isListeningToSubscription(listener)) {
        	pipesCount++;
        }
        
        if (this.builder.isListeningHTTPRequest(listener)) {
        	pipesCount += ListenerConfig.computeParallel(builder, parallelInstanceUnderActiveConstruction);
        }
		return pipesCount;
	}


	protected void populateGreenPipes(Behavior listener, int pipesCount, Pipe<?>[] inputPipes) {
		if (this.builder.isListeningToHTTPResponse(listener)) {        	
        	Pipe<NetResponseSchema> netResponsePipe1 = new Pipe<NetResponseSchema>(builder.pcm.getConfig(NetResponseSchema.class)) {
				@SuppressWarnings("unchecked")
				@Override
				protected DataInputBlobReader<NetResponseSchema> createNewBlobReader() {
					return new HTTPResponseReader(this);
				}
			};
			Pipe<NetResponseSchema> netResponsePipe = netResponsePipe1;        	
            int pipeIdx = netResponsePipeIdx++;
            inputPipes[--pipesCount] = netResponsePipe;            
            boolean addedItem = IntHashTable.setItem(netPipeLookup, builder.behaviorId((Behavior)listener), pipeIdx);
            if (!addedItem) {
            	throw new RuntimeException("Behaviors must only be registered once.\n\nAn instance of "
            			+listener.getClass().getCanonicalName()+" was discovered to be registered twice. "
            			+"\n\nThis stack trace captures the second known registration occurance.");
            }
            
        }
        
        if (this.builder.isListeningToSubscription(listener)) {   
            inputPipes[--pipesCount]=buildPublishPipe(listener);
        }

        
        //if we push to this 1 pipe all the requests...
        //JoinStage to take N inputs and produce 1 output.
        //we use splitter for single pipe to 2 databases
        //we use different group for parallel processing
        //for mutiple we must send them all to the reactor.
        
        
		if (this.builder.isListeningHTTPRequest(listener) ) {
        	
			Pipe<HTTPRequestSchema>[] httpRequestPipes;
        	httpRequestPipes = ListenerConfig.newHTTPRequestPipes(builder,  ListenerConfig.computeParallel(builder, parallelInstanceUnderActiveConstruction));

        	int i = httpRequestPipes.length;        	
        	assert(i>0) : "This listens to Rest requests but none have been routed here";        
        	while (--i >= 0) {        
        		inputPipes[--pipesCount] = httpRequestPipes[i];                		
        	}
        }
	}

    /**
     * This pipe returns all the data this object has requested via subscriptions elsewhere.
     * @param listener
     */
	public Pipe<MessageSubscription> buildPublishPipe(Object listener) {
		Pipe<MessageSubscription> subscriptionPipe = buildMessageSubscriptionPipe();		
		return attachListenerIndexToMessageSubscriptionPipe(listener, subscriptionPipe);
	}
	
	public Pipe<MessageSubscription> buildPublishPipe(int listenerHash) {
		Pipe<MessageSubscription> subscriptionPipe = buildMessageSubscriptionPipe();	
		if (!IntHashTable.setItem(subscriptionPipeLookup, listenerHash, subscriptionPipeIdx++)) {
			throw new RuntimeException("HashCode must be unique");
		}
		assert(!IntHashTable.isEmpty(subscriptionPipeLookup));
		return subscriptionPipe;
	}

	private Pipe<MessageSubscription> attachListenerIndexToMessageSubscriptionPipe(Object listener,
			Pipe<MessageSubscription> subscriptionPipe) {
		//store this value for lookup later
		//logger.info("adding hash listener {} to pipe  ",System.identityHashCode(listener));
		if (!IntHashTable.setItem(subscriptionPipeLookup, System.identityHashCode(listener), subscriptionPipeIdx++)) {
			throw new RuntimeException("Could not find unique identityHashCode for "+listener.getClass().getCanonicalName());
		}
		assert(!IntHashTable.isEmpty(subscriptionPipeLookup));
		return subscriptionPipe;
	}

	private Pipe<MessageSubscription> buildMessageSubscriptionPipe() {
		
	    Pipe<MessageSubscription> subscriptionPipe = new Pipe<MessageSubscription>(builder.pcm.getConfig(MessageSubscription.class)) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<MessageSubscription> createNewBlobReader() {
				return new MessageReader(this);
			}
		};
		return subscriptionPipe;
	}
	
	protected void constructingParallelInstance(int i) {
		parallelInstanceUnderActiveConstruction = i;
	}

	protected void constructingParallelInstancesEnding() {
		parallelInstanceUnderActiveConstruction = -1;
	}
	
	//////////////////
	//server and other behavior
	//////////////////
	public void declareBehavior(MsgApp app) {
		
		if (builder.isUseNetServer()) {
		    buildGraphForServer(app);
		} else {            	
			app.declareBehavior(this);
			
			if (app instanceof MsgAppParallel) {
				int parallelism = builder.parallelism();
				//since server was not started and did not create each parallel instance this will need to be done here
	   
				for(int i = 0;i<parallelism;i++) { //do not use this loop, we will loop inside server setup..
			    	constructingParallelInstance(i);
			    	((MsgAppParallel)app).declareParallelBehavior(this);                
			    }
			}
		}
		constructingParallelInstancesEnding();
		
		//Init bridges
		
		int b = bridges.length;
		while (--b>=0) {
			((BridgeConfigImpl)bridges[b]).finish(this);
		}
	}
	
	private void buildGraphForServer(MsgApp app) {

		ServerPipesConfig serverConfig = new ServerPipesConfig(builder.isLarge(), builder.isServerTLS());

		ServerCoordinator serverCoord = new ServerCoordinator( builder.isServerTLS(),
															   (String) builder.bindHost(), builder.bindPort(), 
				                                               serverConfig.maxConnectionBitsOnServer, 
				                                               serverConfig.maxPartialResponsesServer, 
				                                               builder.parallelism(),
				                                               "Server",builder.defaultHostPath());
		
		final int routerCount = builder.parallelism();
		
		final Pipe<NetPayloadSchema>[] encryptedIncomingGroup = Pipe.buildPipes(serverConfig.maxPartialResponsesServer, serverConfig.incomingDataConfig);           
		
		Pipe[] acks = NetGraphBuilder.buildSocketReaderStage(gm, serverCoord, routerCount, serverConfig, encryptedIncomingGroup, -1);
		               
		Pipe[] handshakeIncomingGroup=null;
		Pipe[] planIncomingGroup;
		
		if (builder.isServerTLS()) {
			planIncomingGroup = Pipe.buildPipes(serverConfig.maxPartialResponsesServer, serverConfig.incomingDataConfig);
			handshakeIncomingGroup = NetGraphBuilder.populateGraphWithUnWrapStages(gm, serverCoord, 
					                      serverConfig.serverRequestUnwrapUnits, serverConfig.handshakeDataConfig,
					                      encryptedIncomingGroup, planIncomingGroup, acks, -1);
		} else {
			planIncomingGroup = encryptedIncomingGroup;
		}
		
		//Must call here so the beginning stages of the graph are drawn first when exporting graph.
		app.declareBehavior(this);
		
		buildLastHalfOfGraphForServer(app, serverConfig, serverCoord, routerCount, acks, handshakeIncomingGroup, planIncomingGroup);
	}


	private void buildLastHalfOfGraphForServer(MsgApp app, ServerPipesConfig serverConfig,
			ServerCoordinator serverCoord, final int routerCount, Pipe[] acks, 
			Pipe[] handshakeIncomingGroup,
			Pipe[] planIncomingGroup) {
		////////////////////////
		//create the working modules
		//////////////////////////

		if (app instanceof MsgAppParallel) {
			int p = builder.parallelism();
			
			for (int i = 0; i < p; i++) {
				constructingParallelInstance(i);
				((MsgAppParallel)app).declareParallelBehavior(this);  //this creates all the modules for this parallel instance								
			}	
		} else {
			if (builder.parallelism()>1) {
				throw new UnsupportedOperationException(
						"Remove call to parallelism("+builder.parallelism()+") OR make the application implement GreenAppParallel or something extending it.");
			}
		}

		//////////////////
		//////////////////
		
		HTTP1xRouterStageConfig routerConfig = builder.routerConfig();
				
		ArrayList<Pipe> forPipeCleaner = new ArrayList<Pipe>();
		Pipe<HTTPRequestSchema>[][] fromRouterToModules = new Pipe[routerCount][];	
		int t = routerCount;
		int totalRequestPipes = 0;
		while (--t>=0) {
			//[router/parallel] then [parser/routes] 
			int path = routerConfig.routesCount();

			/////////////////
			///for catch all
			///////////////
			if (path==0) {
				path=1;
			}
			/////////////
			
			fromRouterToModules[t] = new Pipe[path]; 
		    while (--path >= 0) {
		    	
		    	ArrayList<Pipe<HTTPRequestSchema>> requestPipes = builder.buildFromRequestArray(t, path);
		    	
		    	//with a single pipe just pass it one, otherwise use the replicator to fan out from a new single pipe.
		    	int size = requestPipes.size();
		    	totalRequestPipes += size;
		    	
				if (1==size) {
		    		fromRouterToModules[t][path] = requestPipes.get(0);
		    	} else {
		    		//we only create a pipe when we are about to use the replicator
		    		fromRouterToModules[t][path] =  
		    				builder.newHTTPRequestPipe(builder.pcm.getConfig(HTTPRequestSchema.class));		    		
		    		if (0==size) {
		    			logger.info("warning there are routes without any consumers");
		    			//we have no consumer so tie it to pipe cleaner		    		
		    			forPipeCleaner.add(fromRouterToModules[t][path]);
		    		} else {
		    			ReplicatorStage.newInstance(gm, fromRouterToModules[t][path], requestPipes.toArray(new Pipe[requestPipes.size()]));	
		    		}
		    	}
		    }
		    if (0==totalRequestPipes) {
		    	logger.warn("ERROR: includeRoutes or includeAllRoutes must be called on REST listener.");
		    }
		}
		
		if (!forPipeCleaner.isEmpty()) {
			PipeCleanerStage.newInstance(gm, forPipeCleaner.toArray(new Pipe[forPipeCleaner.size()]));
		}
		
		//NOTE: building arrays of pipes grouped by parallel/routers heading out to order supervisor		
		Pipe<ServerResponseSchema>[][] fromModulesToOrderSuper = new Pipe[routerCount][];
		Pipe<ServerResponseSchema>[] errorResponsePipes = new Pipe[routerCount];
		PipeConfig<ServerResponseSchema> errConfig = ServerResponseSchema.instance.newPipeConfig(4, 512);
		int r = routerCount;
		while (--r>=0) {
			errorResponsePipes[r] = new Pipe<ServerResponseSchema>(errConfig);
			fromModulesToOrderSuper[r] = PronghornStage.join(builder.buildToOrderArray(r),errorResponsePipes[r]);			
		}
				
		
		boolean catchAll = builder.routerConfig().routesCount()==0;
		NetGraphBuilder.buildRouters(gm, routerCount, planIncomingGroup, acks, 
				                     fromRouterToModules, errorResponsePipes, routerConfig,
				                     serverCoord, -1, catchAll);

		final GraphManager graphManager = gm; 
		
				
		Pipe<NetPayloadSchema>[] fromOrderedContent = NetGraphBuilder.buildRemainderOfServerStages(graphManager, serverCoord,
				                                            serverConfig, handshakeIncomingGroup, -1);
		
		NetGraphBuilder.buildOrderingSupers(graphManager, serverCoord, routerCount, fromModulesToOrderSuper, fromOrderedContent, -1);
	}
	//////////////////
	//end of server and other behavior
	//////////////////
	
	
	public void setExclusiveTopics(MsgCommandChannel cc, String ... exlusiveTopics) {
		// TODO Auto-generated method stub
		
		throw new UnsupportedOperationException("Not yet implemented");
		
	}


	//Not for general consumption, only used when we need the low level Pipes to connect directly to the pub/sub or other dynamic subsystem.
	public static GraphManager getGraphManager(MsgRuntime runtime) {
		return runtime.gm;
	}
	
	
	public RouteFilter addFileServer(String path) { //adds server to all routes
		final int parallelIndex = (-1 == parallelInstanceUnderActiveConstruction) ? 0 : parallelInstanceUnderActiveConstruction;
                		
        //due to internal implementation we must keep the same number of outputs as inputs.
		Pipe<HTTPRequestSchema>[] inputs = new Pipe[1];
		Pipe<ServerResponseSchema>[] outputs = new Pipe[1];		
		populateHTTPInOut(inputs, outputs, 0, parallelIndex);
			
		File rootPath = buildFilePath(path);
		FileReadModuleStage.newInstance(gm, inputs, outputs, builder.httpSpec, rootPath);
				
		return new StageRouteFilter(inputs[0], builder, parallelIndex);
	}
	
	public RouteFilter addFileServer(String resourceRoot, String resourceDefault) {
		final int parallelIndex = (-1 == parallelInstanceUnderActiveConstruction) ? 0 : parallelInstanceUnderActiveConstruction;
		
		//due to internal implementation we must keep the same number of outputs as inputs.
		Pipe<HTTPRequestSchema>[] inputs = new Pipe[1];
		Pipe<ServerResponseSchema>[] outputs = new Pipe[1];		
		populateHTTPInOut(inputs, outputs, 0, parallelIndex);
				
		FileReadModuleStage.newInstance(gm, inputs, outputs, builder.httpSpec, resourceRoot, resourceDefault);
					
		return new StageRouteFilter(inputs[0], builder, parallelIndex);
				
	}


	private void populateHTTPInOut(Pipe<HTTPRequestSchema>[] inputs, 
			                      Pipe<ServerResponseSchema>[] outputs, 
			                      int idx, int parallelIndex) {
		
		if (null == fileRequestConfig) {
			fileRequestConfig = builder.pcm.getConfig(HTTPRequestSchema.class).grow2x();
		}
		inputs[idx] = builder.newHTTPRequestPipe(fileRequestConfig);
		outputs[idx] = builder.newNetResponsePipe(builder.pcm.getConfig(ServerResponseSchema.class), parallelIndex);

	}
	

    //TODO: needs a lot of re-work.
	private File buildFilePath(String path) {
		// Strip URL space tokens from incoming path strings to avoid issues.
        // TODO: This should be made garbage free.
        // TODO: Is this expected behavior?
        path = path.replaceAll("\\Q%20\\E", " ");

        //TODO: MUST FIND PATH...
        Enumeration<URL> resource;
		try {
			resource = ClassLoader.getSystemResources(path);
		
			while (resource.hasMoreElements()) {
				System.err.println("looking for resoruce: "+path+" and found "+String.valueOf(resource.nextElement()));
			}
		
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        		
        	//	ClassLoader.getSystemResource(path);
        
        
        File rootPath = new File(path);
        
        if (!rootPath.exists()) {
        	//test if this is under development
        	File devPath = new File("./src/main/resources/"+path);
        	if (devPath.exists()) {
        		rootPath = devPath;
        	}
        }
       
        
        if (!rootPath.exists()) {
        	throw new UnsupportedOperationException("Path not found: "+rootPath);
        }
		return rootPath;
	}

    ///////////////////////////
	//end of file server
	///////////////////////////

	
	
    public Builder getBuilder(){
    	if(this.builder==null){    	    
    	    this.builder = (B) new BuilderImpl(gm,args);
    	}
    	return this.builder;
    }
        
    private ListenerFilter registerListenerImpl(final Behavior listener) {
    	
    	////////////
    	//OUTPUT
    	///////////
    	outputPipes = new Pipe<?>[0];
    	//extract pipes used by listener and use cmdChannelUsageChecker to confirm its not re-used
    	ChildClassScanner.visitUsedByClass(listener, gatherPipesVisitor, MsgCommandChannel.class);//populates outputPipes

    	
    	/////////////
    	//INPUT
    	//add green features, count first then create the pipes
    	//NOTE: that each Behavior is inspected and will find Transducers which need inputs as well
    	/////////
    	int pipesCount = addGreenPipesCount(listener, 0);
        Pipe<?>[] inputPipes = new Pipe<?>[pipesCount];
        populateGreenPipes(listener, pipesCount, inputPipes);                

        //////////////////////
        //////////////////////
        
        //this is empty when transducerAutowiring is off
        final ArrayList<ReactiveManagerPipeConsumer> consumers = new ArrayList<ReactiveManagerPipeConsumer>(); 
        
        //extract this into common method to be called in GL and FL
		if (transducerAutowiring) {
			inputPipes = autoWireTransducers(listener, inputPipes, consumers);
		}       
        
        ReactiveListenerStage reactiveListener = builder.createReactiveListener(gm, listener, 
        		                                inputPipes, outputPipes, consumers,
        		                                parallelInstanceUnderActiveConstruction);

        
        if (listener instanceof RestListenerBase) {
			GraphManager.addNota(gm, GraphManager.DOT_RANK_NAME, "ModuleStage", reactiveListener);
			
		}
		
		/////////////////////
		//StartupListener is not driven by any response data and is called when the stage is started up. no pipe needed.
		/////////////////////
		//TimeListener, time rate signals are sent from the stages its self and therefore does not need a pipe to consume.
		/////////////////////
		
		configureStageRate(listener,reactiveListener);
		
		int testId = -1;
		int i = inputPipes.length;
		while (--i>=0) {
			if (inputPipes[i]!=null && Pipe.isForSchema((Pipe<MessageSubscription>)inputPipes[i], MessageSubscription.class)) {
				testId = inputPipes[i].id;
			}
		}
		
		assert(-1==testId || GraphManager.allPipesOfType(gm, MessageSubscription.instance)[subscriptionPipeIdx-1].id==testId) : "GraphManager has returned the pipes out of the expected order";
		
		return reactiveListener;
        
    }

	protected Pipe<?>[] autoWireTransducers(final Behavior listener, Pipe<?>[] inputPipes,
			final ArrayList<ReactiveManagerPipeConsumer> consumers) {
		
		if (inputPipes.length==0) {
			return inputPipes;//no work since no inputs are used.
		}
		
		final Grouper g = new Grouper(inputPipes);
		
		ChildClassScannerVisitor tVisitor = new ChildClassScannerVisitor() {
			@Override
			public boolean visit(Object child, Object topParent) {					
				if (g.additions()==0) {
					//add first value
					Pipe[] pipes = ReactiveListenerStage.operators.createPipes(listener, g);
					consumers.add(new ReactiveManagerPipeConsumer(child, ReactiveListenerStage.operators, pipes));
					g.add(pipes);
				}					
				
				int c = consumers.size();
				while (--c>=0) {
					if (consumers.get(c).obj == child) {
						//do not add this one it is already recorded
						return true;
					}
				}
				
				Pipe[] pipes = ReactiveListenerStage.operators.createPipes(child, g);
				consumers.add(new ReactiveManagerPipeConsumer(child, ReactiveListenerStage.operators, pipes));
				g.add(pipes);		
				
				return true;
			}				
		};
		
		ChildClassScanner.visitUsedByClass(listener, tVisitor, ListenerTransducer.class);
					
		if (g.additions()>0) {
			inputPipes = g.firstArray();
			g.buildReplicators(gm, consumers);
		}
		return inputPipes;
	}

	protected PipeConfigManager buildPipeManager() {
		PipeConfigManager pcm = new PipeConfigManager();
		pcm.addConfig(defaultCommandChannelLength,0,TrafficOrderSchema.class );
		return pcm;
	}
    
}
