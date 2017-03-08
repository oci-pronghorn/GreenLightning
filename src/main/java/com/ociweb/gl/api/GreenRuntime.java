package com.ociweb.gl.api;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ociweb.gl.impl.BuilderImpl;
import com.ociweb.gl.impl.schema.MessagePubSub;
import com.ociweb.gl.impl.schema.MessageSubscription;
import com.ociweb.gl.impl.schema.TrafficOrderSchema;
import com.ociweb.gl.impl.stage.ReactiveListenerStage;
import com.ociweb.pronghorn.network.HTTP1xRouterStage;
import com.ociweb.pronghorn.network.HTTP1xRouterStageConfig;
import com.ociweb.pronghorn.network.HTTPServerConfig;
import com.ociweb.pronghorn.network.ModuleConfig;
import com.ociweb.pronghorn.network.NetGraphBuilder;
import com.ociweb.pronghorn.network.OrderSupervisorStage;
import com.ociweb.pronghorn.network.ServerCoordinator;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import com.ociweb.pronghorn.network.config.HTTPHeaderKeyDefaults;
import com.ociweb.pronghorn.network.config.HTTPRevisionDefaults;
import com.ociweb.pronghorn.network.config.HTTPSpecification;
import com.ociweb.pronghorn.network.config.HTTPVerbDefaults;
import com.ociweb.pronghorn.network.module.FileReadModuleStage;
import com.ociweb.pronghorn.network.schema.ClientHTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.HTTPRequestSchema;
import com.ociweb.pronghorn.network.schema.NetPayloadSchema;
import com.ociweb.pronghorn.network.schema.NetResponseSchema;
import com.ociweb.pronghorn.network.schema.ServerResponseSchema;
import com.ociweb.pronghorn.pipe.DataInputBlobReader;
import com.ociweb.pronghorn.pipe.Pipe;
import com.ociweb.pronghorn.pipe.PipeConfig;
import com.ociweb.pronghorn.pipe.util.hash.IntHashTable;
import com.ociweb.pronghorn.stage.PronghornStage;
import com.ociweb.pronghorn.stage.monitor.MonitorConsoleStage;
import com.ociweb.pronghorn.stage.route.ReplicatorStage;
import com.ociweb.pronghorn.stage.scheduling.GraphManager;
import com.ociweb.pronghorn.stage.scheduling.NonThreadScheduler;
import com.ociweb.pronghorn.stage.scheduling.StageScheduler;
import com.ociweb.pronghorn.stage.test.PipeCleanerStage;

public class GreenRuntime {
 
    private static final int nsPerMS = 1_000_000;

    /*
     * Caution: in order to make good use of ProGuard we need to make an effort to avoid using static so 
     * dependencies can be traced and kept in the jar.
     *  
     */
    private static final Logger logger = LoggerFactory.getLogger(GreenRuntime.class);
    
    protected BuilderImpl builder;
    
    private StageScheduler scheduler;
    private final GraphManager gm;
  
    private final int defaultCommandChannelLength = 16;
    private final int defaultCommandChannelMaxPayload = 256; //largest i2c request or pub sub payload
    private final int defaultCommandChannelHTTPResponseMaxPayload = 1<<12;
    
    

    ////////////
    ///Pipes for subscribing to and sending messages this is MQTT, StateChanges and many others
    private final PipeConfig<MessagePubSub> messagePubSubConfig = new PipeConfig<MessagePubSub>(MessagePubSub.instance, defaultCommandChannelLength,defaultCommandChannelMaxPayload); 
   
    ////////////
    //Each of the above pipes are paired with TrafficOrder pipe to group commands togetehr in atomic groups and to enforce order across the pipes.
    private final PipeConfig<TrafficOrderSchema> goPipeConfig = new PipeConfig<TrafficOrderSchema>(TrafficOrderSchema.instance, defaultCommandChannelLength); 

    //////////
    //Pipes containing response data from HTTP requests.
    private final PipeConfig<NetResponseSchema> responseNetConfig = new PipeConfig<NetResponseSchema>(NetResponseSchema.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload);   
    
    //////////
    //Pipes containing HTTP requests.
    private final PipeConfig<ClientHTTPRequestSchema> requestNetConfig = new PipeConfig<ClientHTTPRequestSchema>(ClientHTTPRequestSchema.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload);
    
    //////////
    //Pipes containing HTTP responses
    private final PipeConfig<ServerResponseSchema> serverResponseNetConfig = new PipeConfig<ServerResponseSchema>(ServerResponseSchema.instance, 1<<12, defaultCommandChannelHTTPResponseMaxPayload);
    
    
    /////////
    //Pipes for receiving messages, this includes MQTT, State and many others
    private final PipeConfig<MessageSubscription> messageSubscriptionConfig = new PipeConfig<MessageSubscription>(MessageSubscription.instance, defaultCommandChannelLength, defaultCommandChannelMaxPayload);
            
