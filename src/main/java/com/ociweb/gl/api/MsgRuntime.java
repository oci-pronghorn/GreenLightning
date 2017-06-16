package com.ociweb.gl.api;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.HTTPPayloadReader;
import com.ociweb.gl.impl.PayloadReader;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.stage.ReactiveListenerStage;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.ServerPipesConfig;
import com.ociweb.pronghorn.network.http.HTTP1xRouterStageConfig;
import com.ociweb.pronghorn.network.module.AbstractPayloadResponseStage;
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
import com.ociweb.pronghorn.stage.monitor.MonitorConsoleStage;
import com.ociweb.pronghorn.stage.route.ReplicatorStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;

public class MsgRuntime<B extends BuilderImpl, L extends ListenerFilter> {
 
    private static final Logger logger = LoggerFactory.getLogger(MsgRuntime.class);

    
    protected static final int nsPerMS = 1_000_000;
	public B builder;
    protected final GraphManager gm;

    protected StageScheduler scheduler;
    
    protected final int defaultCommandChannelLength = 16;
    protected final int defaultCommandChannelMaxPayload = 256; //largest i2c request or pub sub payload
    protected final int defaultCommandChannelHTTPResponseMaxPayload = 1<<12;
    
    
    protected final PipeConfig<NetResponseSchema> responseNetConfig = new PipeConfig<NetResponseSchema>(NetResponseSchema.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload);   
    protected final PipeConfig<ClientHTTPRequestSchema> requestNetConfig = new PipeConfig<ClientHTTPRequestSchema>(ClientHTTPRequestSchema.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload);
    protected final PipeConfig<ServerResponseSchema> serverResponseNetConfig = new PipeConfig<ServerResponseSchema>(ServerResponseSchema.instance, 1<<12, defaultCommandChannelHTTPResponseMaxPayload);
    protected final PipeConfig<MessageSubscription> messageSubscriptionConfig = new PipeConfig<MessageSubscription>(MessageSubscription.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload);
    protected final PipeConfig<ServerResponseSchema> fileResponseConfig = new PipeConfig<ServerResponseSchema>(ServerResponseSchema.instance, 1<<12, defaultCommandChannelHTTPResponseMaxPayload);
    
    protected int netResponsePipeIdx = 0;//this implementation is dependent upon graphManager returning the pipes in the order created!
    protected int subscriptionPipeIdx = 0; //this implementation is dependent upon graphManager returning the pipes in the order created!
    protected final IntHashTable subscriptionPipeLookup = new IntHashTable(10);//NOTE: this is a maximum of 1024 listeners
    protected final IntHashTable netPipeLookup = new IntHashTable(10);//NOTE: this is a maximum of 1024 listeners
    
	protected int parallelInstanceUnderActiveConstruction = -1;
	protected Pipe<?>[] outputPipes = null;
	protected Pipe<HTTPRequestSchema>[] httpRequestPipes = null;
    protected CommandChannelVisitor gatherPipesVisitor = new CommandChannelVisitor() {
    	
		@Override
		public void visit(GreenCommandChannel cmdChnl) {
			outputPipes = PronghornStage.join(outputPipes, cmdChnl.getOutputPipes());			
		}

    };
    
    
	public MsgRuntime() {
		 gm = new GraphManager();
	}
	
	
    public final L addRestListener(RestListener listener, int ... routes) {
    	return (L) registerListenerImpl(listener, routes);
    }
    
    
    public final L addStartupListener(StartupListener listener) {
        return (L) registerListenerImpl(listener);
    }
	    
    public final L addTimeListener(TimeListener listener) {
        return (L) registerListenerImpl(listener);
    }
    
    public final L addPubSubListener(PubSubListener listener) {
        return (L) registerListenerImpl(listener);
    }

    public final <E extends Enum<E>> L addStateChangeListener(StateChangeListener<E> listener) {
        return (L) registerListenerImpl(listener);
    }
    
    public final L addListener(Object listener) {
        return (L) registerListenerImpl(listener);
    }
    
    public L registerListener(Object listener) {
    	return (L) registerListenerImpl(listener);
    }

	protected void extractPipeData(Object listener, int... optionalInts) {
		outputPipes = new Pipe<?>[0];
		httpRequestPipes = (Pipe<HTTPRequestSchema>[]) new Pipe<?>[0];     	
    	
		if (optionalInts.length>0 && listener instanceof RestListener) {
			httpRequestPipes = PronghornStage.join(httpRequestPipes, new ListenerConfig(builder, optionalInts, parallelInstanceUnderActiveConstruction).getHTTPRequestPipes());
		}
    	
		//extract pipes used by listener
    	visitCommandChannelsUsedByListener(listener, gatherPipesVisitor);//populates  httpRequestPipes and outputPipes
	}
	