    private final PipeConfig<ServerResponseSchema> fileResponseConfig = new PipeConfig<ServerResponseSchema>(ServerResponseSchema.instance, 1<<12, defaultCommandChannelHTTPResponseMaxPayload);
    
    
    private int netResponsePipeIdx = 0;//this implementation is dependent upon graphManager returning the pipes in the order created!
    private int subscriptionPipeIdx = 0; //this implementation is dependent upon graphManager returning the pipes in the order created!
    
    
    private int defaultSleepRateNS = 1_200;//10_000;   //we will only check for new work 100 times per second to keep CPU usage low.

    
    private final IntHashTable subscriptionPipeLookup = new IntHashTable(10);//NOTE: this is a maximum of 1024 listeners
    private final IntHashTable netPipeLookup = new IntHashTable(10);//NOTE: this is a maximum of 1024 listeners
    
	private int parallelInstanceUnderActiveConstruction = -1;
	
	private Pipe<?>[] outputPipes = null;
	private Pipe<HTTPRequestSchema>[] httpRequestPipes = null;
	


    CommandChannelVisitor gatherPipesVisitor = new CommandChannelVisitor() {
    	
		@Override
		public void visit(CommandChannel cmdChnl) {			
			outputPipes = PronghornStage.join(outputPipes, cmdChnl.getOutputPipes());
			
		}

		@Override
		public void visit(ListenerConfig cnfg) {
			Pipe<HTTPRequestSchema>[] httpRequestPipesInCC = cnfg.getHTTPRequestPipes();
			httpRequestPipes = PronghornStage.join(httpRequestPipes, httpRequestPipesInCC);
		}
		
    };
   
    
    
    private void resetGatherPipesVisitor() {
    	outputPipes = new Pipe<?>[0];
    	httpRequestPipes = (Pipe<HTTPRequestSchema>[]) new Pipe<?>[0];
    }
    
	
    public GreenRuntime() {
        gm = new GraphManager();
     
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
    
    public BuilderImpl getHardware(){
    	if(this.builder==null){    	    
    	    this.builder = new BuilderImpl(gm);
    	}
    	return this.builder;
    }
    
    
    
    public CommandChannel newCommandChannel(int features) { 
      
    	return buildCommandChannel(
									 features,
                                       messagePubSubConfig,
                                       requestNetConfig,
                                       (goPipeConfig),
                                       serverResponseNetConfig
		                             );    	
    }

    public CommandChannel newCommandChannel(int features, int customChannelLength) { 
       
        return buildCommandChannel(
										features,
                                       (new PipeConfig<MessagePubSub>(MessagePubSub.instance, customChannelLength,defaultCommandChannelMaxPayload)),
                                       requestNetConfig,
                                       (new PipeConfig<TrafficOrderSchema>(TrafficOrderSchema.instance, customChannelLength)),
                                       new PipeConfig<ServerResponseSchema>(ServerResponseSchema.instance, customChannelLength, defaultCommandChannelHTTPResponseMaxPayload)
        		                	);        
    }
    
    
    private CommandChannel buildCommandChannel(
    		 										int features,
										    		PipeConfig<MessagePubSub> pubSubConfig,
										            PipeConfig<ClientHTTPRequestSchema> netRequestConfig,
										            PipeConfig<TrafficOrderSchema> orderPipe,
										            PipeConfig<ServerResponseSchema> netResponseconfig
										    	) {
    	
    	
    	return this.builder.newCommandChannel(
    			features,
    			parallelInstanceUnderActiveConstruction,
                messagePubSubConfig,
                requestNetConfig,
                (goPipeConfig),
                serverResponseNetConfig
              ); 
    	
    	
    }
    

    public ListenerFilter addRestListener(RestListener listener) {
    	return registerListener(listener);
    }
    
    public ListenerFilter addStartupListener(StartupListener listener) {
        return registerListener(listener);
    }
    
    public ListenerFilter addTimeListener(TimeListener listener) {
        return registerListener(listener);
    }
    
    public ListenerFilter addPubSubListener(PubSubListener listener) {
        return registerListener(listener);
    }

    public <E extends Enum<E>> ListenerFilter addStateChangeListener(StateChangeListener<E> listener) {
        return registerListener(listener);
    }
    
    public ListenerFilter addListener(Object listener) {
        return registerListener(listener);
    }
    
    public ListenerFilter registerListener(Object listener) {
                
    	resetGatherPipesVisitor();        
		visitCommandChannelsUsedByListener(listener, gatherPipesVisitor);//populates  httpRequestPipes and outputPipes

		
		
    	/////////
    	//pre-count how many pipes will be needed so the array can be built to the right size
    	/////////
    	int pipesCount = 0;

        if (this.builder.isListeningToHTTPResponse(listener)) {
        	pipesCount++; //these are calls to URL responses        	
        }
        
        if (this.builder.isListeningToSubscription(listener)) {
        	pipesCount++;
        }
        
        if (this.builder.isListeningHTTPRequest(listener)) {
        	pipesCount += httpRequestPipes.length; //NOTE: these are all accumulated from the command chanel as for routes we listen to (change in the future)
        }
                
        Pipe<?>[] inputPipes = new Pipe<?>[pipesCount];
    	 	
    	
    	///////
        //Populate the inputPipes array with the required pipes
    	///////      

        if (this.builder.isListeningToHTTPResponse(listener)) {        	
        	Pipe<NetResponseSchema> netResponsePipe = createNetResponsePipe();        	
            int pipeIdx = netResponsePipeIdx++;
            inputPipes[--pipesCount] = netResponsePipe;
            boolean addedItem = IntHashTable.setItem(netPipeLookup, System.identityHashCode(listener), pipeIdx);
            if (!addedItem) {
            	throw new RuntimeException("Could not find unique identityHashCode for "+listener.getClass().getCanonicalName());
            }
            
        }
        
        int subPipeIdx = -1;
        int testId = -1;
        
        if (this.builder.isListeningToSubscription(listener)) {
            Pipe<MessageSubscription> subscriptionPipe = createSubscriptionPipe();
            
            subPipeIdx = subscriptionPipeIdx++;
            testId = subscriptionPipe.id;
            inputPipes[--pipesCount]=(subscriptionPipe);
            //store this value for lookup later
            //logger.debug("adding hash listener {} to pipe {} ",System.identityHashCode(listener), subPipeIdx);
            boolean addedItem = IntHashTable.setItem(subscriptionPipeLookup, System.identityHashCode(listener), subPipeIdx);
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
        
        assert(-1==testId || GraphManager.allPipesOfType(gm, MessageSubscription.instance)[subPipeIdx].id==testId) : "GraphManager has returned the pipes out of the expected order";
        
        return reactiveListener;
        
    }


	private Pipe<MessageSubscription> createSubscriptionPipe() {
		Pipe<MessageSubscription> subscriptionPipe = new Pipe<MessageSubscription>(messageSubscriptionConfig) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<MessageSubscription> createNewBlobReader() {
				return new PayloadReader(this);
			}
		};
		return subscriptionPipe;
	}


	private Pipe<NetResponseSchema> createNetResponsePipe() {
		Pipe<NetResponseSchema> netResponsePipe = new Pipe<NetResponseSchema>(responseNetConfig) {
			@SuppressWarnings("unchecked")
			@Override
			protected DataInputBlobReader<NetResponseSchema> createNewBlobReader() {
				return new PayloadReader(this);
			}
		};
		return netResponsePipe;
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
    private boolean channelNotPreviouslyUsed(CommandChannel cmdChnl) {
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


    private void visitCommandChannelsUsedByListener(Object listener, CommandChannelVisitor visitor) {

        Class<? extends Object> c = listener.getClass();
        Field[] fields = c.getDeclaredFields();
        int f = fields.length;
        while (--f >= 0) {
            try {
                fields[f].setAccessible(true);                
                if (CommandChannel.class == fields[f].getType()) {
                    CommandChannel cmdChnl = (CommandChannel)fields[f].get(listener);                 
                    
                    assert(channelNotPreviouslyUsed(cmdChnl)) : "A CommandChannel instance can only be used exclusivly by one object or lambda. Double check where CommandChannels are passed in.";
                    cmdChnl.setListener(listener);
                    visitor.visit(cmdChnl);
                                        
                }
                
                if (ListenerConfig.class == fields[f].getType()) {
                	ListenerConfig cnfg = (ListenerConfig)fields[f].get(listener);                 
                    
                    visitor.visit(cnfg);
                                        
                }
                
            } catch (Throwable e) {
                logger.debug("unable to find CommandChannel",e);
            }
        }

    }


    protected void configureStageRate(Object listener, ReactiveListenerStage stage) {
        //if we have a time event turn it on.
        long rate = builder.getTriggerRate();
        if (rate>0 && listener instanceof TimeListener) {
            stage.setTimeEventSchedule(rate, builder.getTriggerStart());
            //Since we are using the time schedule we must set the stage to be faster
            long customRate =   (rate*nsPerMS)/NonThreadScheduler.granularityMultiplier;// in ns and guanularityXfaster than clock trigger
            long appliedRate = Math.min(customRate,defaultSleepRateNS);
            GraphManager.addNota(gm, GraphManager.SCHEDULE_RATE, appliedRate, stage);
        }
    }

        
    private void finalGraphBuild() {

       builder.buildStages(subscriptionPipeLookup,
    		   				netPipeLookup,
                            GraphManager.allPipesOfType(gm, MessageSubscription.instance),
                            GraphManager.allPipesOfType(gm, NetResponseSchema.instance),
                            
                            GraphManager.allPipesOfType(gm, TrafficOrderSchema.instance),                            

                            GraphManager.allPipesOfType(gm, MessagePubSub.instance),
                            GraphManager.allPipesOfType(gm, ClientHTTPRequestSchema.instance)
               );
    
       
       //find all the instances of CommandChannel stage to startup first, note they are also unscheduled.
                   
       logStageScheduleRates();
          
       MonitorConsoleStage.attach(gm);//documents what was buit.
       
       
       scheduler = builder.createScheduler(this);       

    }


    private void logStageScheduleRates() {
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

    public StageScheduler getScheduler() {
        return scheduler;
    }    

    public void shutdownRuntime() {
        //clean shutdown providing time for the pipe to empty
        scheduler.shutdown();
        scheduler.awaitTermination(10, TimeUnit.SECONDS); //timeout error if this does not exit cleanly withing this time.
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

    
	public static GreenRuntime run(GreenApp app) {
	    GreenRuntime runtime = new GreenRuntime(); 
        return run(app, runtime);
    }

    private static GreenRuntime run(GreenApp app, GreenRuntime runtime) {
        try {
            app.declareConfiguration(runtime.getHardware());
            
            //debug the URL trie
            //runtime.getHardware().routerConfig().debugURLMap();
            
            establishDefaultRate(runtime);
            
            if (runtime.builder.isUseNetServer()) {

	            buildGraphForServer(app, runtime);

            } else {
            	
            	app.declareBehavior(runtime);
            	
            	int parallelism = runtime.getHardware().parallelism();
            	//since server was not started and did not create each parallel instance this will need to be done here
    
				for(int i = 0;i<parallelism;i++) { //do not use this loop, we will loop inside server setup..
                	runtime.constructingParallelInstance(i);
                	app.declareParallelBehavior(runtime);                
                }
            }
            runtime.constructingParallelInstancesEnding();                    

            
            
            runtime.finalGraphBuild();
            
           
            
            runtime.scheduler.startup();
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(-1);
        }
        return runtime;
    }


	private static void buildGraphForServer(GreenApp app, GreenRuntime runtime) {
		
		
		HTTPServerConfig serverConfig = new HTTPServerConfig(runtime.builder.isLarge(), runtime.builder.isTLS());

		ServerCoordinator serverCoord = new ServerCoordinator((String) runtime.builder.bindHost(), runtime.builder.bindPort(), 
				                                               serverConfig.maxConnectionBitsOnServer, 
				                                               serverConfig.maxPartialResponsesServer, 
				                                               runtime.getHardware().parallelism());
		
		final int routerCount = runtime.getHardware().parallelism();
		
		final Pipe[] encryptedIncomingGroup = Pipe.buildPipes(serverConfig.maxPartialResponsesServer, serverConfig.incomingDataConfig);           
		
		Pipe[] acks = NetGraphBuilder.buildSocketReaderStage(runtime.builder.isTLS(), runtime.gm, serverCoord, routerCount, serverConfig, encryptedIncomingGroup);
		               
		Pipe[] handshakeIncomingGroup=null;
		Pipe[] planIncomingGroup;
		
		if (runtime.builder.isTLS()) {
			planIncomingGroup = Pipe.buildPipes(serverConfig.maxPartialResponsesServer, serverConfig.incomingDataConfig);
			handshakeIncomingGroup = NetGraphBuilder.populateGraphWithUnWrapStages(runtime.gm, serverCoord, serverConfig.serverRequestUnwrapUnits, serverConfig.handshakeDataConfig,
					                      encryptedIncomingGroup, planIncomingGroup, acks);
		} else {
			planIncomingGroup = encryptedIncomingGroup;
		}
		
		//Must call here so the beginning stages of the graph are drawn first when exporting graph.
		app.declareBehavior(runtime);
		
		buildLastHalfOfGraphForServer(app, runtime, serverConfig, serverCoord, routerCount, acks, handshakeIncomingGroup, planIncomingGroup);
	}


	private static void buildLastHalfOfGraphForServer(GreenApp app, GreenRuntime runtime, HTTPServerConfig serverConfig,
			ServerCoordinator serverCoord, final int routerCount, Pipe[] acks, Pipe[] handshakeIncomingGroup,
			Pipe[] planIncomingGroup) {
		////////////////////////
		//create the working modules
		//////////////////////////

		int p = runtime.builder.parallelism();
		
		for (int i = 0; i < p; i++) {
			runtime.constructingParallelInstance(i);
			app.declareParallelBehavior(runtime);  //this creates all the modules for this parallel instance								
		}	

		//////////////////
		//////////////////
		
		HTTP1xRouterStageConfig routerConfig = runtime.builder.routerConfig();
				
		ArrayList<Pipe> forPipeCleaner = new ArrayList<Pipe>();
		Pipe<HTTPRequestSchema>[][] fromRouterToModules = new Pipe[routerCount][];	
		int t = routerCount;
		while (--t>=0) {
			//[router/parallel] then [parser/routes] 
			int path = routerConfig.routesCount();
			fromRouterToModules[t] = new Pipe[path]; 
			
		    while (--path >= 0) {
		    	
		    	Pipe<HTTPRequestSchema>[] array = runtime.builder.buildFromRequestArray(t, path);
		    	//with a single pipe just pass it one, otherwise use the replicator to fan out from a new single pipe.
		    	if (1==array.length) {
		    		fromRouterToModules[t][path] = array[0];
		    	} else {	
		    		fromRouterToModules[t][path] = runtime.builder.newHTTPRequestPipe(runtime.builder.restPipeConfig);		    		
		    		if (0==array.length) {
		    			//we have no consumer so tie it to pipe cleaner		    		
		    			forPipeCleaner.add(fromRouterToModules[t][path]);
		    		} else {
		    			ReplicatorStage.instance(runtime.gm, fromRouterToModules[t][path], array);	
		    		}
		    	}
		    }
		}
		
		
		if (!forPipeCleaner.isEmpty()) {
			PipeCleanerStage.newInstance(runtime.gm, forPipeCleaner.toArray(new Pipe[forPipeCleaner.size()]));
		}
		
		NetGraphBuilder.buildRouters(runtime.gm, routerCount, planIncomingGroup, acks, fromRouterToModules, routerConfig, serverCoord); 
		
		
		
		//NOTE: building arrays of pipes grouped by parallel/routers heading out to order supervisor		
		Pipe<ServerResponseSchema>[][] fromModulesToOrderSuper = new Pipe[routerCount][];
		int r = routerCount;
		while (--r>=0) {
			fromModulesToOrderSuper[r] = runtime.builder.buildToOrderArray(r);			
		}
		NetGraphBuilder.buildRemainderOfServerStages(runtime.builder.isTLS(), runtime.gm, serverCoord, routerCount, serverConfig, handshakeIncomingGroup, fromModulesToOrderSuper);
	}


	private void constructingParallelInstance(int i) {
		parallelInstanceUnderActiveConstruction = i;
	}

	private void constructingParallelInstancesEnding() {
		parallelInstanceUnderActiveConstruction = -1;
	}

	private static void establishDefaultRate(GreenRuntime runtime) {
		
		System.err.println("default rate "+runtime.defaultSleepRateNS);
		
		//by default, unless explicitly set the stages will use this sleep rate
		GraphManager.addDefaultNota(runtime.gm, GraphManager.SCHEDULE_RATE, runtime.defaultSleepRateNS);
	
	}



	public void setExclusiveTopics(CommandChannel cc, String ... exlusiveTopics) {
		// TODO Auto-generated method stub
		
		throw new UnsupportedOperationException("Not yet implemented");
		
	}


	public ListenerConfig newRestListenerConfig(int[] routes) {
		return new ListenerConfig(builder, routes, parallelInstanceUnderActiveConstruction);
	}
    

	public void addFileServer(String path, int ... routes) {
				
		//due to internal implementation we must keep the same number of outputs as inputs.
		int r = routes.length;
		int p = computeParaMulti();
		
		int count = r*p;
		
		Pipe<HTTPRequestSchema>[] inputs = new Pipe[count];
		Pipe<ServerResponseSchema>[] outputs = new Pipe[count];
		populatePipeArrays(r, p, inputs, outputs, routes);		
		
		FileReadModuleStage.newInstance(gm, inputs, outputs, builder.httpSpec, new File(path));
				
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
		int p;
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
				outputs[idx] = builder.newNetResposnePipe(fileResponseConfig, parallelIndex);
			}
		}
	}

    
}