    protected void visitCommandChannelsUsedByListener(Object listener, CommandChannelVisitor visitor) {

        Class<? extends Object> c = listener.getClass();
        Field[] fields = c.getDeclaredFields();
        int f = fields.length;
        while (--f >= 0) {
            try {
                fields[f].setAccessible(true);   
                Object obj = fields[f].get(listener);
                if (obj instanceof GreenCommandChannel) {
                    GreenCommandChannel cmdChnl = (GreenCommandChannel)obj;                 
                    
                    assert(channelNotPreviouslyUsed(cmdChnl)) : "A CommandChannel instance can only be used exclusivly by one object or lambda. Double check where CommandChannels are passed in.";
                    GreenCommandChannel.setListener(cmdChnl, listener);
                    visitor.visit(cmdChnl);
                                        
                }
                
            } catch (Throwable e) {
                logger.debug("unable to find CommandChannel",e);
            }
        }

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
    
    static String reportChoice(final String longName, final String shortName, final String value) {
        System.out.print(longName);
        System.out.print(" ");
        System.out.print(shortName);
        System.out.print(" ");
        System.out.println(value);
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
    	if (null == scheduler || null == builder) {
    		System.exit(0);
    		return;
    	}
        //clean shutdown providing time for the pipe to empty
        scheduler.shutdown();
        scheduler.awaitTermination(3, TimeUnit.SECONDS); //timeout error if this does not exit cleanly withing this time.
        //all the software has now stopped so now shutdown the hardware.
        builder.shutdown();
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
    private static IntHashTable cmdChannelUsageChecker;
    static {
        assert(setupForChannelAssertCheck());
    }    
    private static boolean setupForChannelAssertCheck() {
        cmdChannelUsageChecker = new IntHashTable(9);
        return true;
    }
    protected boolean channelNotPreviouslyUsed(GreenCommandChannel cmdChnl) {
        int hash = cmdChnl.hashCode();
        
        if (IntHashTable.hasItem(cmdChannelUsageChecker, hash)) {
                //this was already assigned somewhere so this is  an error
                logger.error("A CommandChannel instance can only be used exclusivly by one object or lambda. Double check where CommandChannels are passed in.", new UnsupportedOperationException());
                return false;
        } 
        //keep so this is detected later if use;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
        
        
        IntHashTable.setItem(cmdChannelUsageChecker, hash, 42);
        return true;
    }
    ///////////
    ///////////
    

	protected int addGreenPipesCount(Object listener, int pipesCount) {
		if (this.builder.isListeningToHTTPResponse(listener)) {
        	pipesCount++; //these are calls to URL responses        	
        }
        
        if (this.builder.isListeningToSubscription(listener)) {
        	pipesCount++;
        }
        
        if (this.builder.isListeningHTTPRequest(listener)) {
        	pipesCount += httpRequestPipes.length; //NOTE: these are all accumulated from the command chanel as for routes we listen to (change in the future)
        }
		return pipesCount;
	}

	protected void populateGreenPipes(Object listener, int pipesCount, Pipe<?>[] inputPipes) {
		if (this.builder.isListeningToHTTPResponse(listener)) {        	
        	Pipe<NetResponseSchema> netResponsePipe1 = new Pipe<NetResponseSchema>(responseNetConfig) {
				@SuppressWarnings("unchecked")
				@Override
				protected DataInputBlobReader<NetResponseSchema> createNewBlobReader() {
					return new HTTPPayloadReader<NetResponseSchema>(this);
				}
			};
			Pipe<NetResponseSchema> netResponsePipe = netResponsePipe1;        	
            int pipeIdx = netResponsePipeIdx++;
            inputPipes[--pipesCount] = netResponsePipe;
            boolean addedItem = IntHashTable.setItem(netPipeLookup, System.identityHashCode(listener), pipeIdx);
            if (!addedItem) {
            	throw new RuntimeException("Could not find unique identityHashCode for "+listener.getClass().getCanonicalName());
            }
            
        }
        
        if (this.builder.isListeningToSubscription(listener)) {
            Pipe<MessageSubscription> subscriptionPipe = new Pipe<MessageSubscription>(messageSubscriptionConfig) {
				@SuppressWarnings("unchecked")
				@Override
				protected DataInputBlobReader<MessageSubscription> createNewBlobReader() {
					return new MessageReader(this);
				}
			};
			
            inputPipes[--pipesCount]=(subscriptionPipe);
            //store this value for lookup later
            //logger.debug("adding hash listener {} to pipe {} ",System.identityHashCode(listener), subPipeIdx);
            boolean addedItem = IntHashTable.setItem(subscriptionPipeLookup, System.identityHashCode(listener), subscriptionPipeIdx++);
            if (!addedItem) {
            	throw new RuntimeException("Could not find unique identityHashCode for "+listener.getClass().getCanonicalName());
            }
        }

        
        //if we push to this 1 pipe all the requests...
        //JoinStage to take N inputs and produce 1 output.
        //we use splitter for single pipe to 2 databases
        //we use different group for parallel processing
        //for mutiple we must send them all to the reactor.
        
        
        if (this.builder.isListeningHTTPRequest(listener) ) {
        	
        	int i = httpRequestPipes.length;
        	while (--i >= 0) {        		
        		inputPipes[--pipesCount] = httpRequestPipes[i];                		
        	}
        }
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
			
			int parallelism = builder.parallelism();
			//since server was not started and did not create each parallel instance this will need to be done here
   
			for(int i = 0;i<parallelism;i++) { //do not use this loop, we will loop inside server setup..
		    	constructingParallelInstance(i);
		    	app.declareParallelBehavior(this);                
		    }
		}
		constructingParallelInstancesEnding();
	}
	
	private void buildGraphForServer(MsgApp app) {
		
		
		ServerPipesConfig serverConfig = new ServerPipesConfig(builder.isLarge(), builder.isTLS());

		ServerCoordinator serverCoord = new ServerCoordinator( builder.isTLS(),
															   (String) builder.bindHost(), builder.bindPort(), 
				                                               serverConfig.maxConnectionBitsOnServer, 
				                                               serverConfig.maxPartialResponsesServer, 
				                                               builder.parallelism());
		
		final int routerCount = builder.parallelism();
		
		final Pipe<NetPayloadSchema>[] encryptedIncomingGroup = Pipe.buildPipes(serverConfig.maxPartialResponsesServer, serverConfig.incomingDataConfig);           
		
		Pipe[] acks = NetGraphBuilder.buildSocketReaderStage(gm, serverCoord, routerCount, serverConfig, encryptedIncomingGroup, -1);
		               
		Pipe[] handshakeIncomingGroup=null;
		Pipe[] planIncomingGroup;
		
		if (builder.isTLS()) {
			planIncomingGroup = Pipe.buildPipes(serverConfig.maxPartialResponsesServer, serverConfig.incomingDataConfig);
			handshakeIncomingGroup = NetGraphBuilder.populateGraphWithUnWrapStages(gm, serverCoord, serverConfig.serverRequestUnwrapUnits, serverConfig.handshakeDataConfig,
					                      encryptedIncomingGroup, planIncomingGroup, acks, -1);
		} else {
			planIncomingGroup = encryptedIncomingGroup;
		}
		
		//Must call here so the beginning stages of the graph are drawn first when exporting graph.
		app.declareBehavior(this);
		
		buildLastHalfOfGraphForServer(app, serverConfig, serverCoord, routerCount, acks, handshakeIncomingGroup, planIncomingGroup);
	}


	private void buildLastHalfOfGraphForServer(MsgApp app, ServerPipesConfig serverConfig,
			ServerCoordinator serverCoord, final int routerCount, Pipe[] acks, Pipe[] handshakeIncomingGroup,
			Pipe[] planIncomingGroup) {
		////////////////////////
		//create the working modules
		//////////////////////////

		int p = builder.parallelism();
		
		for (int i = 0; i < p; i++) {
			constructingParallelInstance(i);
			app.declareParallelBehavior(this);  //this creates all the modules for this parallel instance								
		}	

		//////////////////
		//////////////////
		
		HTTP1xRouterStageConfig routerConfig = builder.routerConfig();
				
		ArrayList<Pipe> forPipeCleaner = new ArrayList<Pipe>();
		Pipe<HTTPRequestSchema>[][] fromRouterToModules = new Pipe[routerCount][];	
		int t = routerCount;
		while (--t>=0) {
			//[router/parallel] then [parser/routes] 
			int path = routerConfig.routesCount();
			fromRouterToModules[t] = new Pipe[path]; 
			
		    while (--path >= 0) {
		    	
		    	Pipe<HTTPRequestSchema>[] array = builder.buildFromRequestArray(t, path);
		    	//with a single pipe just pass it one, otherwise use the replicator to fan out from a new single pipe.
		    	if (1==array.length) {
		    		fromRouterToModules[t][path] = array[0];
		    	} else {	
		    		fromRouterToModules[t][path] = builder.newHTTPRequestPipe(builder.restPipeConfig);		    		
		    		if (0==array.length) {
		    			//we have no consumer so tie it to pipe cleaner		    		
		    			forPipeCleaner.add(fromRouterToModules[t][path]);
		    		} else {
		    			ReplicatorStage.instance(gm, fromRouterToModules[t][path], array);	
		    		}
		    	}
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
		
		NetGraphBuilder.buildRouters(gm, routerCount, planIncomingGroup, acks, fromRouterToModules, errorResponsePipes, routerConfig, serverCoord, -1);

		final GraphManager graphManager = gm; 
		
				
		Pipe<NetPayloadSchema>[] fromOrderedContent = NetGraphBuilder.buildRemainderOfServerStages(graphManager, serverCoord,
				                                            serverConfig, handshakeIncomingGroup, -1);
		
		NetGraphBuilder.buildOrderingSupers(graphManager, serverCoord, routerCount, fromModulesToOrderSuper, fromOrderedContent, -1);
	}
	//////////////////
	//end of server and other behavior
	//////////////////
	
	
	public void setExclusiveTopics(GreenCommandChannel cc, String ... exlusiveTopics) {
		// TODO Auto-generated method stub
		
		throw new UnsupportedOperationException("Not yet implemented");
		
	}


	//Not for general consumption, only used when we need the low level Pipes to connect directly to the pub/sub or other dynamic subsystem.
	public static GraphManager getGraphManager(MsgRuntime runtime) {
		return runtime.gm;
	}
	

	
	
	
	////////////////
	//add file server
	////////////////
	
	public void addFileServer(String path, int ... routes) {

        File rootPath = buildFilePath(path);
        
        
		//due to internal implementation we must keep the same number of outputs as inputs.
		int r = routes.length;
		int p = computeParaMulti();
		
		int count = r*p;
		
		Pipe<HTTPRequestSchema>[] inputs = new Pipe[count];
		Pipe<ServerResponseSchema>[] outputs = new Pipe[count];
		populatePipeArrays(r, p, inputs, outputs, routes);		
		
		FileReadModuleStage.newInstance(gm, inputs, outputs, builder.httpSpec, rootPath);
				
	}
	
	public void addFileServer(String resourceRoot, String resourceDefault, int ... routes) {
		
		//due to internal implementation we must keep the same number of outputs as inputs.
		int r = routes.length;
		int p = computeParaMulti();
		
		int count = r*p;
		
		Pipe<HTTPRequestSchema>[] inputs = new Pipe[count];
		Pipe<ServerResponseSchema>[] outputs = new Pipe[count];
		populatePipeArrays(r, p, inputs, outputs, routes);		
		
		FileReadModuleStage.newInstance(gm, inputs, outputs, builder.httpSpec, resourceRoot, resourceDefault);
				
	}


	private int computeParaMulti() {
		if (-1==parallelInstanceUnderActiveConstruction ) {
			return builder.parallelism();
		} else {
			return 1;
		}
	}


	private void populatePipeArrays(int r, int p, Pipe<HTTPRequestSchema>[] inputs,	Pipe<ServerResponseSchema>[] outputs, int[] routes) {
		int idx = inputs.length;
		assert(inputs.length==outputs.length);
		
		while (--r>=0) {
			int x = p;
			while (--x >= 0) {
				idx--;
				int parallelIndex = (-1 == parallelInstanceUnderActiveConstruction) ? x : parallelInstanceUnderActiveConstruction;
				inputs[idx] = builder.createHTTPRequestPipe(builder.restPipeConfig.grow2x(), routes[r], parallelIndex);
				outputs[idx] = builder.newNetResponsePipe(fileResponseConfig, parallelIndex);
			}
		}
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
    	    this.builder = (B) new BuilderImpl(gm);
    	}
    	return this.builder;
    }
    
    
    public GreenCommandChannel<B> newCommandChannel(int features) { 
      
    	PipeConfigManager pcm = new PipeConfigManager(4, defaultCommandChannelLength, defaultCommandChannelMaxPayload);

    	pcm.addConfig(requestNetConfig);
    	pcm.addConfig(defaultCommandChannelLength,0,TrafficOrderSchema.class);
    	pcm.addConfig(serverResponseNetConfig);
    	    	
    	return this.builder.newCommandChannel(
				features,
				parallelInstanceUnderActiveConstruction,
				pcm
		  );    	
    }

    public GreenCommandChannel<B> newCommandChannel(int features, int customChannelLength) { 
       
    	PipeConfigManager pcm = new PipeConfigManager(4, defaultCommandChannelLength, defaultCommandChannelMaxPayload);
    	
    	pcm.addConfig(customChannelLength,defaultCommandChannelMaxPayload,MessagePubSub.class);
    	pcm.addConfig(requestNetConfig);
    	pcm.addConfig(customChannelLength,0,TrafficOrderSchema.class);
    	pcm.addConfig(customChannelLength,defaultCommandChannelHTTPResponseMaxPayload,ServerResponseSchema.class);
    	   	
        return this.builder.newCommandChannel(
				features,
				parallelInstanceUnderActiveConstruction,
				pcm
		  );        
    }

    
    private ListenerFilter registerListenerImpl(Object listener, int ... optionalInts) {
                
    	extractPipeData(listener, optionalInts);
		
		
    	/////////
    	//pre-count how many pipes will be needed so the array can be built to the right size
    	/////////
    	int pipesCount = 0;

        pipesCount = addGreenPipesCount(listener, pipesCount);
                
        Pipe<?>[] inputPipes = new Pipe<?>[pipesCount];
    	 	
    	
    	///////
        //Populate the inputPipes array with the required pipes
    	///////      

        populateGreenPipes(listener, pipesCount, inputPipes);                

		
        //////////////////////
        //////////////////////
        
        ReactiveListenerStage reactiveListener = builder.createReactiveListener(gm, listener, inputPipes, outputPipes);
		
        if (listener instanceof RestListener) {
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
			if (inputPipes[i]!=null && Pipe.isForSchema(inputPipes[i], MessageSubscription.instance)) {
				testId = inputPipes[i].id;
			}
		}
		
		assert(-1==testId || GraphManager.allPipesOfType(gm, MessageSubscription.instance)[subscriptionPipeIdx-1].id==testId) : "GraphManager has returned the pipes out of the expected order";
		
		return reactiveListener;
        
    }

  
    public static MsgRuntime run(MsgApp app) {
	    MsgRuntime runtime = new MsgRuntime(); 
        try {
		    app.declareConfiguration(runtime.getBuilder());
		         
		    GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());
		    
		    runtime.declareBehavior(app);
		            
		    runtime.builder.buildStages(runtime.subscriptionPipeLookup, runtime.netPipeLookup, runtime.gm);
			
			   //find all the instances of CommandChannel stage to startup first, note they are also unscheduled.
			               
			   runtime.logStageScheduleRates();
			      
			   if (runtime.builder.isTelemetryEnabled()) {	
				   runtime.gm.enableTelemetry("127.0.0.1", 8098);
			   }
			
		    
		} catch (Throwable t) {
		    t.printStackTrace();
		    System.exit(-1);
		}
		
		runtime.scheduler = runtime.builder.createScheduler(runtime);            
		runtime.scheduler.startup();
		
		return runtime;
    }
	
	public static MsgRuntime test(MsgApp app) {
	    MsgRuntime runtime = new MsgRuntime(); 
        try {
		    app.declareConfiguration(runtime.getBuilder());
		         
		    GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.builder.getDefaultSleepRateNS());
		    
		    runtime.declareBehavior(app);
		            
		    runtime.builder.buildStages(runtime.subscriptionPipeLookup, runtime.netPipeLookup, runtime.gm);
			
			   //find all the instances of CommandChannel stage to startup first, note they are also unscheduled.
			               
			   runtime.logStageScheduleRates();
			      
			   if ( runtime.builder.isTelemetryEnabled()) {	   
				   runtime.gm.enableTelemetry("127.0.0.1", 8098);
			   }
			
		    
		} catch (Throwable t) {
		    t.printStackTrace();
		    System.exit(-1);
		}
		
		runtime.scheduler = new NonThreadScheduler(runtime.builder.gm);            
		runtime.scheduler.startup();
		
		return runtime;
    }

	

    
}
